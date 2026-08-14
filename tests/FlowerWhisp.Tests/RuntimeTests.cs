using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using FlowerWhisp.Core;
using FlowerWhisp.Infrastructure;

namespace FlowerWhisp.Tests;

public sealed class RuntimeTests
{
    [Fact]
    public void WavEncoder_writes_pcm16_mono_header_and_data_size()
    {
        var bytes = WavEncoder.Encode(new AudioPayload([1, 2, 3, 4], 16_000, 1));
        Assert.Equal(48, bytes.Length);
        Assert.Equal("RIFF", Encoding.ASCII.GetString(bytes, 0, 4));
        Assert.Equal("WAVE", Encoding.ASCII.GetString(bytes, 8, 4));
        Assert.Equal("fmt ", Encoding.ASCII.GetString(bytes, 12, 4));
        Assert.Equal("data", Encoding.ASCII.GetString(bytes, 36, 4));
        Assert.Equal(4, BitConverter.ToInt32(bytes, 40));
        Assert.Equal((short)1, BitConverter.ToInt16(bytes, 22));
        Assert.Equal((short)16, BitConverter.ToInt16(bytes, 34));
    }

    [Fact]
    public async Task Groq_transcription_rate_limit_preserves_429_status()
    {
        using var client = new HttpClient(new StubHandler(_ =>
        {
            var response = new HttpResponseMessage(HttpStatusCode.TooManyRequests);
            response.Headers.RetryAfter = new RetryConditionHeaderValue(TimeSpan.FromSeconds(3));
            response.Headers.TryAddWithoutValidation("x-ratelimit-reset-requests", "2m59.56s");
            return response;
        }));
        var provider = new GroqTranscriptionProvider(client, "test-key");
        var rateLimit = await Assert.ThrowsAsync<GroqRateLimitException>(() => provider.TranscribeAsync(new TranscriptionRequest(Guid.NewGuid(), new AudioPayload([0, 0]))));
        Assert.Equal((HttpStatusCode)429, rateLimit.StatusCode);
        Assert.Equal(TimeSpan.FromSeconds(3), rateLimit.RetryAfter);
        Assert.Contains("request window resets", rateLimit.Message);
        Assert.DoesNotContain("audio", rateLimit.Message, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Groq_transcription_rejects_unapproved_model_without_substitution()
    {
        using var client = new HttpClient(new StubHandler(_ => new HttpResponseMessage(HttpStatusCode.OK)));
        Assert.Throws<ArgumentException>(() => new GroqTranscriptionProvider(client, "test-key", "distil-whisper-large-v3-en"));
    }

    [Fact]
    public async Task Sqlite_repositories_migrate_upsert_and_apply_retention()
    {
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        var path = Path.Combine(directory, "flowerwhisp.db");
        try
        {
            var dictations = new SqliteDictationRepository(path);
            var old = new DictationRecord(Guid.NewGuid(), DateTimeOffset.UtcNow.AddHours(-25), "old", "old", TranscriptionBackend.LocalWhisper, PolishMode.Off, RetentionPolicy.DeleteAfter24Hours, TimeSpan.FromSeconds(2));
            var fresh = old with { Id = Guid.NewGuid(), CreatedAt = DateTimeOffset.UtcNow, RawText = "fresh" };
            await dictations.SaveAsync(old);
            await dictations.SaveAsync(fresh);
            await dictations.SaveAsync(fresh with { FinalText = "updated" });
            Assert.Equal(2, (await dictations.ListAsync()).Count);
            Assert.Equal(1, await new RetentionService(dictations).ApplyAsync(RetentionPolicy.DeleteAfter24Hours));
            var remaining = await dictations.ListAsync();
            Assert.Single(remaining);
            Assert.Equal("updated", remaining[0].FinalText);

            var usage = new SqliteUsageAggregateRepository(path);
            var day = DateOnly.FromDateTime(DateTime.UtcNow);
            await usage.RecordAsync(new UsageAggregate(day, 1, 2, 3));
            await usage.RecordAsync(new UsageAggregate(day, 2, 4, 5));
            var aggregate = Assert.Single(await usage.ListAsync());
            Assert.Equal(3, aggregate.DictationCount);
            Assert.Equal(6, aggregate.AudioSeconds);
            Assert.Equal(8, aggregate.CharacterCount);
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }

    [Fact]
    public async Task Local_host_returns_structured_transcript_from_configured_checkout_and_loads_once()
    {
        var python = FindPython();
        var hostScript = FindRepoFile(Path.Combine("tools", "local-whisper-host", "host.py"));
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        var checkout = Path.Combine(directory, "checkout");
        Directory.CreateDirectory(Path.Combine(checkout, "whisper"));
        var module = """
            from pathlib import Path
            def load_model(name, **kwargs):
                marker = Path(__file__).with_name('load-count')
                count = int(marker.read_text()) if marker.exists() else 0
                marker.write_text(str(count + 1))
                class Model:
                    def transcribe(self, audio, **kwargs):
                        return {'text': 'configured transcript', 'language': 'en', 'duration': 0.25,
                                'segments': [{'start': 0.0, 'end': 0.25, 'text': 'configured transcript'}]}
                return Model()
            """;
        await File.WriteAllTextAsync(Path.Combine(checkout, "whisper", "__init__.py"), module);
        try
        {
            await using var host = new ProcessLocalWhisperHost(python, hostScript, "small", checkout, directory, "cpu");
            var handshake = await host.StartAsync();
            Assert.True(handshake.Ready);
            Assert.Equal("small", handshake.Model);
            var request = new TranscriptionRequest(Guid.NewGuid(), new AudioPayload(new byte[32], 16_000, 1), "auto");
            var result = await host.TranscribeAsync(request);
            Assert.Equal("configured transcript", result.Text);
            Assert.Equal("en", result.DetectedLanguage);
            Assert.Equal(TimeSpan.FromSeconds(.25), result.Duration);
            Assert.Single(result.Segments);
            _ = await host.TranscribeAsync(request with { RequestId = Guid.NewGuid() });
            Assert.Equal("1", await File.ReadAllTextAsync(Path.Combine(checkout, "whisper", "load-count")));
            await host.ShutdownAsync();
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }

    [Fact]
    public async Task Local_host_rejects_malformed_protocol_response()
    {
        var python = FindPython();
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        var script = Path.Combine(directory, "malformed.py");
        await File.WriteAllTextAsync(script, "import sys\nfor _ in sys.stdin:\n print('not-json', flush=True)\n");
        try
        {
            await using var host = new ProcessLocalWhisperHost(python, script);
            await Assert.ThrowsAsync<LocalWhisperProtocolException>(() => host.StartAsync());
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }

    [Fact]
    public async Task Local_host_bounds_startup_and_retains_only_safe_stderr_diagnostics()
    {
        var python = FindPython();
        var directory = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        var script = Path.Combine(directory, "blocked.py");
        await File.WriteAllTextAsync(script, "import sys, time\nsys.stderr.write('warning: transcript should not be retained\\n'); sys.stderr.flush()\ntime.sleep(5)\n");
        try
        {
            await using var host = new ProcessLocalWhisperHost(
                python,
                script,
                startupTimeout: TimeSpan.FromMilliseconds(250),
                readTimeout: TimeSpan.FromMilliseconds(250));
            var exception = await Assert.ThrowsAsync<TimeoutException>(() => host.StartAsync());
            Assert.Contains("timed out", exception.Message, StringComparison.OrdinalIgnoreCase);
            Assert.Contains("category=warning", exception.Message, StringComparison.OrdinalIgnoreCase);
            Assert.DoesNotContain("transcript should not be retained", exception.Message, StringComparison.OrdinalIgnoreCase);
            Assert.NotNull(host.LastStderrDiagnostic);
            Assert.Equal("warning", host.LastStderrDiagnostic!.LastCategory);
        }
        finally { try { Directory.Delete(directory, recursive: true); } catch { } }
    }

    private static string FindPython()
    {
        var candidate = Environment.GetEnvironmentVariable("FLOWERWHISP_PYTHON");
        if (!string.IsNullOrWhiteSpace(candidate) && File.Exists(candidate)) return candidate;
        var configured = Path.Combine("D:\\Github", "openai whisper Transcriber", ".venv", "Scripts", "python.exe");
        if (File.Exists(configured)) return configured;
        return "python";
    }

    private static string FindRepoFile(string relative)
    {
        var current = new DirectoryInfo(Directory.GetCurrentDirectory());
        while (current is not null)
        {
            var path = Path.Combine(current.FullName, relative);
            if (File.Exists(path)) return path;
            current = current.Parent;
        }
        throw new FileNotFoundException(relative);
    }

    private sealed class StubHandler(Func<HttpRequestMessage, HttpResponseMessage> handler) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken) => Task.FromResult(handler(request));
    }
}
