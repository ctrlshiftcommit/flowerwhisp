using System.Buffers.Binary;
using FlowerWhisp.Platform.Windows;
using NAudio.Wave;

namespace FlowerWhisp.Tests;

public sealed class PlatformTests
{
    [Fact]
    public void Pcm16Mono16KHz_DecodesIeeeFloatFormat()
    {
        var format = WaveFormat.CreateIeeeFloatWaveFormat(16_000, 1);
        var source = Floats(0.5f, -0.5f);

        var payload = WasapiAudioCaptureService.Pcm16Mono16KHz(source, format);

        Assert.Equal(16_000, payload.SampleRate);
        Assert.Equal(1, payload.Channels);
        Assert.Equal(2, payload.Pcm16.Length / 2);
        Assert.Equal(16_384, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(0, 2)));
        Assert.Equal(-16_384, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(2, 2)));
    }

    [Fact]
    public void Pcm16Mono16KHz_DecodesExtensibleIeeeFloatSubtype()
    {
        var format = new WaveFormatExtensible(16_000, 32, 1);
        var source = Floats(0.5f, -0.5f);

        var payload = WasapiAudioCaptureService.Pcm16Mono16KHz(source, format);

        Assert.Equal(16_384, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(0, 2)));
        Assert.Equal(-16_384, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(2, 2)));
    }

    [Fact]
    public void Pcm16Mono16KHz_DecodesExtensiblePcmSubtypeAndDownmixes()
    {
        var format = new WaveFormatExtensible(16_000, 16, 2);
        var source = Int16s(16_384, -16_384, 32_767, 0);

        var payload = WasapiAudioCaptureService.Pcm16Mono16KHz(source, format);

        Assert.Equal(0, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(0, 2)));
        Assert.Equal(16_383, BinaryPrimitives.ReadInt16LittleEndian(payload.Pcm16.AsSpan(2, 2)));
    }

    [Fact]
    public async Task DpapiSecretStore_RejectsPathLikeKeys()
    {
        var store = new DpapiSecretStore(Path.Combine(Path.GetTempPath(), "FlowerWhispTests"));

        await Assert.ThrowsAsync<ArgumentException>(() => store.SetAsync("../outside", "secret"));
        await Assert.ThrowsAsync<ArgumentException>(() => store.DeleteAsync("nested/name"));
    }

    private static byte[] Floats(params float[] samples)
    {
        var bytes = new byte[samples.Length * sizeof(float)];
        for (var i = 0; i < samples.Length; i++)
            Buffer.BlockCopy(BitConverter.GetBytes(samples[i]), 0, bytes, i * sizeof(float), sizeof(float));
        return bytes;
    }

    private static byte[] Int16s(params short[] samples)
    {
        var bytes = new byte[samples.Length * sizeof(short)];
        for (var i = 0; i < samples.Length; i++)
            BinaryPrimitives.WriteInt16LittleEndian(bytes.AsSpan(i * sizeof(short), sizeof(short)), samples[i]);
        return bytes;
    }
}
