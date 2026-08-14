using System.Text.Json;
using FlowerWhisp.Core;

namespace FlowerWhisp.Infrastructure;

/// <summary>
/// Non-secret settings consumed by the runtime at process startup.  Credentials
/// deliberately do not have a property in this type; the Groq key is stored by
/// the Windows DPAPI-backed ISecretStore instead.
/// </summary>
public sealed record FlowerWhispSettings(
    string WhisperCheckout = "",
    string WhisperPython = "",
    string WhisperHostScript = "",
    string WhisperModel = "small",
    string WhisperModelDirectory = "",
    string WhisperDevice = "cuda",
    TranscriptionBackend Backend = TranscriptionBackend.Groq,
    string GroqTranscriptionModel = "whisper-large-v3-turbo",
    string GroqPolishModel = "openai/gpt-oss-20b",
    PolishMode Polish = PolishMode.Off,
    RetentionPolicy Retention = RetentionPolicy.DeleteAfter24Hours,
    string Language = "auto",
    bool AnalyticsEnabled = true);

/// <summary>Small, atomic JSON store under the current user's local app data.</summary>
public sealed class UserSettingsStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.General)
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    private readonly string _path;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public UserSettingsStore(string? path = null) => _path = path ?? DefaultPath;

    public static string DefaultPath => System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "FlowerWhisp", "settings.json");

    public string Path => _path;

    public async Task<FlowerWhispSettings> LoadAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!File.Exists(_path)) return new FlowerWhispSettings();
            await using var stream = File.OpenRead(_path);
            return await JsonSerializer.DeserializeAsync<FlowerWhispSettings>(stream, JsonOptions, cancellationToken).ConfigureAwait(false)
                ?? new FlowerWhispSettings();
        }
        catch (JsonException)
        {
            // A hand-edited or interrupted settings file must not prevent the
            // app from launching; the next explicit save replaces it atomically.
            return new FlowerWhispSettings();
        }
        finally { _gate.Release(); }
    }

    /// <summary>Sync startup seam used by RuntimeFactory before the UI exists.</summary>
    public FlowerWhispSettings Load()
    {
        try
        {
            if (!File.Exists(_path)) return new FlowerWhispSettings();
            using var stream = File.OpenRead(_path);
            return JsonSerializer.Deserialize<FlowerWhispSettings>(stream, JsonOptions)
                ?? new FlowerWhispSettings();
        }
        catch (IOException) { return new FlowerWhispSettings(); }
        catch (JsonException) { return new FlowerWhispSettings(); }
    }

    public async Task SaveAsync(FlowerWhispSettings settings, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(settings);
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var fullPath = System.IO.Path.GetFullPath(_path);
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(fullPath) ?? ".");
            var temporaryPath = fullPath + ".tmp";
            await using (var stream = File.Create(temporaryPath))
                await JsonSerializer.SerializeAsync(stream, settings, JsonOptions, cancellationToken).ConfigureAwait(false);
            File.Move(temporaryPath, fullPath, true);
        }
        finally { _gate.Release(); }
    }
}

public sealed record LocalDictionaryEntry(string Phrase, string Replacement, bool Protected = false);
public sealed record LocalSnippet(string Name, string Content, string Shortcut = "New");
public sealed record LocalStyle(string Name, string Instructions, bool BuiltIn = false);

/// <summary>
/// User-authored Signal Ledger content.  It is separate from settings so a
/// settings reset cannot erase the dictionary or writing library.
/// </summary>
public sealed class SignalLedgerContent
{
    public List<LocalDictionaryEntry> Dictionary { get; set; } = [];
    public List<LocalSnippet> Snippets { get; set; } = [];
    public List<LocalStyle> Styles { get; set; } = [];
    public string ScratchRaw { get; set; } = "";
    public string ScratchPolished { get; set; } = "";
}

public sealed class SignalLedgerStore
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.General)
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };

    private readonly string _path;
    private readonly SemaphoreSlim _gate = new(1, 1);

    public SignalLedgerStore(string? path = null) => _path = path ?? DefaultPath;

    public static string DefaultPath => System.IO.Path.Combine(
        Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
        "FlowerWhisp", "signal-ledger.json");

    public string Path => _path;

    public async Task<SignalLedgerContent?> LoadAsync(CancellationToken cancellationToken = default)
    {
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!File.Exists(_path)) return null;
            await using var stream = File.OpenRead(_path);
            return await JsonSerializer.DeserializeAsync<SignalLedgerContent>(stream, JsonOptions, cancellationToken).ConfigureAwait(false);
        }
        catch (JsonException) { return null; }
        finally { _gate.Release(); }
    }

    public async Task SaveAsync(SignalLedgerContent content, CancellationToken cancellationToken = default)
    {
        ArgumentNullException.ThrowIfNull(content);
        await _gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            var fullPath = System.IO.Path.GetFullPath(_path);
            Directory.CreateDirectory(System.IO.Path.GetDirectoryName(fullPath) ?? ".");
            var temporaryPath = fullPath + ".tmp";
            await using (var stream = File.Create(temporaryPath))
                await JsonSerializer.SerializeAsync(stream, content, JsonOptions, cancellationToken).ConfigureAwait(false);
            File.Move(temporaryPath, fullPath, true);
        }
        finally { _gate.Release(); }
    }
}
