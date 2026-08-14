using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using FlowerWhisp.Core;
using FlowerWhisp.Infrastructure;

namespace FlowerWhisp.ViewModels;

/// <summary>
/// UI state for the Signal Ledger. The page deliberately owns only local,
/// presentation state; production providers can be connected at this seam
/// without changing the shell or its interaction affordances.
/// </summary>
public sealed class MainPageViewModel : ObservableObject
{
    private string _activeSection = "Dictations";
    private string _statusText = "Ready · no audio retained until a dictation completes";
    private string _scratchRaw = "";
    private string _scratchPolished = "";
    private string _dictionarySearch = "";
    private int _todayCount;
    private bool _isRecording;
    private bool _isToggleMode;
    private bool _polishEnabled;
    private bool _cleanupFillerWords = true;
    private bool _cleanupPunctuation = true;
    private bool _cleanupParagraphs = true;
    private string _selectedStyle = "Clean notes";

    public MainPageViewModel()
    {
        Dictations = [];
        DictionaryEntries = new ObservableCollection<DictionaryEntryRow>([
            new("FlowerWhisp", "FlowerWhisp", true), new("Signal Ledger", "Signal Ledger", true), new("Maya", "Maya", false)
        ]);
        Snippets = new ObservableCollection<SnippetRow>([
            new("Follow up", "I’ll follow up with the next step by {date}.", "⌘ F"),
            new("Meeting recap", "Here’s the short version of what we decided: ", "⌘ R")
        ]);
        Styles = new ObservableCollection<StyleRow>([
            new("Clean notes", "Short sentences. Keep the speaker’s meaning. Use plain punctuation.", true),
            new("Warm email", "Friendly, concise, and specific. Keep a human opening and a clear ask.", false),
            new("Technical log", "Preserve identifiers and steps. Prefer numbered lists for procedures.", false)
        ]);
        Transforms = new ObservableCollection<TransformRow>([
            new("Remove filler words", "Removes ‘um’, ‘uh’, and repeated starts without changing meaning.", true),
            new("Sentence case", "Normalises casing while preserving acronyms and code identifiers.", true),
            new("Tighten paragraphs", "Groups short thoughts into readable paragraphs.", false)
        ]);
        Heatmap = new ObservableCollection<HeatmapDay>([
            new("Mon", "12", 0.88), new("Tue", "7", 0.55), new("Wed", "15", 1.0), new("Thu", "3", 0.24), new("Fri", "11", 0.76), new("Sat", "0", 0.08), new("Sun", "5", 0.4)
        ]);
        _todayCount = Dictations.Count(row => row.Timestamp.StartsWith("Today", StringComparison.Ordinal));
        ScratchRaw = "A private place to think out loud.\n\nPaste a draft here or capture with the pill.";
        ScratchPolished = "A private place to think out loud.\n\nPaste a draft here or capture with the pill.";
    }

    public ObservableCollection<LedgerDictation> Dictations { get; }
    public ObservableCollection<DictionaryEntryRow> DictionaryEntries { get; }
    public ObservableCollection<SnippetRow> Snippets { get; }
    public ObservableCollection<StyleRow> Styles { get; }
    public ObservableCollection<TransformRow> Transforms { get; }
    public ObservableCollection<HeatmapDay> Heatmap { get; }

    public string ActiveSection { get => _activeSection; set => SetProperty(ref _activeSection, value); }
    public string StatusText { get => _statusText; set => SetProperty(ref _statusText, value); }
    public string ScratchRaw { get => _scratchRaw; set => SetProperty(ref _scratchRaw, value); }
    public string ScratchPolished { get => _scratchPolished; set => SetProperty(ref _scratchPolished, value); }
    public string DictionarySearch { get => _dictionarySearch; set => SetProperty(ref _dictionarySearch, value); }
    public int TodayCount { get => _todayCount; set => SetProperty(ref _todayCount, value); }
    public bool IsRecording { get => _isRecording; set => SetProperty(ref _isRecording, value); }
    public bool IsToggleMode { get => _isToggleMode; set => SetProperty(ref _isToggleMode, value); }
    public bool PolishEnabled { get => _polishEnabled; set => SetProperty(ref _polishEnabled, value); }
    public bool CleanupFillerWords { get => _cleanupFillerWords; set => SetProperty(ref _cleanupFillerWords, value); }
    public bool CleanupPunctuation { get => _cleanupPunctuation; set => SetProperty(ref _cleanupPunctuation, value); }
    public bool CleanupParagraphs { get => _cleanupParagraphs; set => SetProperty(ref _cleanupParagraphs, value); }
    public string SelectedStyle { get => _selectedStyle; set => SetProperty(ref _selectedStyle, value); }

    public void AddDictation(string title, string text, string backend = "Groq")
    {
        Dictations.Insert(0, new LedgerDictation(Guid.NewGuid(), title, text, text, "Just now", backend, PolishEnabled ? "Light" : "Off", "12 sec"));
        TodayCount++;
    }

    public void AddDictionaryEntry(string phrase, string replacement)
    {
        if (string.IsNullOrWhiteSpace(phrase)) return;
        DictionaryEntries.Insert(0, new DictionaryEntryRow(phrase.Trim(), string.IsNullOrWhiteSpace(replacement) ? phrase.Trim() : replacement.Trim(), false));
    }

    public void AddSnippet(string name, string content)
    {
        if (string.IsNullOrWhiteSpace(name) || string.IsNullOrWhiteSpace(content)) return;
        Snippets.Insert(0, new SnippetRow(name.Trim(), content.Trim(), "New"));
    }

    public void AddStyle(string name, string instructions)
    {
        if (string.IsNullOrWhiteSpace(name)) return;
        Styles.Add(new StyleRow(name.Trim(), instructions.Trim(), false));
    }

    public void AddTransform(string name, string description)
    {
        if (string.IsNullOrWhiteSpace(name)) return;
        Transforms.Add(new TransformRow(name.Trim(), description.Trim(), false));
    }

    /// <summary>Rehydrates only user-authored Signal Ledger state.</summary>
    public void LoadPersistedContent(SignalLedgerContent content)
    {
        ArgumentNullException.ThrowIfNull(content);

        if (content.Dictionary.Count > 0)
        {
            DictionaryEntries.Clear();
            foreach (var entry in content.Dictionary.Where(x => !string.IsNullOrWhiteSpace(x.Phrase)))
            {
                var phrase = entry.Phrase.Trim();
                DictionaryEntries.Add(new DictionaryEntryRow(
                    phrase,
                    string.IsNullOrWhiteSpace(entry.Replacement) ? phrase : entry.Replacement.Trim(),
                    entry.Protected));
            }
            EnsureProtectedDictionaryEntries();
        }

        if (content.Snippets.Count > 0)
        {
            Snippets.Clear();
            foreach (var snippet in content.Snippets.Where(x => !string.IsNullOrWhiteSpace(x.Name)))
                Snippets.Add(new SnippetRow(snippet.Name.Trim(), snippet.Content ?? string.Empty, snippet.Shortcut ?? "New"));
        }

        if (content.Styles.Count > 0)
        {
            Styles.Clear();
            foreach (var style in content.Styles.Where(x => !string.IsNullOrWhiteSpace(x.Name)))
                Styles.Add(new StyleRow(style.Name.Trim(), style.Instructions ?? string.Empty, style.BuiltIn));
        }

        if (!string.IsNullOrWhiteSpace(content.ScratchRaw)) ScratchRaw = content.ScratchRaw;
        if (!string.IsNullOrWhiteSpace(content.ScratchPolished)) ScratchPolished = content.ScratchPolished;
    }

    public SignalLedgerContent CapturePersistedContent() => new()
    {
        Dictionary = DictionaryEntries
            .Select(x => new LocalDictionaryEntry(x.Phrase, x.Replacement, x.Protected))
            .ToList(),
        Snippets = Snippets
            .Select(x => new LocalSnippet(x.Name, x.Content, x.Shortcut))
            .ToList(),
        Styles = Styles
            .Select(x => new LocalStyle(x.Name, x.Instructions, x.BuiltIn))
            .ToList(),
        ScratchRaw = ScratchRaw,
        ScratchPolished = ScratchPolished
    };

    private void EnsureProtectedDictionaryEntries()
    {
        var protectedDefaults = new[]
        {
            new DictionaryEntryRow("FlowerWhisp", "FlowerWhisp", true),
            new DictionaryEntryRow("Signal Ledger", "Signal Ledger", true)
        };
        foreach (var entry in protectedDefaults.Where(defaultEntry =>
                     !DictionaryEntries.Any(existing => existing.Phrase.Equals(defaultEntry.Phrase, StringComparison.OrdinalIgnoreCase))))
            DictionaryEntries.Add(entry);
    }
}

public sealed record LedgerDictation(Guid Id, string Title, string RawText, string FinalText, string Timestamp, string Backend, string Polish, string Duration);
public sealed record DictionaryEntryRow(string Phrase, string Replacement, bool Protected);
public sealed record SnippetRow(string Name, string Content, string Shortcut);
public sealed record StyleRow(string Name, string Instructions, bool BuiltIn);
public sealed record TransformRow(string Name, string Description, bool Enabled);
public sealed record HeatmapDay(string Day, string Count, double Intensity);
