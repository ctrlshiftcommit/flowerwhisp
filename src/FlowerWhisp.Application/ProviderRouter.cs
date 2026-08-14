using FlowerWhisp.Core;

namespace FlowerWhisp.Application;

public sealed class ProviderRouter
{
    private readonly IReadOnlyDictionary<TranscriptionBackend, ITranscriptionProvider> _providers;
    public ProviderRouter(IEnumerable<ITranscriptionProvider> providers) => _providers = providers.ToDictionary(x => x.Backend);
    public Task<TranscriptionResult> TranscribeAsync(TranscriptionBackend backend, TranscriptionRequest request, CancellationToken cancellationToken = default) => _providers.TryGetValue(backend, out var provider) ? provider.TranscribeAsync(request, cancellationToken) : throw new InvalidOperationException($"No transcription provider registered for {backend}.");
}
