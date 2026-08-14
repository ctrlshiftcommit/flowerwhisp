using FlowerWhisp.Application;
using FlowerWhisp.Core;
using FlowerWhisp.Infrastructure;
using FlowerWhisp.Platform.Windows;
using Microsoft.UI.Dispatching;
using Microsoft.UI.Xaml;

namespace FlowerWhisp;

/// <summary>Application composition root and native-window lifetime owner.</summary>
public partial class App : Microsoft.UI.Xaml.Application
{
    public static Window Window { get; private set; } = null!;
    public static PillWindow? Pill { get; private set; }
    public static DispatcherQueue DispatcherQueue { get; private set; } = null!;
    public static DictationRuntime Runtime { get; private set; } = null!;

    public static nint WindowHandle =>
        WinRT.Interop.WindowNative.GetWindowHandle(Window);

    public App() => InitializeComponent();

    protected override void OnLaunched(Microsoft.UI.Xaml.LaunchActivatedEventArgs args)
    {
        // MainWindow navigates immediately in its constructor, so compose the
        // runtime before constructing it.  This also makes MainPage and the
        // pill subscribe to one shared lifecycle from their first frame.
        DispatcherQueue = Microsoft.UI.Dispatching.DispatcherQueue.GetForCurrentThread();
        Runtime = RuntimeFactory.Create(DispatcherQueue);
        Window = new MainWindow();
        Window.Closed += OnMainWindowClosed;
        Window.Activate();

        EnsurePill().AppWindow.Show(activateWindow: false);
    }

    public static PillWindow EnsurePill()
    {
        if (Pill is not null) return Pill;
        Pill = new PillWindow();
        return Pill;
    }

    internal static void OnPillClosed(PillWindow closed)
    {
        if (ReferenceEquals(Pill, closed)) Pill = null;
    }

    private static void OnMainWindowClosed(object sender, WindowEventArgs args)
    {
        try { Pill?.Close(); } catch { }
        _ = Runtime?.DisposeAsync().AsTask();
    }
}

/// <summary>
/// The one runtime used by the main page, pill, and global keyboard hook.
/// All hook callbacks are marshalled to the WinUI dispatcher before they touch
/// the state machine or orchestrator, and the semaphore keeps button/hook
/// actions ordered when a key is released during provider work.
/// </summary>
public sealed class DictationRuntime : IAsyncDisposable
{
    private readonly DispatcherQueue _dispatcher;
    private readonly LowLevelKeyboardHook _hook;
    private readonly ShortcutStateMachine _shortcuts = new();
    private readonly SemaphoreSlim _inputGate = new(1, 1);
    private readonly DictationOptions _defaults;
    private bool _manualSession;
    private bool _disposed;
    private DictationMode _mode = DictationMode.Hold;
    private DictationState _state = DictationState.Idle;

    public DictationRuntime(
        DispatcherQueue dispatcher,
        DictationOrchestrator orchestrator,
        LowLevelKeyboardHook hook,
        DictationOptions defaults,
        IDictationRepository repository)
    {
        _dispatcher = dispatcher;
        Orchestrator = orchestrator;
        _hook = hook;
        _defaults = defaults;
        _repository = repository;
        _hook.KeyEvent += OnHookKeyEvent;
    }

    public DictationOrchestrator Orchestrator { get; }
    public bool IsRecording => Orchestrator.ActiveSession is not null;
    public DictationMode Mode => _mode;
    public event EventHandler<DictationRuntimeState>? StateChanged;

    public async Task<IReadOnlyList<DictationRecord>> LoadHistoryAsync(CancellationToken cancellationToken = default) =>
        await OrchestratorRepository.ListAsync(cancellationToken).ConfigureAwait(false);

    public Task DeleteHistoryAsync(Guid id, CancellationToken cancellationToken = default) =>
        OrchestratorRepository.DeleteAsync(id, cancellationToken);

    // The repository is intentionally kept behind this small adapter so the
    // UI never needs to know which SQLite path the composition root selected.
    private IDictationRepository OrchestratorRepository => _repository;
    private readonly IDictationRepository _repository;

    public Task StartFromUiAsync(bool toggle, CancellationToken cancellationToken = default) =>
        StartFromUiAsync(toggle ? DictationMode.Toggle : DictationMode.Hold, cancellationToken);

    public Task StartScratchpadAsync(CancellationToken cancellationToken = default) =>
        StartFromUiAsync(DictationMode.Scratchpad, cancellationToken);

    private async Task StartFromUiAsync(DictationMode mode, CancellationToken cancellationToken)
    {
        await _inputGate.WaitAsync(cancellationToken).ConfigureAwait(true);
        try
        {
            if (Orchestrator.ActiveSession is not null)
            {
                Publish(_state, "Already listening · finish or cancel the current capture.");
                return;
            }

            _manualSession = true;
            _mode = mode;
            _state = DictationState.Recording;
            Publish(_state, "Starting microphone…");
            await Orchestrator.StartAsync(_defaults with { Mode = _mode }, cancellationToken).ConfigureAwait(true);
            Publish(_state, _mode switch
            {
                DictationMode.Toggle => "Listening · toggle mode; press Finish when you are done.",
                DictationMode.Scratchpad => "Listening · scratchpad mode; press Finish to keep the result in FlowerWhisp.",
                _ => "Listening · press Finish to insert."
            });
        }
        catch (Exception ex)
        {
            _manualSession = false;
            _state = DictationState.Failed;
            Publish(_state, ActionableMessage(ex));
        }
        finally { _inputGate.Release(); }
    }

    public async Task FinishFromUiAsync(CancellationToken cancellationToken = default)
    {
        await _inputGate.WaitAsync(cancellationToken).ConfigureAwait(true);
        try
        {
            if (Orchestrator.ActiveSession is null)
            {
                Publish(DictationState.Idle, "Ready · start a recording first.");
                return;
            }

            _state = DictationState.Processing;
            Publish(_state, "Finishing · transcribing, inserting, and saving locally…");
            var result = await Orchestrator.FinishAsync(cancellationToken).ConfigureAwait(true);
            _manualSession = false;
            _state = DictationState.Inserted;
            Publish(_state, InsertionMessage(result), result);
        }
        catch (Exception ex)
        {
            _manualSession = false;
            _state = DictationState.Failed;
            Publish(_state, ActionableMessage(ex));
        }
        finally { _inputGate.Release(); }
    }

    public async Task CancelFromUiAsync(CancellationToken cancellationToken = default)
    {
        await _inputGate.WaitAsync(cancellationToken).ConfigureAwait(true);
        try
        {
            await Orchestrator.CancelAsync(cancellationToken).ConfigureAwait(true);
            _manualSession = false;
            _state = DictationState.Cancelled;
            Publish(_state, "Cancelled · audio was discarded; no history row was created.");
            _shortcuts.Reset();
        }
        catch (Exception ex)
        {
            _state = DictationState.Failed;
            Publish(_state, ActionableMessage(ex));
        }
        finally { _inputGate.Release(); }
    }

    public async ValueTask DisposeAsync()
    {
        if (_disposed) return;
        _disposed = true;
        _hook.KeyEvent -= OnHookKeyEvent;
        _hook.Dispose();
        try { await Orchestrator.DisposeAsync().ConfigureAwait(false); } catch { }
        _inputGate.Dispose();
    }

    private void OnHookKeyEvent(object? sender, KeyboardHookEventArgs args)
    {
        if (_disposed) return;
        // The native hook callback is on its private message-pump thread.  Do
        // not let it call WinUI or a provider directly.
        _dispatcher.TryEnqueue(() => _ = ProcessHookEventAsync(args));
    }

    private async Task ProcessHookEventAsync(KeyboardHookEventArgs args)
    {
        await _inputGate.WaitAsync().ConfigureAwait(true);
        try
        {
            if (_manualSession) return;
            var snapshot = _shortcuts.Handle(Map(args));
            _mode = snapshot.Mode;
            if (snapshot.ShouldCancel)
            {
                await Orchestrator.CancelAsync().ConfigureAwait(true);
                _state = DictationState.Cancelled;
                Publish(_state, "Cancelled · audio was discarded; no history row was created.");
                _shortcuts.Reset();
                return;
            }

            if (snapshot.ShouldStartRecording)
            {
                _state = snapshot.State;
                Publish(_state, "Starting microphone…");
                try
                {
                    await Orchestrator.StartAsync(_defaults with { Mode = snapshot.Mode }).ConfigureAwait(true);
                    Publish(_state, "Listening · release Ctrl + Win to finish.");
                }
                catch (Exception ex)
                {
                    _state = DictationState.Failed;
                    Publish(_state, ActionableMessage(ex));
                    _shortcuts.Reset();
                }
                return;
            }

            // Space reclassification produces a state-only snapshot.  Keep
            // the existing audio session and update the pill without restart.
            if (snapshot.State == DictationState.Recording && snapshot.Mode == DictationMode.Toggle)
            {
                _state = DictationState.Recording;
                Publish(_state, "Listening · toggle mode; press Ctrl + Win + Space to insert.");
            }

            if (snapshot.ShouldStopRecording)
            {
                _state = DictationState.Processing;
                Publish(_state, "Finishing · transcribing, inserting, and saving locally…");
                try
                {
                    var result = await Orchestrator.FinishAsync().ConfigureAwait(true);
                    _state = DictationState.Inserted;
                    Publish(_state, InsertionMessage(result), result);
                    _shortcuts.Reset();
                }
                catch (Exception ex)
                {
                    _state = DictationState.Failed;
                    Publish(_state, ActionableMessage(ex));
                    _shortcuts.Reset();
                }
            }
        }
        catch (Exception ex)
        {
            _state = DictationState.Failed;
            Publish(_state, ActionableMessage(ex));
            _shortcuts.Reset();
        }
        finally { _inputGate.Release(); }
    }

    private void Publish(DictationState state, string message, DictationExecutionResult? result = null)
    {
        StateChanged?.Invoke(this, new DictationRuntimeState(
            state, _mode, IsRecording, state == DictationState.Processing, message, result));
    }

    private static ShortcutEvent Map(KeyboardHookEventArgs args) =>
        new(args.IsKeyDown ? ShortcutEventKind.KeyDown : ShortcutEventKind.KeyUp, args.VirtualKey switch
        {
            0xA2 => ShortcutKey.LeftCtrl,
            0xA3 => ShortcutKey.RightCtrl,
            0x5B => ShortcutKey.LeftWin,
            0x5C => ShortcutKey.RightWin,
            0x20 => ShortcutKey.Space,
            _ => ShortcutKey.Other
        }, args.IsInjected);

    private static string InsertionMessage(DictationExecutionResult result) => result.Insertion.Outcome switch
    {
        InsertionOutcome.Inserted => "Inserted · transcript saved to the local ledger.",
        InsertionOutcome.CopiedForManualPaste => result.Insertion.Detail ?? "Copied for manual paste; the original target rejected direct input.",
        InsertionOutcome.TargetChanged => result.Insertion.Detail ?? "Target changed; text was not inserted.",
        InsertionOutcome.TargetElevated => result.Insertion.Detail ?? "Target is elevated; text was not inserted.",
        _ => result.Insertion.Detail ?? "Transcription completed, but insertion failed."
    };

    private static string ActionableMessage(Exception exception) => exception switch
    {
        OperationCanceledException => "Cancelled · audio was discarded; no history row was created.",
        _ => exception.Message
    };

    // Factory-only constructor helper.  Keeping repository ownership in the
    // runtime makes history loading use the exact same SQLite file as writes.
    internal static DictationRuntime Create(
        DispatcherQueue dispatcher,
        DictationOrchestrator orchestrator,
        LowLevelKeyboardHook hook,
        DictationOptions defaults,
        IDictationRepository repository) =>
        new(dispatcher, orchestrator, hook, defaults, repository);
}

public sealed record DictationRuntimeState(
    DictationState State,
    DictationMode Mode,
    bool IsRecording,
    bool IsProcessing,
    string Message,
    DictationExecutionResult? Result = null);

internal static class RuntimeFactory
{
    public static DictationRuntime Create(DispatcherQueue dispatcher)
    {
        var settings = new UserSettingsStore().Load();
        var secretStore = new DpapiSecretStore();
        var groqKey = ReadGroqKey(secretStore);
        var http = new HttpClient { Timeout = TimeSpan.FromMinutes(3) };
        var providers = new List<ITranscriptionProvider>();
        IPolishProvider? polisher = null;
        if (!string.IsNullOrWhiteSpace(groqKey))
        {
            providers.Add(new GroqTranscriptionProvider(http, groqKey, settings.GroqTranscriptionModel));
            polisher = new GroqPolishProvider(http, groqKey, settings.GroqPolishModel);
        }
        else providers.Add(new UnavailableTranscriptionProvider(TranscriptionBackend.Groq,
            "Groq is not configured. Add a Groq API key in Settings → Providers or set FLOWERWHISP_GROQ_API_KEY."));

        var local = TryCreateLocalProvider(settings, out var localError);
        if (local is not null) providers.Add(local);
        else providers.Add(new UnavailableTranscriptionProvider(TranscriptionBackend.LocalWhisper, localError));

        var repository = new SqliteDictationRepository();
        var usage = new SqliteUsageAggregateRepository();
        var capture = new WasapiAudioCaptureService();
        var orchestrator = new DictationOrchestrator(
            capture, providers, polisher, new SendInputTextInsertionService(),
            new ForegroundTargetService(), repository, usage);
        var hook = new LowLevelKeyboardHook();
        var defaultBackend = settings.Backend;
        return DictationRuntime.Create(dispatcher, orchestrator, hook,
            new DictationOptions(Backend: defaultBackend, Polish: settings.Polish,
                Retention: settings.Retention, Language: settings.Language,
                AnalyticsEnabled: settings.AnalyticsEnabled), repository);
    }

    private static string? ReadGroqKey(ISecretStore secretStore)
    {
        var fromEnvironment = Environment.GetEnvironmentVariable("FLOWERWHISP_GROQ_API_KEY")
            ?? Environment.GetEnvironmentVariable("GROQ_API_KEY");
        if (!string.IsNullOrWhiteSpace(fromEnvironment)) return fromEnvironment;
        try { return secretStore.GetAsync("groq-api-key").GetAwaiter().GetResult(); }
        catch { return null; }
    }

    private static ITranscriptionProvider? TryCreateLocalProvider(FlowerWhispSettings settings, out string error)
    {
        var script = Environment.GetEnvironmentVariable("FLOWERWHISP_WHISPER_HOST_SCRIPT");
        if (string.IsNullOrWhiteSpace(script)) script = settings.WhisperHostScript;
        var checkout = Environment.GetEnvironmentVariable("FLOWERWHISP_WHISPER_CHECKOUT");
        if (string.IsNullOrWhiteSpace(checkout)) checkout = settings.WhisperCheckout;
        var python = Environment.GetEnvironmentVariable("FLOWERWHISP_WHISPER_PYTHON");
        if (string.IsNullOrWhiteSpace(python)) python = string.IsNullOrWhiteSpace(settings.WhisperPython) ? "python" : settings.WhisperPython;
        var model = Environment.GetEnvironmentVariable("FLOWERWHISP_MODEL");
        if (string.IsNullOrWhiteSpace(model)) model = settings.WhisperModel;
        var modelDirectory = Environment.GetEnvironmentVariable("FLOWERWHISP_MODEL_DIR");
        if (string.IsNullOrWhiteSpace(modelDirectory)) modelDirectory = settings.WhisperModelDirectory;
        var device = Environment.GetEnvironmentVariable("FLOWERWHISP_DEVICE");
        if (string.IsNullOrWhiteSpace(device)) device = settings.WhisperDevice;
        if (string.IsNullOrWhiteSpace(script))
        {
            var packaged = Path.Combine(AppContext.BaseDirectory, "local-whisper-host", "host.py");
            var source = Path.Combine(AppContext.BaseDirectory, "tools", "local-whisper-host", "host.py");
            script = File.Exists(packaged) ? packaged : source;
        }
        if (!File.Exists(script))
        {
            error = "Local Whisper is not configured. Set FLOWERWHISP_WHISPER_HOST_SCRIPT and FLOWERWHISP_WHISPER_CHECKOUT to an official checkout.";
            return null;
        }
        if (string.IsNullOrWhiteSpace(checkout))
        {
            error = "Local Whisper needs an official checkout. Set FLOWERWHISP_WHISPER_CHECKOUT; no model or canned transcript will be used.";
            return null;
        }
        var host = new ProcessLocalWhisperHost(python, script, model, checkout, modelDirectory, device);
        error = string.Empty;
        return new LocalWhisperTranscriptionProvider(host);
    }
}

internal sealed class UnavailableTranscriptionProvider : ITranscriptionProvider
{
    private readonly string _message;
    public UnavailableTranscriptionProvider(TranscriptionBackend backend, string message) { Backend = backend; _message = message; }
    public TranscriptionBackend Backend { get; }
    public Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default) =>
        Task.FromException<TranscriptionResult>(new InvalidOperationException(_message));
}

internal sealed class LocalWhisperTranscriptionProvider : ITranscriptionProvider
{
    private readonly ILocalWhisperHost _host;
    public LocalWhisperTranscriptionProvider(ILocalWhisperHost host) => _host = host;
    public TranscriptionBackend Backend => TranscriptionBackend.LocalWhisper;
    public Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default) =>
        _host.TranscribeAsync(request, cancellationToken);
}
