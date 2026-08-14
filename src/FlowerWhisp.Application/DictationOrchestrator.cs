using FlowerWhisp.Core;

namespace FlowerWhisp.Application;

public sealed record DictationOptions(
    TranscriptionBackend Backend = TranscriptionBackend.Groq,
    DictationMode Mode = DictationMode.Hold,
    PolishMode Polish = PolishMode.Off,
    RetentionPolicy Retention = RetentionPolicy.DeleteAfter24Hours,
    string Language = "auto",
    string? Style = null,
    string? CustomPrompt = null,
    bool AnalyticsEnabled = true);

/// <summary>Identity and options captured at the instant recording begins.</summary>
public sealed record DictationSession(
    Guid RequestId,
    TargetSnapshot Target,
    DictationOptions Options,
    DateTimeOffset StartedAt);

/// <summary>
/// Coordinates one real dictation session at a time.
///
/// StartAsync only captures the foreground target and starts WASAPI.  FinishAsync
/// stops audio before doing any provider work, then transcribes, optionally
/// polishes, inserts into the original target, and finally writes retention and
/// privacy-safe usage records.  CancelAsync stops and discards the active audio;
/// it never invokes a provider or creates history/usage rows.
/// </summary>
public sealed class DictationOrchestrator : IAsyncDisposable
{
    private readonly IAudioCaptureService _capture;
    private readonly IReadOnlyDictionary<TranscriptionBackend, ITranscriptionProvider> _transcribers;
    private readonly IPolishProvider? _polisher;
    private readonly ITextInsertionService _insertion;
    private readonly IForegroundTargetService _foreground;
    private readonly IDictationRepository _repository;
    private readonly IUsageAggregateRepository _usage;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private DictationSession? _session;
    private bool _disposed;

    public DictationOrchestrator(
        IAudioCaptureService capture,
        IEnumerable<ITranscriptionProvider> transcribers,
        IPolishProvider? polisher,
        ITextInsertionService insertion,
        IForegroundTargetService foreground,
        IDictationRepository repository,
        IUsageAggregateRepository usage)
    {
        _capture = capture ?? throw new ArgumentNullException(nameof(capture));
        _transcribers = (transcribers ?? throw new ArgumentNullException(nameof(transcribers)))
            .GroupBy(x => x.Backend)
            .ToDictionary(x => x.Key, x => x.Last());
        _polisher = polisher;
        _insertion = insertion ?? throw new ArgumentNullException(nameof(insertion));
        _foreground = foreground ?? throw new ArgumentNullException(nameof(foreground));
        _repository = repository ?? throw new ArgumentNullException(nameof(repository));
        _usage = usage ?? throw new ArgumentNullException(nameof(usage));
    }

    public DictationSession? ActiveSession => _session;
    public bool IsRecording => _session is not null && _capture.IsRecording;

    public async Task<DictationSession> StartAsync(
        DictationOptions options,
        CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (_session is not null)
                throw new InvalidOperationException("A dictation session is already active.");

            var target = _foreground.Capture();
            try
            {
                await _capture.StartAsync(cancellationToken).ConfigureAwait(false);
            }
            catch
            {
                // A backend can fail after partially opening a device.  The
                // cancellation seam is idempotent and gives WASAPI a chance to
                // release that device before the next attempt.
                try { await _capture.CancelAsync(CancellationToken.None).ConfigureAwait(false); } catch { }
                throw;
            }

            var session = new DictationSession(Guid.NewGuid(), target, options, DateTimeOffset.UtcNow);
            _session = session;
            return session;
        }
        finally { _gate.Release(); }
    }

    public async Task<DictationExecutionResult> FinishAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            var session = _session ?? throw new InvalidOperationException("There is no active dictation session to finish.");
            AudioPayload audio;
            try
            {
                // Stop before looking up a provider or making network/process
                // calls.  This bounds microphone lifetime even on provider
                // configuration errors.
                audio = await _capture.StopAsync(cancellationToken).ConfigureAwait(false);
            }
            catch
            {
                try { await _capture.CancelAsync(CancellationToken.None).ConfigureAwait(false); } catch { }
                throw;
            }

            return await ProcessStoppedAudioAsync(session, audio, cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            _session = null;
            _gate.Release();
        }
    }

    public async Task CancelAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (_session is null) return;
            try { await _capture.CancelAsync(cancellationToken).ConfigureAwait(false); }
            finally { _session = null; }
        }
        finally { _gate.Release(); }
    }

    /// <summary>
    /// Compatibility helper for callers that intentionally want one complete
    /// hold-style operation.  New UI code should use StartAsync/FinishAsync so
    /// keyboard and pill events can share the live session.
    /// </summary>
    public async Task<DictationExecutionResult> ExecuteAsync(
        DictationOptions options,
        CancellationToken cancellationToken = default)
    {
        await StartAsync(options, cancellationToken).ConfigureAwait(false);
        try
        {
            return await FinishAsync(cancellationToken).ConfigureAwait(false);
        }
        catch
        {
            // Finish normally clears the session in its finally block.  If a
            // caller cancels before StopAsync is entered, make the discard
            // explicit without masking the original exception.
            try { await CancelAsync(CancellationToken.None).ConfigureAwait(false); } catch { }
            throw;
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (_disposed) return;
        _disposed = true;
        await _gate.WaitAsync().ConfigureAwait(false);
        try
        {
            if (_session is not null)
            {
                try { await _capture.CancelAsync(CancellationToken.None).ConfigureAwait(false); } catch { }
                _session = null;
            }
        }
        finally
        {
            _gate.Release();
            _gate.Dispose();
            try { await _capture.DisposeAsync().ConfigureAwait(false); } catch { }
        }
    }

    private async Task<DictationExecutionResult> ProcessStoppedAudioAsync(
        DictationSession session,
        AudioPayload audio,
        CancellationToken cancellationToken)
    {
        if (!_transcribers.TryGetValue(session.Options.Backend, out var provider))
            throw new InvalidOperationException($"No transcription provider is configured for {session.Options.Backend}. Configure a provider in Settings → Providers, then try again.");

        var transcript = await provider.TranscribeAsync(
            new TranscriptionRequest(session.RequestId, audio, session.Options.Language),
            cancellationToken).ConfigureAwait(false);

        var finalText = transcript.Text;
        var polishModel = "raw";
        if (session.Options.Polish != PolishMode.Off)
        {
            if (_polisher is null)
                throw new InvalidOperationException("Text polish is not configured. Add an approved Groq key in Settings → Providers or turn polish off.");
            var polished = await _polisher.PolishAsync(
                new PolishRequest(session.RequestId, transcript.Text, session.Options.Polish, session.Options.Style, session.Options.CustomPrompt),
                cancellationToken).ConfigureAwait(false);
            finalText = polished.Text;
            polishModel = polished.ProviderModel;
        }

        var scratchpad = session.Options.Mode == DictationMode.Scratchpad;
        var targetChanged = !scratchpad && _foreground.HasChanged(session.Target);
        var targetElevated = !scratchpad && !targetChanged && _foreground.IsElevated(session.Target);
        InsertionResult insertion;
        if (scratchpad)
        {
            insertion = new InsertionResult(InsertionOutcome.Inserted,
                "Captured in Scratchpad; no external target received input.");
        }
        else if (targetChanged || targetElevated)
        {
            // The target check is deliberately performed immediately before
            // insertion. If it no longer matches, never inject or paste into
            // whatever happens to be foreground now; leave the text on the
            // clipboard for an explicit user action instead.
            var manualCopy = await _insertion.CopyForManualPasteAsync(finalText, cancellationToken).ConfigureAwait(false);
            var targetOutcome = targetChanged ? InsertionOutcome.TargetChanged : InsertionOutcome.TargetElevated;
            var targetDescription = targetChanged
                ? "The foreground target changed while processing."
                : "The original target is elevated.";
            var copyDescription = manualCopy.Outcome == InsertionOutcome.CopiedForManualPaste
                ? " Text was copied to the clipboard for manual paste."
                : " Text could not be copied to the clipboard.";
            insertion = new InsertionResult(targetOutcome, targetDescription + copyDescription);
        }
        else
        {
            insertion = await _insertion.InsertAsync(finalText, cancellationToken).ConfigureAwait(false);
        }

        var duration = transcript.Duration ?? AudioDuration(audio);
        if (session.Options.Retention != RetentionPolicy.NeverStore)
        {
            await _repository.SaveAsync(
                new DictationRecord(session.RequestId, DateTimeOffset.UtcNow, transcript.Text, finalText,
                    session.Options.Backend, session.Options.Polish, session.Options.Retention,
                    duration, transcript.DetectedLanguage), cancellationToken).ConfigureAwait(false);
        }

        if (session.Options.AnalyticsEnabled)
        {
            await _usage.RecordAsync(
                new UsageAggregate(DateOnly.FromDateTime(DateTime.UtcNow), 1, duration.TotalSeconds, finalText.Length),
                cancellationToken).ConfigureAwait(false);
        }

        return new DictationExecutionResult(session.RequestId, transcript, finalText, polishModel, insertion, session.Options);
    }

    private static TimeSpan AudioDuration(AudioPayload audio)
    {
        if (audio.Pcm16.Length == 0 || audio.SampleRate <= 0 || audio.Channels <= 0) return TimeSpan.Zero;
        return TimeSpan.FromSeconds(audio.Pcm16.Length / (double)(audio.SampleRate * audio.Channels * 2));
    }
}

public sealed record DictationExecutionResult(
    Guid RequestId,
    TranscriptionResult Transcript,
    string FinalText,
    string PolishModel,
    InsertionResult Insertion,
    DictationOptions Options);
