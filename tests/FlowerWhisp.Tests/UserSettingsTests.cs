using FlowerWhisp.Infrastructure;

namespace FlowerWhisp.Tests;

public sealed class UserSettingsTests
{
    [Fact]
    public async Task User_settings_round_trip_without_secret_fields()
    {
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        var path = Path.Combine(directory, "settings.json");
        try
        {
            var store = new UserSettingsStore(path);
            await store.SaveAsync(new FlowerWhispSettings(
                WhisperCheckout: Path.Combine(directory, "checkout"),
                WhisperPython: "python.exe",
                WhisperModel: "small",
                WhisperDevice: "cuda"));

            var loaded = await store.LoadAsync();
            Assert.Equal(Path.Combine(directory, "checkout"), loaded.WhisperCheckout);
            Assert.Equal("python.exe", loaded.WhisperPython);
            Assert.Equal("small", loaded.WhisperModel);
            Assert.DoesNotContain("api", await File.ReadAllTextAsync(path), StringComparison.OrdinalIgnoreCase);
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }

    [Fact]
    public async Task Signal_ledger_content_round_trips_locally()
    {
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        var path = Path.Combine(directory, "signal-ledger.json");
        try
        {
            var store = new SignalLedgerStore(path);
            await store.SaveAsync(new SignalLedgerContent
            {
                Dictionary = [new LocalDictionaryEntry("FlowerWhisp", "FlowerWhisp", true)],
                Snippets = [new LocalSnippet("Follow up", "Next step: {date}.")],
                Styles = [new LocalStyle("Clean notes", "Plain punctuation.", true)],
                ScratchRaw = "raw",
                ScratchPolished = "polished"
            });

            var loaded = await store.LoadAsync();
            Assert.NotNull(loaded);
            Assert.Single(loaded!.Dictionary);
            Assert.Equal("Follow up", loaded.Snippets[0].Name);
            Assert.Equal("Clean notes", loaded.Styles[0].Name);
            Assert.Equal("raw", loaded.ScratchRaw);
            Assert.Equal("polished", loaded.ScratchPolished);
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }
}
