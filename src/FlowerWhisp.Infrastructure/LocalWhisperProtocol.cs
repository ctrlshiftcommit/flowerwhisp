using System.Diagnostics;
using System.Collections.Immutable;
using System.Text.Json;
using FlowerWhisp.Core;

namespace FlowerWhisp.Infrastructure;

public static class LocalWhisperProtocol
{
    public const int Version = 1;
    public static readonly TimeSpan DefaultStartupTimeout = TimeSpan.FromSeconds(120);
    public static readonly TimeSpan DefaultReadTimeout = TimeSpan.FromSeconds(120);

    public static bool TryParse(string line, out JsonDocument? document, out string? error)
    {
        try
        {
            document = JsonDocument.Parse(line);
            error = null;
            return true;
        }
        catch (JsonException ex)
        {
            document = null;
            error = ex.Message;
            return false;
        }
    }
}

/// <summary>
/// Bounded, privacy-safe diagnostics collected from the sidecar's stderr. The
/// actual line is never retained, so model/runtime output cannot become
/// transcript or audio logging.
/// </summary>
public sealed record LocalWhisperStderrDiagnostic(int LinesObserved, int LastLineLength, string LastCategory);

/// <summary>
/// Serialized process boundary for the official Whisper sidecar.  Stdout is kept
/// strictly NDJSON; stderr is intentionally redirected so a Python warning can
/// never corrupt a protocol response.  A single unexpected process failure gets
/// one automatic restart, while model/protocol errors are surfaced unchanged.
/// </summary>
public sealed class ProcessLocalWhisperHost : ILocalWhisperHost
{
    private readonly string _python;
    private readonly string _script;
    private readonly string _model;
    private readonly string? _whisperCheckout;
    private readonly string? _modelDirectory;
    private readonly string? _device;
    private readonly TimeSpan _startupTimeout;
    private readonly TimeSpan _readTimeout;
    private readonly SemaphoreSlim _gate = new(1, 1);
    private Process? _process;
    private Task? _stderrDrainTask;
    private LocalWhisperHandshake? _handshake;
    private int _restartBudget = 1;
    private bool _disposed;

    public LocalWhisperStderrDiagnostic? LastStderrDiagnostic { get; private set; }

    public ProcessLocalWhisperHost(
        string python,
        string script,
        string model = "small",
        string? whisperCheckout = null,
        string? modelDirectory = null,
        string? device = null,
        TimeSpan? startupTimeout = null,
        TimeSpan? readTimeout = null)
    {
        _python = IsPathLike(python) ? Path.GetFullPath(python) : python;
        _script = Path.GetFullPath(script);
        _model = string.IsNullOrWhiteSpace(model) ? "small" : model;
        _whisperCheckout = string.IsNullOrWhiteSpace(whisperCheckout) ? null : Path.GetFullPath(whisperCheckout);
        _modelDirectory = string.IsNullOrWhiteSpace(modelDirectory) ? null : Path.GetFullPath(modelDirectory);
        _device = string.IsNullOrWhiteSpace(device) ? null : device;
        _startupTimeout = ValidateTimeout(startupTimeout ?? LocalWhisperProtocol.DefaultStartupTimeout, nameof(startupTimeout));
        _readTimeout = ValidateTimeout(readTimeout ?? LocalWhisperProtocol.DefaultReadTimeout, nameof(readTimeout));
    }

    public async Task<LocalWhisperHandshake> StartAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            _restartBudget = 1;
            return await EnsureStartedCoreAsync(cancellationToken).ConfigureAwait(false);
        }
        finally { _gate.Release(); }
    }

    public async Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        var tempPath = Path.Combine(Path.GetTempPath(), $"flowerwhisp-{request.RequestId:N}.wav");
        try
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            await File.WriteAllBytesAsync(tempPath, WavEncoder.Encode(request.Audio), cancellationToken).ConfigureAwait(false);
            await EnsureStartedCoreAsync(cancellationToken).ConfigureAwait(false);
            for (var attempt = 0; ; attempt++)
            {
                try
                {
                    var payload = new
                    {
                        type = "transcribe",
                        requestId = request.RequestId,
                        audioPath = Path.GetFullPath(tempPath),
                        language = request.Language
                    };
                    await WriteAsync(payload, cancellationToken).ConfigureAwait(false);
                    using var document = await ReadResponseWithinTimeoutAsync(
                        request.RequestId,
                        cancellationToken,
                        _readTimeout,
                        "transcription").ConfigureAwait(false);
                    return ParseTranscription(request.RequestId, document.RootElement);
                }
                catch (OperationCanceledException)
                {
                    // A canceled read leaves an unread response in the pipe.  Tear
                    // down the process so the next request cannot consume it.
                    await TerminateProcessAsync().ConfigureAwait(false);
                    throw;
                }
                catch (Exception ex) when (attempt == 0 && _restartBudget > 0 && IsProcessFailure(ex))
                {
                    _restartBudget--;
                    await TerminateProcessAsync().ConfigureAwait(false);
                    await EnsureStartedCoreAsync(cancellationToken).ConfigureAwait(false);
                }
            }
        }
        finally
        {
            try { File.Delete(tempPath); } catch { }
            _gate.Release();
        }
    }

    public async Task ShutdownAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (_process is null) return;
            try
            {
                var id = Guid.NewGuid();
                await WriteAsync(new { type = "shutdown", requestId = id }, cancellationToken).ConfigureAwait(false);
                using var response = await ReadResponseWithinTimeoutAsync(
                    id,
                    cancellationToken,
                    _readTimeout,
                    "shutdown").ConfigureAwait(false);
                if (!response.RootElement.TryGetProperty("type", out var type) || type.GetString() != "shutdown")
                    throw new LocalWhisperProtocolException("Shutdown response was not a shutdown acknowledgement.");
            }
            catch { /* process teardown below is authoritative */ }
            await TerminateProcessAsync().ConfigureAwait(false);
            _handshake = null;
            _restartBudget = 1;
        }
        finally { _gate.Release(); }
    }

    public async ValueTask DisposeAsync()
    {
        if (_disposed) return;
        _disposed = true;
        try { await ShutdownAsync().ConfigureAwait(false); }
        catch { await TerminateProcessAsync().ConfigureAwait(false); }
        _gate.Dispose();
    }

    private async Task<LocalWhisperHandshake> EnsureStartedCoreAsync(CancellationToken cancellationToken)
    {
        if (_process is { HasExited: false } && _handshake is not null) return _handshake;
        if (_process is not null) await TerminateProcessAsync().ConfigureAwait(false);
        if (!File.Exists(_script)) throw new FileNotFoundException("Local Whisper host script was not found.", _script);
        if (!File.Exists(_python) && !Path.GetFileName(_python).Equals("python", StringComparison.OrdinalIgnoreCase) && !Path.GetFileName(_python).Equals("python.exe", StringComparison.OrdinalIgnoreCase))
            throw new FileNotFoundException("Python executable was not found.", _python);

        var start = new ProcessStartInfo(_python)
        {
            RedirectStandardInput = true,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            UseShellExecute = false,
            CreateNoWindow = true,
            WorkingDirectory = Path.GetDirectoryName(_script) ?? Environment.CurrentDirectory
        };
        start.ArgumentList.Add(_script);
        start.Environment["FLOWERWHISP_MODEL"] = _model;
        if (_whisperCheckout is not null) start.Environment["FLOWERWHISP_WHISPER_CHECKOUT"] = _whisperCheckout;
        if (_modelDirectory is not null) start.Environment["FLOWERWHISP_MODEL_DIR"] = _modelDirectory;
        if (_device is not null) start.Environment["FLOWERWHISP_DEVICE"] = _device;
        _process = Process.Start(start) ?? throw new InvalidOperationException("Unable to start local Whisper host.");
        _stderrDrainTask = DrainStderrAsync(_process);
        try
        {
            var id = Guid.NewGuid();
            await WriteAsync(new { type = "handshake", requestId = id }, cancellationToken).ConfigureAwait(false);
            using var response = await ReadResponseWithinTimeoutAsync(
                id,
                cancellationToken,
                _startupTimeout,
                "startup handshake").ConfigureAwait(false);
            _handshake = ParseHandshake(response.RootElement);
            return _handshake;
        }
        catch
        {
            await TerminateProcessAsync().ConfigureAwait(false);
            throw;
        }
    }

    private static bool IsPathLike(string value) => Path.IsPathRooted(value) || value.Contains(Path.DirectorySeparatorChar) || value.Contains(Path.AltDirectorySeparatorChar);

    private static TimeSpan ValidateTimeout(TimeSpan timeout, string parameterName)
    {
        if (timeout <= TimeSpan.Zero || timeout == Timeout.InfiniteTimeSpan)
            throw new ArgumentOutOfRangeException(parameterName, "Timeout must be positive and finite.");
        return timeout;
    }

    private async Task WriteAsync(object payload, CancellationToken cancellationToken)
    {
        var process = _process ?? throw new InvalidOperationException("Local Whisper host is not running.");
        if (process.HasExited) throw new EndOfStreamException("Local Whisper host exited before accepting a request.");
        await process.StandardInput.WriteLineAsync(JsonSerializer.Serialize(payload).AsMemory(), cancellationToken).ConfigureAwait(false);
        await process.StandardInput.FlushAsync(cancellationToken).ConfigureAwait(false);
    }

    private async Task<JsonDocument> ReadResponseAsync(Guid requestId, CancellationToken cancellationToken)
    {
        var process = _process ?? throw new InvalidOperationException("Local Whisper host is not running.");
        var line = await process.StandardOutput.ReadLineAsync(cancellationToken).ConfigureAwait(false);
        if (line is null) throw new EndOfStreamException("Local Whisper host closed stdout.");
        if (!LocalWhisperProtocol.TryParse(line, out var document, out var error) || document is null)
            throw new LocalWhisperProtocolException($"Malformed local Whisper response: {error}");
        if (!document.RootElement.TryGetProperty("requestId", out var responseId) || !Guid.TryParse(responseId.GetString(), out var parsed) || parsed != requestId)
        {
            document.Dispose();
            throw new LocalWhisperProtocolException("Local Whisper response requestId did not match the request.");
        }
        return document;
    }

    private async Task<JsonDocument> ReadResponseWithinTimeoutAsync(
        Guid requestId,
        CancellationToken cancellationToken,
        TimeSpan timeout,
        string operation)
    {
        try
        {
            return await ReadResponseAsync(requestId, cancellationToken)
                .WaitAsync(timeout, cancellationToken).ConfigureAwait(false);
        }
        catch (TimeoutException exception)
        {
            await TerminateProcessAsync().ConfigureAwait(false);
            throw new TimeoutException(
                FormatDiagnostics($"Local Whisper {operation} timed out after {timeout.TotalSeconds:0.###} seconds"),
                exception);
        }
    }

    private static LocalWhisperHandshake ParseHandshake(JsonElement root)
    {
        if (!root.TryGetProperty("type", out var type) || type.GetString() != "handshake")
            throw new LocalWhisperProtocolException("Local Whisper handshake response had an invalid type.");
        var version = root.TryGetProperty("protocolVersion", out var protocol) ? protocol.GetInt32() : -1;
        if (version != LocalWhisperProtocol.Version)
            throw new LocalWhisperProtocolException($"Local Whisper protocol version mismatch: expected {LocalWhisperProtocol.Version}, got {version}.");
        var ready = root.TryGetProperty("ready", out var readyValue) && readyValue.GetBoolean();
        if (!ready) throw new LocalWhisperProtocolException(root.TryGetProperty("message", out var message) ? message.GetString() ?? "Local Whisper host is not ready." : "Local Whisper host is not ready.");
        return new LocalWhisperHandshake(version,
            root.TryGetProperty("hostVersion", out var hostVersion) ? hostVersion.GetString() ?? "unknown" : "unknown",
            root.TryGetProperty("model", out var model) ? model.GetString() ?? "unknown" : "unknown",
            root.TryGetProperty("device", out var device) ? device.GetString() ?? "unknown" : "unknown",
            root.TryGetProperty("multilingual", out var multilingual) && multilingual.GetBoolean(), ready);
    }

    private static TranscriptionResult ParseTranscription(Guid requestId, JsonElement root)
    {
        var type = root.TryGetProperty("type", out var typeValue) ? typeValue.GetString() : null;
        if (type == "error")
        {
            var code = root.TryGetProperty("code", out var codeValue) ? codeValue.GetString() : "error";
            var message = root.TryGetProperty("message", out var messageValue) ? messageValue.GetString() : "Local Whisper transcription failed.";
            throw new LocalWhisperProtocolException($"{code}: {message}");
        }
        if (type != "transcription") throw new LocalWhisperProtocolException("Local Whisper response had an invalid transcription type.");
        var text = root.TryGetProperty("text", out var textValue) ? textValue.GetString() ?? string.Empty : string.Empty;
        TimeSpan? duration = null;
        if (root.TryGetProperty("duration", out var durationValue) && durationValue.ValueKind == JsonValueKind.Number) duration = TimeSpan.FromSeconds(durationValue.GetDouble());
        var language = root.TryGetProperty("language", out var languageValue) ? languageValue.GetString() : null;
        var segments = ImmutableArray.CreateBuilder<TranscriptSegment>();
        if (root.TryGetProperty("segments", out var segmentValues) && segmentValues.ValueKind == JsonValueKind.Array)
        {
            foreach (var segment in segmentValues.EnumerateArray())
            {
                if (!segment.TryGetProperty("start", out var start) || !segment.TryGetProperty("end", out var end)) continue;
                segments.Add(new TranscriptSegment(TimeSpan.FromSeconds(start.GetDouble()), TimeSpan.FromSeconds(end.GetDouble()), segment.TryGetProperty("text", out var segmentText) ? segmentText.GetString() ?? string.Empty : string.Empty));
            }
        }
        return new TranscriptionResult(requestId, text, language, duration, segments.ToImmutable());
    }

    private static bool IsProcessFailure(Exception exception) => exception is EndOfStreamException or IOException or ObjectDisposedException || exception is InvalidOperationException && exception.Message.Contains("host", StringComparison.OrdinalIgnoreCase);

    private async Task DrainStderrAsync(Process process)
    {
        try
        {
            while (await process.StandardError.ReadLineAsync().ConfigureAwait(false) is { } line)
            {
                LastStderrDiagnostic = new LocalWhisperStderrDiagnostic(
                    LinesObserved: (LastStderrDiagnostic?.LinesObserved ?? 0) + 1,
                    LastLineLength: Math.Min(line.Length, 4096),
                    LastCategory: CategorizeStderr(line));
            }
        }
        catch (ObjectDisposedException) { }
        catch (InvalidOperationException) { }
        catch (IOException) { }
    }

    private static string CategorizeStderr(string line)
    {
        var normalized = line.Trim();
        if (normalized.Length == 0) return "empty";
        if (normalized.Contains("error", StringComparison.OrdinalIgnoreCase) || normalized.Contains("exception", StringComparison.OrdinalIgnoreCase)) return "error";
        if (normalized.Contains("warn", StringComparison.OrdinalIgnoreCase)) return "warning";
        return "diagnostic";
    }

    private string FormatDiagnostics(string context)
    {
        var diagnostic = LastStderrDiagnostic;
        return diagnostic is null
            ? context + "."
            : $"{context} (stderr: category={diagnostic.LastCategory}, lines={diagnostic.LinesObserved}, lastLength={diagnostic.LastLineLength}).";
    }

    private async Task TerminateProcessAsync()
    {
        var process = _process;
        _process = null;
        _handshake = null;
        if (process is null) return;
        try
        {
            if (!process.HasExited) process.Kill(entireProcessTree: true);
            await process.WaitForExitAsync().ConfigureAwait(false);
            if (_stderrDrainTask is not null)
            {
                try { await _stderrDrainTask.WaitAsync(TimeSpan.FromSeconds(2)).ConfigureAwait(false); }
                catch (TimeoutException) { }
            }
        }
        catch { }
        finally
        {
            _stderrDrainTask = null;
            process.Dispose();
        }
    }
}

public sealed class LocalWhisperProtocolException : InvalidOperationException
{
    public LocalWhisperProtocolException(string message) : base(message) { }
}
