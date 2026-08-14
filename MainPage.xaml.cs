using FlowerWhisp.Core;
using FlowerWhisp.Infrastructure;
using FlowerWhisp.Platform.Windows;
using FlowerWhisp.ViewModels;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System.Text.Json;
using Windows.Storage;
using Windows.Storage.Pickers;
using Windows.ApplicationModel.DataTransfer;

namespace FlowerWhisp;

public sealed partial class MainPage : Page
{
    public MainPageViewModel ViewModel { get; } = new();
    private string _activeSection = "Dictations";
    private SnippetRow? _selectedSnippet;
    private StyleRow? _selectedStyle;
    private bool _runtimeSubscribed;
    private readonly SignalLedgerStore _contentStore = new();
    private readonly UserSettingsStore _settingsStore = new();
    private readonly DpapiSecretStore _secretStore = new();
    private bool _localStateLoaded;
    private bool _scratchpadCapture;

    public MainPage()
    {
        InitializeComponent();
        DataContext = ViewModel;
        // The ViewModel's sample rows are useful to the design shell but must
        // never appear as real history in a running app.
        ViewModel.Dictations.Clear();
        LedgerNav.SelectedItem = LedgerNav.MenuItems[0];
        UpdateCounters();
        Loaded += OnLoaded;
        Unloaded += OnUnloaded;
    }

    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (!_localStateLoaded)
        {
            _localStateLoaded = true;
            await LoadLocalStateAsync();
        }

        if (App.Runtime is null || _runtimeSubscribed) return;
        App.Runtime.StateChanged += OnRuntimeStateChanged;
        _runtimeSubscribed = true;
        try
        {
            foreach (var record in await App.Runtime.LoadHistoryAsync()) AddLedgerRow(record);
            UpdateCounters();
            OnRuntimeStateChanged(this, new DictationRuntimeState(
                DictationState.Idle, App.Runtime.Mode, false, false,
                "Ready - audio is captured only after you start a real session."));
        }
        catch (Exception ex) { ViewModel.StatusText = $"History unavailable - {ex.Message}"; }
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        if (_localStateLoaded) _ = PersistContentAsync();
        if (!_runtimeSubscribed || App.Runtime is null) return;
        App.Runtime.StateChanged -= OnRuntimeStateChanged;
        _runtimeSubscribed = false;
    }

    private void OnNavSelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
    {
        var tag = (args.SelectedItem as NavigationViewItem)?.Tag?.ToString() ?? "Dictations";
        ShowSection(tag);
    }

    private void OnSettingsClick(object sender, RoutedEventArgs e) => ShowSection("Settings");

    private void ShowSection(string section)
    {
        _activeSection = section;
        ViewModel.ActiveSection = section;
        PageEyebrow.Text = section.ToUpperInvariant();
        PageTitle.Text = section switch
        {
            "Insights" => "Patterns, not pressure.",
            "Dictionary" => "Your words, your rules.",
            "Snippets" => "Small phrases, ready.",
            "Styles" => "Make the voice yours.",
            "Transforms" => "Deliberate cleanup.",
            "Scratchpad" => "A private listening room.",
            "Settings" => "Keep the edges yours.",
            _ => "Your signal, captured."
        };
        PageSubtitle.Text = section switch
        {
            "Dictations" => "Local-first dictation with a quiet paper trail.",
            "Insights" => "Privacy-safe aggregates help you notice rhythm without storing the words.",
            "Dictionary" => "Protected names and phrases stay on this device.",
            "Snippets" => "Reusable phrases, available without leaving the current app.",
            "Styles" => "Choose how an optional text-only cleanup should sound.",
            "Transforms" => "Small, deliberate cleanup steps with explicit provider state.",
            "Scratchpad" => "A private workspace for capture, comparison, and deliberate export.",
            _ => "General - Pill - Shortcuts - Audio - Providers - Privacy - Appearance - Data - Updates - About"
        };

        DictationsPanel.Visibility = section == "Dictations" ? Visibility.Visible : Visibility.Collapsed;
        InsightsPanel.Visibility = section == "Insights" ? Visibility.Visible : Visibility.Collapsed;
        DictionaryPanel.Visibility = section == "Dictionary" ? Visibility.Visible : Visibility.Collapsed;
        SnippetsPanel.Visibility = section == "Snippets" ? Visibility.Visible : Visibility.Collapsed;
        StylesPanel.Visibility = section == "Styles" ? Visibility.Visible : Visibility.Collapsed;
        TransformsPanel.Visibility = section == "Transforms" ? Visibility.Visible : Visibility.Collapsed;
        ScratchpadPanel.Visibility = section == "Scratchpad" ? Visibility.Visible : Visibility.Collapsed;
        SettingsPanel.Visibility = section == "Settings" ? Visibility.Visible : Visibility.Collapsed;
    }

    private async void OnRecordClick(object sender, RoutedEventArgs e)
    {
        if (App.Runtime is null)
        {
            ViewModel.StatusText = "Runtime is not ready - restart FlowerWhisp to initialize the microphone and provider boundary.";
            return;
        }

        if (!App.Runtime.IsRecording)
            await App.Runtime.StartFromUiAsync(ViewModel.IsToggleMode);
        else
            await App.Runtime.FinishFromUiAsync();
    }

    private void OnOpenPillClick(object sender, RoutedEventArgs e)
    {
        App.EnsurePill().AppWindow.Show(activateWindow: false);
        ViewModel.StatusText = "Pill open - recording state stays visible without stealing focus";
    }

    private void OnCopyDictationClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is LedgerDictation row)
        {
            var package = new DataPackage();
            package.SetText(row.FinalText);
            Clipboard.SetContent(package);
            ViewModel.StatusText = $"Copied '{row.Title}' - ready for manual paste";
        }
    }

    private async void OnDeleteDictationClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is LedgerDictation row)
        {
            if (App.Runtime is not null) await App.Runtime.DeleteHistoryAsync(row.Id);
            ViewModel.Dictations.Remove(row);
            ViewModel.StatusText = "Dictation removed from the local ledger";
            UpdateCounters();
        }
    }

    private void OnDictionarySearchChanged(object sender, TextChangedEventArgs e)
    {
        var query = DictionarySearchBox.Text.Trim();
        DictionaryList.ItemsSource = string.IsNullOrWhiteSpace(query)
            ? ViewModel.DictionaryEntries
            : ViewModel.DictionaryEntries.Where(x => x.Phrase.Contains(query, StringComparison.OrdinalIgnoreCase) || x.Replacement.Contains(query, StringComparison.OrdinalIgnoreCase)).ToList();
    }

    private void OnAddDictionaryClick(object sender, RoutedEventArgs e)
    {
        ViewModel.AddDictionaryEntry(DictionaryPhraseBox.Text, DictionaryReplacementBox.Text);
        DictionaryPhraseBox.Text = string.Empty;
        DictionaryReplacementBox.Text = string.Empty;
        DictionaryList.ItemsSource = ViewModel.DictionaryEntries;
        ViewModel.StatusText = "Dictionary entry added locally";
        _ = PersistContentAsync();
    }

    private async void OnImportDictionaryClick(object sender, RoutedEventArgs e)
    {
        try
        {
            var picker = new FileOpenPicker();
            picker.FileTypeFilter.Add(".json");
            picker.FileTypeFilter.Add(".csv");
            picker.FileTypeFilter.Add(".txt");
            InitializePicker(picker);
            var file = await picker.PickSingleFileAsync();
            if (file is null) return;

            var imported = await ParseDictionaryImportAsync(file);
            if (imported.Count == 0)
            {
                ViewModel.StatusText = "No dictionary entries were found in that file.";
                return;
            }

            var existing = new HashSet<string>(ViewModel.DictionaryEntries.Select(x => x.Phrase), StringComparer.OrdinalIgnoreCase);
            var added = 0;
            foreach (var entry in imported)
            {
                if (existing.Add(entry.Phrase))
                {
                    ViewModel.DictionaryEntries.Insert(0, new DictionaryEntryRow(entry.Phrase, entry.Replacement, false));
                    added++;
                }
            }
            DictionaryList.ItemsSource = ViewModel.DictionaryEntries;
            await PersistContentAsync();
            ViewModel.StatusText = $"Imported {added} dictionary entr{(added == 1 ? "y" : "ies")} locally.";
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            ViewModel.StatusText = $"Dictionary import failed - {ex.Message}";
        }
    }

    private void OnRemoveDictionaryClick(object sender, RoutedEventArgs e)
    {
        if ((sender as Button)?.Tag is DictionaryEntryRow row && !row.Protected)
        {
            ViewModel.DictionaryEntries.Remove(row);
            ViewModel.StatusText = "Dictionary entry removed";
            _ = PersistContentAsync();
        }
        else ViewModel.StatusText = "Protected entries stay in place - edit the replacement instead";
    }

    private void OnAddSnippetClick(object sender, RoutedEventArgs e)
    {
        var name = string.IsNullOrWhiteSpace(SnippetNameBox.Text) ? "Untitled snippet" : SnippetNameBox.Text;
        ViewModel.AddSnippet(name, "Write the phrase you want available at a keystroke.");
        SnippetNameBox.Text = string.Empty;
        ViewModel.StatusText = "Snippet created - select it to edit the preview";
        _ = PersistContentAsync();
    }

    private void OnSnippetSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _selectedSnippet = SnippetsList.SelectedItem as SnippetRow;
        if (_selectedSnippet is not null) SnippetContentBox.Text = _selectedSnippet.Content;
    }

    private void OnSaveSnippetClick(object sender, RoutedEventArgs e)
    {
        if (_selectedSnippet is null) { ViewModel.StatusText = "Select a snippet first"; return; }
        var index = ViewModel.Snippets.IndexOf(_selectedSnippet);
        ViewModel.Snippets[index] = _selectedSnippet with { Content = SnippetContentBox.Text };
        _selectedSnippet = ViewModel.Snippets[index];
        ViewModel.StatusText = "Snippet preview saved locally";
        _ = PersistContentAsync();
    }

    private void OnStyleSelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        _selectedStyle = StyleCombo.SelectedItem as StyleRow;
        if (_selectedStyle is not null)
        {
            ViewModel.SelectedStyle = _selectedStyle.Name;
            StyleInstructionsBox.Text = _selectedStyle.Instructions;
        }
    }

    private void OnSaveStyleClick(object sender, RoutedEventArgs e)
    {
        if (_selectedStyle is null) { ViewModel.StatusText = "Select a style profile first"; return; }
        var index = ViewModel.Styles.IndexOf(_selectedStyle);
        ViewModel.Styles[index] = _selectedStyle with { Instructions = StyleInstructionsBox.Text };
        _selectedStyle = ViewModel.Styles[index];
        ViewModel.StatusText = "Style profile saved locally";
        _ = PersistContentAsync();
    }

    private void OnNewStyleClick(object sender, RoutedEventArgs e)
    {
        ViewModel.AddStyle("Untitled style", "Describe the tone, structure, and constraints you want.");
        StyleCombo.SelectedIndex = ViewModel.Styles.Count - 1;
        ViewModel.StatusText = "New style profile added";
        _ = PersistContentAsync();
    }

    private async void OnScratchCaptureClick(object sender, RoutedEventArgs e)
    {
        if (App.Runtime is null)
        {
            ViewModel.StatusText = "Runtime is not ready - restart FlowerWhisp and try again.";
            return;
        }
        if (!App.Runtime.IsRecording)
        {
            _scratchpadCapture = true;
            await App.Runtime.StartScratchpadAsync();
        }
        else if (_scratchpadCapture)
            await App.Runtime.FinishFromUiAsync();
        else
            ViewModel.StatusText = "Another dictation is already recording; finish or cancel it first.";
    }

    private async void OnExportScratchpadClick(object sender, RoutedEventArgs e)
    {
        try
        {
            // Read the controls once more so an in-progress TwoWay binding edit
            // is included even when the user clicks Export immediately.
            ViewModel.ScratchRaw = ScratchRawBox.Text;
            ViewModel.ScratchPolished = ScratchPolishedBox.Text;
            await PersistContentAsync();

            var picker = new FileSavePicker
            {
                SuggestedFileName = "flowerwhisp-scratchpad.md",
                SuggestedStartLocation = PickerLocationId.DocumentsLibrary
            };
            picker.FileTypeChoices.Add("Markdown", [".md"]);
            picker.FileTypeChoices.Add("Plain text", [".txt"]);
            InitializePicker(picker);
            var file = await picker.PickSaveFileAsync();
            if (file is null) return;

            var isText = file.FileType.Equals(".txt", StringComparison.OrdinalIgnoreCase);
            var output = isText
                ? $"FlowerWhisp Scratchpad\r\n\r\nRAW CAPTURE\r\n\r\n{ViewModel.ScratchRaw}\r\n\r\nCOMPARISON\r\n\r\n{ViewModel.ScratchPolished}\r\n"
                : $"# FlowerWhisp Scratchpad\r\n\r\n## Raw capture\r\n\r\n{ViewModel.ScratchRaw}\r\n\r\n## Comparison\r\n\r\n{ViewModel.ScratchPolished}\r\n";
            await FileIO.WriteTextAsync(file, output);
            ViewModel.StatusText = $"Scratchpad exported locally to {file.Name}.";
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            ViewModel.StatusText = $"Scratchpad export failed - {ex.Message}";
        }
    }

    private async void OnSaveGroqKeyClick(object sender, RoutedEventArgs e)
    {
        var key = GroqApiKeyBox.Password.Trim();
        if (string.IsNullOrWhiteSpace(key))
        {
            GroqProviderStatus.Message = "Enter a Groq API key before saving.";
            ViewModel.StatusText = GroqProviderStatus.Message;
            return;
        }

        try
        {
            var sttModel = SelectedTag(GroqSttModelCombo, "whisper-large-v3-turbo");
            var polishModel = SelectedTag(GroqPolishModelCombo, "openai/gpt-oss-20b");
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(30) };
            var stt = new GroqTranscriptionProvider(http, key, sttModel);
            var polish = new GroqPolishProvider(http, key, polishModel);
            var sttModels = await stt.GetAvailableAllowedModelsAsync();
            var polishModels = await polish.GetAvailableAllowedModelsAsync();
            if (!sttModels.Any(model => model.Id.Equals(sttModel, StringComparison.OrdinalIgnoreCase)))
                throw new InvalidOperationException($"Your Groq organization does not currently expose {sttModel}.");
            if (PolishToggle.IsOn && !polishModels.Any(model => model.Id.Equals(polishModel, StringComparison.OrdinalIgnoreCase)))
                throw new InvalidOperationException($"Your Groq organization does not currently expose {polishModel}.");
            await _secretStore.SetAsync("groq-api-key", key);
            var settings = await _settingsStore.LoadAsync();
            await _settingsStore.SaveAsync(settings with
            {
                GroqTranscriptionModel = sttModel,
                GroqPolishModel = polishModel,
                Polish = PolishToggle.IsOn ? PolishMode.Light : PolishMode.Off
            });
            GroqApiKeyBox.Password = string.Empty;
            GroqProviderStatus.Message = $"Key validated. STT: {string.Join(", ", sttModels.Select(x => x.Id))}. Polish: {string.Join(", ", polishModels.Select(x => x.Id))}. Stored with Windows DPAPI; restart to apply.";
            ViewModel.StatusText = "Groq key saved securely - restart required to apply provider changes.";
        }
        catch (Exception ex)
        {
            GroqProviderStatus.Message = $"Groq key could not be stored - {ex.Message}";
            ViewModel.StatusText = GroqProviderStatus.Message;
        }
    }

    private async void OnSaveGeneralSettingsClick(object sender, RoutedEventArgs e)
    {
        try
        {
            var settings = await _settingsStore.LoadAsync();
            var backendText = SelectedTag(DefaultBackendCombo, nameof(TranscriptionBackend.Groq));
            var retentionText = SelectedTag(DefaultRetentionCombo, nameof(RetentionPolicy.DeleteAfter24Hours));
            var backend = Enum.TryParse<TranscriptionBackend>(backendText, out var parsedBackend)
                ? parsedBackend : TranscriptionBackend.Groq;
            var retention = Enum.TryParse<RetentionPolicy>(retentionText, out var parsedRetention)
                ? parsedRetention : RetentionPolicy.DeleteAfter24Hours;
            await _settingsStore.SaveAsync(settings with
            {
                Backend = backend,
                Retention = retention,
                Language = SelectedTag(DefaultLanguageCombo, "auto"),
                AnalyticsEnabled = AnalyticsToggle.IsOn,
                GroqTranscriptionModel = SelectedTag(GroqSttModelCombo, "whisper-large-v3-turbo"),
                GroqPolishModel = SelectedTag(GroqPolishModelCombo, "openai/gpt-oss-20b"),
                Polish = PolishToggle.IsOn ? PolishMode.Light : PolishMode.Off
            });
            ViewModel.StatusText = "General and provider preferences saved locally - restart required to apply runtime changes.";
        }
        catch (Exception ex) { ViewModel.StatusText = $"Settings could not be saved - {ex.Message}"; }
    }

    private async void OnClearGroqKeyClick(object sender, RoutedEventArgs e)
    {
        try
        {
            await _secretStore.DeleteAsync("groq-api-key");
            GroqApiKeyBox.Password = string.Empty;
            GroqProviderStatus.Message = "Saved Groq key removed from the DPAPI store. Restart FlowerWhisp to disable the provider.";
            ViewModel.StatusText = "Groq key removed securely - restart required to apply provider changes.";
        }
        catch (Exception ex)
        {
            GroqProviderStatus.Message = $"Groq key could not be removed - {ex.Message}";
            ViewModel.StatusText = GroqProviderStatus.Message;
        }
    }

    private async void OnBrowseWhisperCheckoutClick(object sender, RoutedEventArgs e)
    {
        try
        {
            var picker = new FolderPicker { SuggestedStartLocation = PickerLocationId.ComputerFolder };
            picker.FileTypeFilter.Add("*");
            InitializePicker(picker);
            var folder = await picker.PickSingleFolderAsync();
            if (folder is null) return;
            WhisperCheckoutBox.Text = folder.Path;
            SetWhisperValidationMessage(folder.Path);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            LocalWhisperStatus.Message = $"Could not choose a checkout folder - {ex.Message}";
        }
    }

    private async void OnSaveWhisperSettingsClick(object sender, RoutedEventArgs e)
    {
        var checkout = WhisperCheckoutBox.Text.Trim();
        if (!TryValidateWhisperCheckout(checkout, out var fullPath, out var validationMessage))
        {
            LocalWhisperStatus.Message = validationMessage;
            ViewModel.StatusText = validationMessage;
            return;
        }

        try
        {
            var existing = await _settingsStore.LoadAsync();
            var model = (WhisperModelCombo.SelectedItem as ComboBoxItem)?.Tag?.ToString()
                ?? (WhisperModelCombo.SelectedItem as ComboBoxItem)?.Content?.ToString()?.Split(' ')[0]
                ?? existing.WhisperModel;
            await _settingsStore.SaveAsync(existing with
            {
                WhisperCheckout = fullPath,
                WhisperModel = string.IsNullOrWhiteSpace(model) ? "small" : model
            });
            LocalWhisperStatus.Message = string.IsNullOrWhiteSpace(fullPath)
                ? "Local Whisper checkout cleared. Restart FlowerWhisp to apply the provider change."
                : $"Checkout validated: {validationMessage} Restart FlowerWhisp to apply the saved path.";
            ViewModel.StatusText = "Local Whisper settings saved - restart required to apply provider changes.";
        }
        catch (Exception ex)
        {
            LocalWhisperStatus.Message = $"Local Whisper settings could not be saved - {ex.Message}";
            ViewModel.StatusText = LocalWhisperStatus.Message;
        }
    }

    private void OnCheckWhisperClick(object sender, RoutedEventArgs e)
    {
        SetWhisperValidationMessage(WhisperCheckoutBox.Text);
        ViewModel.StatusText = LocalWhisperStatus.Message;
    }

    private void OnRuntimeStateChanged(object? sender, DictationRuntimeState state)
    {
        if (!DispatcherQueue.HasThreadAccess)
        {
            DispatcherQueue.TryEnqueue(() => OnRuntimeStateChanged(sender, state));
            return;
        }

        ViewModel.IsRecording = state.IsRecording;
        ViewModel.IsToggleMode = state.Mode == DictationMode.Toggle;
        ViewModel.StatusText = state.Message;
        if (state.State is DictationState.Cancelled or DictationState.Failed)
            _scratchpadCapture = false;
        RecordButton.Content = state.IsRecording
            ? (state.IsProcessing ? "Processing..." : "Finish recording")
            : "Start recording";
        SidebarCaptureStatus.Text = state.IsRecording
            ? (state.Mode == DictationMode.Toggle ? "Recording - Toggle" : "Recording - Hold")
            : state.State switch
            {
                DictationState.Failed => "Needs attention",
                DictationState.Cancelled => "Cancelled",
                _ => "Ready - Hold"
            };
        SignalProgress.Value = state.IsRecording ? (state.IsProcessing ? 88 : state.Mode == DictationMode.Toggle ? 72 : 58) : 12;

        if (state.Result is not null)
        {
            var options = state.Result.Options;
            if (options.Retention != RetentionPolicy.NeverStore)
            {
                AddLedgerRow(new DictationRecord(
                    state.Result.RequestId, DateTimeOffset.UtcNow,
                    state.Result.Transcript.Text, state.Result.FinalText, options.Backend,
                    options.Polish, options.Retention,
                    state.Result.Transcript.Duration ?? TimeSpan.Zero,
                    state.Result.Transcript.DetectedLanguage));
                UpdateCounters();
            }
            if (_scratchpadCapture)
            {
                ViewModel.ScratchRaw = state.Result.Transcript.Text;
                ViewModel.ScratchPolished = state.Result.FinalText;
                _scratchpadCapture = false;
                _ = PersistContentAsync();
                ViewModel.StatusText = "Scratchpad capture saved locally; no external target received input.";
            }
        }
    }

    private void UpdateCounters()
    {
        TodayStat.Text = ViewModel.TodayCount.ToString();
        TodayCountText.Text = $"{ViewModel.TodayCount} dictations";
    }

    private async Task LoadLocalStateAsync()
    {
        try
        {
            var content = await _contentStore.LoadAsync();
            if (content is not null) ViewModel.LoadPersistedContent(content);
            DictionaryList.ItemsSource = ViewModel.DictionaryEntries;
            StyleCombo.SelectedIndex = ViewModel.Styles.Count == 0 ? -1 : 0;

            var settings = await _settingsStore.LoadAsync();
            WhisperCheckoutBox.Text = settings.WhisperCheckout;
            var modelIndex = settings.WhisperModel.Equals("base", StringComparison.OrdinalIgnoreCase) ? 1 : 0;
            WhisperModelCombo.SelectedIndex = modelIndex;
            DefaultBackendCombo.SelectedIndex = settings.Backend == TranscriptionBackend.LocalWhisper ? 1 : 0;
            DefaultRetentionCombo.SelectedIndex = settings.Retention switch
            {
                RetentionPolicy.KeepForever => 0,
                RetentionPolicy.NeverStore => 2,
                _ => 1
            };
            DefaultLanguageCombo.SelectedIndex = settings.Language.Equals("en", StringComparison.OrdinalIgnoreCase) ? 1
                : settings.Language.Equals("hi", StringComparison.OrdinalIgnoreCase) ? 2 : 0;
            AnalyticsToggle.IsOn = settings.AnalyticsEnabled;
            GroqSttModelCombo.SelectedIndex = settings.GroqTranscriptionModel.Equals("whisper-large-v3", StringComparison.OrdinalIgnoreCase) ? 1 : 0;
            GroqPolishModelCombo.SelectedIndex = settings.GroqPolishModel.Equals("openai/gpt-oss-120b", StringComparison.OrdinalIgnoreCase) ? 1 : 0;
            PolishToggle.IsOn = settings.Polish != PolishMode.Off;
            SetWhisperValidationMessage(settings.WhisperCheckout);

            try
            {
                var key = await _secretStore.GetAsync("groq-api-key");
                GroqProviderStatus.Message = string.IsNullOrWhiteSpace(key)
                    ? "No Groq key is saved. Add one here; it will be protected by Windows DPAPI and applied after restart."
                    : "A Groq key is saved in the Windows DPAPI store. Restart is required after changing it.";
            }
            catch (Exception ex)
            {
                GroqProviderStatus.Message = $"Groq key status unavailable - {ex.Message}";
            }
        }
        catch (Exception ex)
        {
            ViewModel.StatusText = $"Local settings unavailable - {ex.Message}";
        }
    }

    private async Task PersistContentAsync()
    {
        try
        {
            await _contentStore.SaveAsync(ViewModel.CapturePersistedContent());
        }
        catch (Exception ex)
        {
            ViewModel.StatusText = $"Local library could not be saved - {ex.Message}";
        }
    }

    private static void InitializePicker(object picker) =>
        WinRT.Interop.InitializeWithWindow.Initialize(picker, App.WindowHandle);

    private static string SelectedTag(ComboBox combo, string fallback) =>
        (combo.SelectedItem as ComboBoxItem)?.Tag?.ToString() ?? fallback;

    private static async Task<IReadOnlyList<(string Phrase, string Replacement)>> ParseDictionaryImportAsync(StorageFile file)
    {
        var text = await FileIO.ReadTextAsync(file);
        var extension = System.IO.Path.GetExtension(file.Name);
        var rows = new List<(string Phrase, string Replacement)>();
        if (extension.Equals(".json", StringComparison.OrdinalIgnoreCase))
        {
            using var document = JsonDocument.Parse(text);
            var root = document.RootElement;
            var items = root.ValueKind == JsonValueKind.Array
                ? root.EnumerateArray().ToArray()
                : root.TryGetProperty("Dictionary", out var dictionary) && dictionary.ValueKind == JsonValueKind.Array
                    ? dictionary.EnumerateArray().ToArray()
                    : root.TryGetProperty("dictionary", out var lowerDictionary) && lowerDictionary.ValueKind == JsonValueKind.Array
                        ? lowerDictionary.EnumerateArray().ToArray()
                        : [];
            foreach (var item in items)
            {
                if (item.ValueKind != JsonValueKind.Object) continue;
                var phrase = GetJsonString(item, "Phrase") ?? GetJsonString(item, "phrase");
                if (string.IsNullOrWhiteSpace(phrase)) continue;
                var replacement = GetJsonString(item, "Replacement") ?? GetJsonString(item, "replacement") ?? phrase;
                rows.Add((phrase.Trim(), replacement.Trim()));
            }
            return rows;
        }

        foreach (var line in text.Split(["\r\n", "\n"], StringSplitOptions.RemoveEmptyEntries))
        {
            var trimmed = line.Trim();
            if (trimmed.Length == 0 || trimmed.StartsWith('#') || trimmed.StartsWith("//")) continue;
            var separator = trimmed.IndexOfAny([',', '\t']);
            var phrase = separator < 0 ? trimmed : trimmed[..separator].Trim();
            var replacement = separator < 0 ? phrase : trimmed[(separator + 1)..].Trim();
            if (phrase.Equals("phrase", StringComparison.OrdinalIgnoreCase)) continue;
            if (phrase.Length > 0) rows.Add((phrase, string.IsNullOrWhiteSpace(replacement) ? phrase : replacement));
        }
        return rows;
    }

    private static string? GetJsonString(JsonElement item, string name) =>
        item.TryGetProperty(name, out var value) && value.ValueKind == JsonValueKind.String
            ? value.GetString()
            : null;

    private void SetWhisperValidationMessage(string? checkout)
    {
        if (TryValidateWhisperCheckout(checkout, out _, out var message))
            LocalWhisperStatus.Message = string.IsNullOrWhiteSpace(checkout)
                ? "No local checkout configured. The app will not use a bundled model or canned transcript."
                : message;
        else LocalWhisperStatus.Message = message;
    }

    private static bool TryValidateWhisperCheckout(string? checkout, out string fullPath, out string message)
    {
        fullPath = string.Empty;
        if (string.IsNullOrWhiteSpace(checkout))
        {
            message = "No local checkout configured.";
            return true;
        }

        try { fullPath = System.IO.Path.GetFullPath(checkout.Trim()); }
        catch (Exception ex) { message = $"Checkout path is invalid - {ex.Message}"; return false; }
        if (!Directory.Exists(fullPath))
        {
            message = "Checkout folder was not found. Choose an existing local Whisper checkout.";
            return false;
        }
        if (!Directory.Exists(System.IO.Path.Combine(fullPath, "whisper")))
        {
            message = "Folder exists, but it does not contain the expected whisper package folder.";
            return false;
        }
        message = "Checkout folder found with the expected whisper package.";
        return true;
    }

    private void AddLedgerRow(DictationRecord record)
    {
        var local = record.CreatedAt.ToLocalTime();
        var timestamp = local.Date == DateTime.Today ? $"Today - {local:HH:mm}" : local.ToString("MMM d - HH:mm");
        var backend = record.Backend == TranscriptionBackend.LocalWhisper ? "Local Whisper" : "Groq";
        var polish = record.PolishMode == PolishMode.Off ? "Off" : "On";
        var duration = record.Duration.TotalSeconds < 1 ? "<1 sec" : $"{Math.Round(record.Duration.TotalSeconds):0} sec";
        var title = string.IsNullOrWhiteSpace(record.RawText) ? "Untitled signal" : record.RawText[..Math.Min(36, record.RawText.Length)];
        ViewModel.Dictations.Insert(0, new LedgerDictation(record.Id, title, record.RawText, record.FinalText, timestamp, backend, polish, duration));
        ViewModel.TodayCount = ViewModel.Dictations.Count(row => row.Timestamp.StartsWith("Today", StringComparison.Ordinal));
    }
}
