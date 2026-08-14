using System.Globalization;
using System.Net;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.RegularExpressions;
using FlowerWhisp.Core;

namespace FlowerWhisp.Infrastructure;

/// <summary>
/// A rate-limit response from Groq.  The exception deliberately contains only
/// response metadata; request bodies (which can contain audio or transcript
/// text) are never copied into diagnostics.
/// </summary>
public sealed class GroqRateLimitException : HttpRequestException
{
    public GroqRateLimitException(
        TimeSpan? retryAfter,
        string? resetRequests,
        string? resetTokens)
        : base(BuildMessage(retryAfter, resetRequests, resetTokens), null, HttpStatusCode.TooManyRequests)
    {
        RetryAfter = retryAfter;
        ResetRequests = resetRequests;
        ResetTokens = resetTokens;
    }

    public TimeSpan? RetryAfter { get; }
    public string? ResetRequests { get; }
    public string? ResetTokens { get; }

    private static string BuildMessage(TimeSpan? retryAfter, string? resetRequests, string? resetTokens)
    {
        var parts = new List<string>();
        if (retryAfter is { } delay) parts.Add($"retry after {FormatDuration(delay)}");
        if (!string.IsNullOrWhiteSpace(resetRequests)) parts.Add($"request window resets in {resetRequests}");
        if (!string.IsNullOrWhiteSpace(resetTokens)) parts.Add($"token window resets in {resetTokens}");
        return parts.Count == 0
            ? "Groq rate limit reached (HTTP 429)."
            : $"Groq rate limit reached (HTTP 429; {string.Join("; ", parts)}).";
    }

    private static string FormatDuration(TimeSpan duration) =>
        duration.TotalSeconds >= 1
            ? $"{Math.Ceiling(duration.TotalSeconds):0}s"
            : $"{Math.Max(1, duration.TotalMilliseconds):0}ms";
}

/// <summary>Shared, authenticated Groq HTTP behavior for every endpoint.</summary>
internal static class GroqHttp
{
    public static async Task<HttpResponseMessage> SendAsync(
        HttpClient http,
        HttpRequestMessage request,
        CancellationToken cancellationToken)
    {
        var response = await http.SendAsync(
            request,
            HttpCompletionOption.ResponseHeadersRead,
            cancellationToken).ConfigureAwait(false);

        if (response.StatusCode == HttpStatusCode.TooManyRequests)
        {
            var exception = new GroqRateLimitException(
                ParseRetryAfter(response),
                GetHeader(response, "x-ratelimit-reset-requests"),
                GetHeader(response, "x-ratelimit-reset-tokens"));
            response.Dispose();
            throw exception;
        }

        if (!response.IsSuccessStatusCode)
        {
            var status = response.StatusCode;
            var reason = response.ReasonPhrase;
            response.Dispose();
            throw new HttpRequestException(
                $"Groq request failed with HTTP {(int)status}{(string.IsNullOrWhiteSpace(reason) ? string.Empty : $" ({reason})") }.",
                null,
                status);
        }

        return response;
    }

    private static string? GetHeader(HttpResponseMessage response, string name) =>
        response.Headers.TryGetValues(name, out var values) ? values.FirstOrDefault() : null;

    private static TimeSpan? ParseRetryAfter(HttpResponseMessage response)
    {
        if (response.Headers.RetryAfter?.Delta is { } delta && delta >= TimeSpan.Zero) return delta;
        if (response.Headers.RetryAfter?.Date is { } date)
        {
            var delay = date - DateTimeOffset.UtcNow;
            return delay >= TimeSpan.Zero ? delay : TimeSpan.Zero;
        }

        var raw = GetHeader(response, "retry-after");
        return int.TryParse(raw, NumberStyles.Integer, CultureInfo.InvariantCulture, out var seconds) && seconds >= 0
            ? TimeSpan.FromSeconds(seconds)
            : null;
    }
}

public sealed class GroqTranscriptionProvider : ITranscriptionProvider
{
    private const int FreeTierMaxBytes = 25 * 1024 * 1024;
    private static readonly Regex LanguageCode = new("^[A-Za-z]{2,3}(?:-[A-Za-z]{2,4})?$", RegexOptions.CultureInvariant);
    private readonly HttpClient _http;
    private readonly string _apiKey;
    private readonly string _model;

    public GroqTranscriptionProvider(
        HttpClient http,
        string apiKey,
        string model = "whisper-large-v3-turbo")
    {
        _http = http ?? throw new ArgumentNullException(nameof(http));
        _apiKey = string.IsNullOrWhiteSpace(apiKey) ? throw new ArgumentException("An API key is required.", nameof(apiKey)) : apiKey;
        _model = RequireAllowlistedModel(model, ProviderPolicy.GroqTranscriptionAllowlist, nameof(model));
    }

    public TranscriptionBackend Backend => TranscriptionBackend.Groq;
    public string Model => _model;

    public async Task<TranscriptionResult> TranscribeAsync(
        TranscriptionRequest request,
        CancellationToken cancellationToken = default)
    {
        var wav = WavEncoder.Encode(request.Audio);
        if (wav.Length > FreeTierMaxBytes)
            throw new InvalidOperationException("Groq audio requests must be smaller than 25 MB on the free tier.");

        using var content = new MultipartFormDataContent();
        content.Add(new ByteArrayContent(wav), "file", "audio.wav");
        content.Add(new StringContent(_model), "model");
        content.Add(new StringContent("json"), "response_format");
        var language = NormalizeLanguage(request.Language);
        if (language is not null) content.Add(new StringContent(language), "language");

        using var message = new HttpRequestMessage(
            HttpMethod.Post,
            "https://api.groq.com/openai/v1/audio/transcriptions")
        { Content = content };
        message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);

        using var response = await GroqHttp.SendAsync(_http, message, cancellationToken).ConfigureAwait(false);
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
        var text = document.RootElement.TryGetProperty("text", out var textValue)
            ? textValue.GetString() ?? string.Empty
            : throw new InvalidOperationException("Groq transcription response did not contain text.");
        var detectedLanguage = document.RootElement.TryGetProperty("language", out var languageValue)
            ? languageValue.GetString()
            : null;
        return new TranscriptionResult(request.RequestId, text, detectedLanguage);
    }

    /// <summary>Returns only authenticated models that are approved for Groq STT.</summary>
    public async Task<IReadOnlyList<ProviderModel>> GetAvailableAllowedModelsAsync(
        CancellationToken cancellationToken = default)
    {
        var models = await GroqModelCatalog.GetAuthenticatedModelsAsync(_http, _apiKey, cancellationToken).ConfigureAwait(false);
        return models
            .Where(model => ProviderPolicy.GroqTranscriptionAllowlist.Contains(model.Id, StringComparer.OrdinalIgnoreCase))
            .Select(model => model with { SupportsAudio = true })
            .ToArray();
    }

    internal static string? NormalizeLanguage(string? language)
    {
        if (string.IsNullOrWhiteSpace(language) || language.Trim().Equals("auto", StringComparison.OrdinalIgnoreCase)) return null;
        var normalized = language.Trim();
        if (!LanguageCode.IsMatch(normalized))
            throw new ArgumentException("Groq language must be an ISO-639-1/3 code such as 'en' or 'pt-BR'.", nameof(language));
        return normalized.ToLowerInvariant();
    }

    internal static string RequireAllowlistedModel(string model, IEnumerable<string> allowlist, string parameterName)
    {
        if (string.IsNullOrWhiteSpace(model) || !allowlist.Contains(model, StringComparer.OrdinalIgnoreCase))
            throw new ArgumentException("Model is not on the approved Groq production allowlist.", parameterName);
        return model;
    }
}

public static class WavEncoder
{
    public static byte[] Encode(AudioPayload audio)
    {
        using var stream = new MemoryStream();
        using var writer = new BinaryWriter(stream, Encoding.UTF8, leaveOpen: true);
        var dataLength = audio.Pcm16.Length;
        writer.Write(Encoding.ASCII.GetBytes("RIFF"));
        writer.Write(36 + dataLength);
        writer.Write(Encoding.ASCII.GetBytes("WAVEfmt "));
        writer.Write(16);
        writer.Write((short)1);
        writer.Write((short)audio.Channels);
        writer.Write(audio.SampleRate);
        writer.Write(audio.SampleRate * audio.Channels * 2);
        writer.Write((short)(audio.Channels * 2));
        writer.Write((short)16);
        writer.Write(Encoding.ASCII.GetBytes("data"));
        writer.Write(dataLength);
        writer.Write(audio.Pcm16);
        writer.Flush();
        return stream.ToArray();
    }
}

public sealed class GroqPolishProvider : IPolishProvider
{
    private readonly HttpClient _http;
    private readonly string _apiKey;
    private readonly string _model;

    public GroqPolishProvider(HttpClient http, string apiKey, string model = "openai/gpt-oss-20b")
    {
        _http = http ?? throw new ArgumentNullException(nameof(http));
        _apiKey = string.IsNullOrWhiteSpace(apiKey) ? throw new ArgumentException("An API key is required.", nameof(apiKey)) : apiKey;
        _model = GroqTranscriptionProvider.RequireAllowlistedModel(model, ProviderPolicy.GroqPolishAllowlist, nameof(model));
    }

    public string Model => _model;

    public async Task<PolishResult> PolishAsync(
        PolishRequest request,
        CancellationToken cancellationToken = default)
    {
        var payload = JsonSerializer.Serialize(new
        {
            model = _model,
            messages = new[]
            {
                new { role = "system", content = "Clean up grammar while preserving meaning. Return only the cleaned text." },
                new { role = "user", content = request.RawText }
            }
        });
        using var message = new HttpRequestMessage(
            HttpMethod.Post,
            "https://api.groq.com/openai/v1/chat/completions")
        {
            Content = new StringContent(payload, Encoding.UTF8, "application/json")
        };
        message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", _apiKey);

        using var response = await GroqHttp.SendAsync(_http, message, cancellationToken).ConfigureAwait(false);
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
        var text = document.RootElement.GetProperty("choices")[0].GetProperty("message").GetProperty("content").GetString() ?? request.RawText;
        return new PolishResult(request.RequestId, text, !text.Equals(request.RawText, StringComparison.Ordinal), _model);
    }

    public Task<IReadOnlyList<ProviderModel>> GetAvailableModelsAsync(CancellationToken cancellationToken = default) =>
        GroqModelCatalog.GetAuthenticatedModelsAsync(_http, _apiKey, cancellationToken);

    /// <summary>Intersects the authenticated models with the approved polish allowlist.</summary>
    public async Task<IReadOnlyList<ProviderModel>> GetAvailableAllowedModelsAsync(
        CancellationToken cancellationToken = default)
    {
        var models = await GetAvailableModelsAsync(cancellationToken).ConfigureAwait(false);
        return models
            .Where(model => ProviderPolicy.GroqPolishAllowlist.Contains(model.Id, StringComparer.OrdinalIgnoreCase))
            .ToArray();
    }
}

internal static class GroqModelCatalog
{
    public static async Task<IReadOnlyList<ProviderModel>> GetAuthenticatedModelsAsync(
        HttpClient http,
        string apiKey,
        CancellationToken cancellationToken)
    {
        using var message = new HttpRequestMessage(HttpMethod.Get, "https://api.groq.com/openai/v1/models");
        message.Headers.Authorization = new AuthenticationHeaderValue("Bearer", apiKey);
        using var response = await GroqHttp.SendAsync(http, message, cancellationToken).ConfigureAwait(false);
        using var document = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false));
        if (!document.RootElement.TryGetProperty("data", out var data) || data.ValueKind != JsonValueKind.Array)
            throw new InvalidOperationException("Groq models response did not contain a data array.");
        return data.EnumerateArray()
            .Where(item => item.TryGetProperty("id", out var id) && id.ValueKind == JsonValueKind.String)
            .Select(item => new ProviderModel(item.GetProperty("id").GetString() ?? string.Empty))
            .Where(model => !string.IsNullOrWhiteSpace(model.Id))
            .ToArray();
    }
}
