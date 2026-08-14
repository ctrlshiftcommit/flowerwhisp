using System.Collections.Immutable;

namespace FlowerWhisp.Core;

public enum TranscriptionBackend { Groq, LocalWhisper }
public enum DictationMode { Hold, Toggle, Scratchpad }
public enum DictationState { Idle, PendingChord, Recording, Processing, ReadyToInsert, Inserted, Cancelled, Failed }
public enum PolishMode { Off, Light, Medium, StyleProfile, Custom }
public enum RetentionPolicy { KeepForever, DeleteAfter24Hours, NeverStore }
public enum InsertionOutcome { Inserted, CopiedForManualPaste, TargetChanged, TargetElevated, Failed }

public sealed record AudioPayload(byte[] Pcm16, int SampleRate = 16_000, int Channels = 1);
public sealed record TranscriptionRequest(Guid RequestId, AudioPayload Audio, string Language = "auto");
public sealed record TranscriptionResult(Guid RequestId, string Text, string? DetectedLanguage = null, TimeSpan? Duration = null, ImmutableArray<TranscriptSegment> Segments = default);
public sealed record TranscriptSegment(TimeSpan Start, TimeSpan End, string Text);
public sealed record PolishRequest(Guid RequestId, string RawText, PolishMode Mode, string? Style = null, string? CustomPrompt = null);
public sealed record PolishResult(Guid RequestId, string Text, bool WasChanged, string ProviderModel);
public sealed record ProviderModel(string Id, bool SupportsAudio = false);

public interface ITranscriptionProvider
{
    TranscriptionBackend Backend { get; }
    Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default);
}

public interface IPolishProvider
{
    Task<PolishResult> PolishAsync(PolishRequest request, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<ProviderModel>> GetAvailableModelsAsync(CancellationToken cancellationToken = default);
}

public interface IAudioCaptureService : IAsyncDisposable
{
    bool IsRecording { get; }
    Task StartAsync(CancellationToken cancellationToken = default);
    Task<AudioPayload> StopAsync(CancellationToken cancellationToken = default);
    Task CancelAsync(CancellationToken cancellationToken = default);
}

public interface ITextInsertionService
{
    Task<InsertionResult> InsertAsync(string text, CancellationToken cancellationToken = default);

    /// <summary>
    /// Copies text to the clipboard for an explicit user paste. This seam must
    /// never synthesize Ctrl+V or otherwise target the current foreground
    /// window.
    /// </summary>
    Task<InsertionResult> CopyForManualPasteAsync(string text, CancellationToken cancellationToken = default);
}

public interface IForegroundTargetService
{
    TargetSnapshot Capture();
    bool HasChanged(TargetSnapshot target);
    bool IsElevated(TargetSnapshot target);
}

public sealed record TargetSnapshot(nint WindowHandle, uint ProcessId, string? ProcessName, DateTimeOffset CapturedAt);
public sealed record InsertionResult(InsertionOutcome Outcome, string? Detail = null);

public interface ILocalWhisperHost : IAsyncDisposable
{
    Task<LocalWhisperHandshake> StartAsync(CancellationToken cancellationToken = default);
    Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default);
    Task ShutdownAsync(CancellationToken cancellationToken = default);
}

public sealed record LocalWhisperHandshake(int ProtocolVersion, string HostVersion, string Model, string Device, bool Multilingual, bool Ready);

public interface ISecretStore
{
    Task SetAsync(string key, string value, CancellationToken cancellationToken = default);
    Task<string?> GetAsync(string key, CancellationToken cancellationToken = default);
    Task DeleteAsync(string key, CancellationToken cancellationToken = default);
}

public interface IDictationRepository
{
    Task SaveAsync(DictationRecord record, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<DictationRecord>> ListAsync(CancellationToken cancellationToken = default);
    Task DeleteAsync(Guid id, CancellationToken cancellationToken = default);
}

public interface IUsageAggregateRepository
{
    Task RecordAsync(UsageAggregate aggregate, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<UsageAggregate>> ListAsync(CancellationToken cancellationToken = default);
}

public interface IRetentionService
{
    Task<int> ApplyAsync(RetentionPolicy policy, CancellationToken cancellationToken = default);
}

public sealed record DictationRecord(
    Guid Id,
    DateTimeOffset CreatedAt,
    string RawText,
    string FinalText,
    TranscriptionBackend Backend,
    PolishMode PolishMode,
    RetentionPolicy Retention,
    TimeSpan Duration,
    string? Language = null);

public sealed record UsageAggregate(DateOnly Day, int DictationCount, double AudioSeconds, int CharacterCount);

public sealed record DictionaryEntry(Guid Id, string Phrase, string Replacement, bool Protected = false);
public sealed record Snippet(Guid Id, string Name, string Content, bool Protected = false);
public sealed record StyleProfile(Guid Id, string Name, string Instructions);
public sealed record Transform(Guid Id, string Name, string Description, bool Enabled = true);

public static class ProviderPolicy
{
    public static readonly ImmutableHashSet<string> GroqTranscriptionAllowlist =
        ["whisper-large-v3-turbo", "whisper-large-v3"];

    public static readonly ImmutableHashSet<string> GroqPolishAllowlist =
        ["openai/gpt-oss-20b", "openai/gpt-oss-120b"];

    public static string SelectDefaultTranscription(IEnumerable<ProviderModel> models) =>
        models.Select(x => x.Id).Intersect(GroqTranscriptionAllowlist, StringComparer.OrdinalIgnoreCase)
            .FirstOrDefault(x => x.Equals("whisper-large-v3-turbo", StringComparison.OrdinalIgnoreCase))
        ?? throw new InvalidOperationException("No approved Groq transcription model is available.");

    public static string SelectDefaultPolish(IEnumerable<ProviderModel> models) =>
        models.Select(x => x.Id).Intersect(GroqPolishAllowlist, StringComparer.OrdinalIgnoreCase)
            .FirstOrDefault(x => x.Equals("openai/gpt-oss-20b", StringComparison.OrdinalIgnoreCase))
        ?? throw new InvalidOperationException("No approved Groq polish model is available.");

    public static IReadOnlyList<string> FilterPolishModels(IEnumerable<ProviderModel> authenticatedModels) =>
        authenticatedModels.Select(x => x.Id)
            .Where(GroqPolishAllowlist.Contains)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(x => x, StringComparer.OrdinalIgnoreCase)
            .ToArray();
}
