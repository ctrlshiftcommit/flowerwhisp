using System.Runtime.InteropServices;
using System.Runtime.CompilerServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.Unicode;
using System.Diagnostics;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using FlowerWhisp.Core;

[assembly: InternalsVisibleTo("FlowerWhisp.Tests")]

namespace FlowerWhisp.Platform.Windows;

[Obsolete("Use WasapiAudioCaptureService for real microphone capture.")]
public sealed class NullAudioCaptureService : IAudioCaptureService
{
    private readonly MemoryStream _buffer = new(); public bool IsRecording { get; private set; }
    public Task StartAsync(CancellationToken cancellationToken = default) { IsRecording = true; _buffer.SetLength(0); return Task.CompletedTask; }
    public Task<AudioPayload> StopAsync(CancellationToken cancellationToken = default) { IsRecording = false; return Task.FromResult(new AudioPayload(_buffer.ToArray())); }
    public Task CancelAsync(CancellationToken cancellationToken = default) { IsRecording = false; _buffer.SetLength(0); return Task.CompletedTask; }
    public ValueTask DisposeAsync() { _buffer.Dispose(); return ValueTask.CompletedTask; }
}

/// <summary>
/// Captures the default Windows microphone through shared-mode WASAPI and emits
/// the format expected by Whisper (16-bit, 16 kHz, mono).  Windows devices often
/// expose 44.1/48 kHz stereo or float samples, so conversion is done after the
/// capture has stopped instead of assuming the endpoint can negotiate 16 kHz.
/// </summary>
public sealed class WasapiAudioCaptureService : IAudioCaptureService
{
    private readonly object _sync = new();
    private readonly MMDevice? _configuredDevice;
    private WasapiCapture? _capture;
    private MemoryStream? _buffer;
    private TaskCompletionSource<object?>? _stopped;
    private bool _disposed;

    public WasapiAudioCaptureService(MMDevice? device = null) => _configuredDevice = device;
    public bool IsRecording { get; private set; }

    public Task StartAsync(CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        lock (_sync)
        {
            ObjectDisposedException.ThrowIf(_disposed, this);
            if (IsRecording) throw new InvalidOperationException("Audio capture is already running.");
            var device = _configuredDevice ?? new MMDeviceEnumerator().GetDefaultAudioEndpoint(DataFlow.Capture, Role.Multimedia);
            var capture = new WasapiCapture(device) { ShareMode = AudioClientShareMode.Shared };
            _buffer = new MemoryStream(capacity: Math.Max(16_000, capture.WaveFormat.AverageBytesPerSecond));
            _stopped = new TaskCompletionSource<object?>(TaskCreationOptions.RunContinuationsAsynchronously);
            capture.DataAvailable += OnDataAvailable;
            capture.RecordingStopped += OnRecordingStopped;
            _capture = capture;
            try
            {
                capture.StartRecording();
                IsRecording = true;
            }
            catch
            {
                capture.DataAvailable -= OnDataAvailable;
                capture.RecordingStopped -= OnRecordingStopped;
                capture.Dispose();
                _capture = null;
                _buffer?.Dispose();
                _buffer = null;
                _stopped = null;
                throw;
            }
        }
        return Task.CompletedTask;
    }

    public async Task<AudioPayload> StopAsync(CancellationToken cancellationToken = default)
    {
        WasapiCapture? capture;
        Task stopped;
        lock (_sync)
        {
            if (!IsRecording || _capture is null || _buffer is null) return new AudioPayload([]);
            capture = _capture;
            stopped = _stopped?.Task ?? Task.CompletedTask;
            IsRecording = false;
        }
        try { capture.StopRecording(); }
        catch { _stopped?.TrySetResult(null); throw; }
        await stopped.WaitAsync(cancellationToken).ConfigureAwait(false);
        lock (_sync)
        {
            var bytes = _buffer?.ToArray() ?? [];
            var format = capture.WaveFormat;
            CleanupCapture(capture);
            return Pcm16Mono16KHz(bytes, format);
        }
    }

    public async Task CancelAsync(CancellationToken cancellationToken = default)
    {
        WasapiCapture? capture;
        Task stopped;
        lock (_sync)
        {
            if (!IsRecording || _capture is null)
            {
                _buffer?.SetLength(0);
                return;
            }
            capture = _capture;
            stopped = _stopped?.Task ?? Task.CompletedTask;
            IsRecording = false;
        }
        try { capture.StopRecording(); }
        catch { _stopped?.TrySetResult(null); }
        await stopped.WaitAsync(cancellationToken).ConfigureAwait(false);
        lock (_sync) CleanupCapture(capture);
    }

    public async ValueTask DisposeAsync()
    {
        WasapiCapture? capture;
        lock (_sync)
        {
            if (_disposed) return;
            _disposed = true;
            capture = _capture;
            IsRecording = false;
        }
        if (capture is not null)
        {
            try { capture.StopRecording(); } catch { }
            _stopped?.TrySetResult(null);
            try { await (_stopped?.Task ?? Task.CompletedTask).ConfigureAwait(false); } catch { }
            lock (_sync) CleanupCapture(capture);
        }
        _configuredDevice?.Dispose();
    }

    private void OnDataAvailable(object? sender, WaveInEventArgs e)
    {
        lock (_sync)
        {
            if (IsRecording) _buffer?.Write(e.Buffer, 0, e.BytesRecorded);
        }
    }

    private void OnRecordingStopped(object? sender, StoppedEventArgs e) => _stopped?.TrySetResult(null);

    private void CleanupCapture(WasapiCapture capture)
    {
        capture.DataAvailable -= OnDataAvailable;
        capture.RecordingStopped -= OnRecordingStopped;
        capture.Dispose();
        _capture = null;
        _stopped = null;
        _buffer?.Dispose();
        _buffer = null;
    }

    internal static AudioPayload Pcm16Mono16KHz(byte[] source, WaveFormat format)
    {
        var channels = Math.Max(1, format.Channels);
        var bytesPerSample = Math.Max(1, format.BitsPerSample / 8);
        var sourceFrameBytes = channels * bytesPerSample;
        if (source.Length == 0 || sourceFrameBytes <= 0 || format.SampleRate <= 0)
            return new AudioPayload([]);
        var frames = source.Length / sourceFrameBytes;
        var outputFrames = Math.Max(1, (int)Math.Round(frames * 16_000d / format.SampleRate, MidpointRounding.ToEven));
        var output = new byte[outputFrames * 2];
        for (var i = 0; i < outputFrames; i++)
        {
            var sourcePosition = i * format.SampleRate / 16_000d;
            var first = Math.Min(frames - 1, (int)Math.Floor(sourcePosition));
            var second = Math.Min(frames - 1, first + 1);
            var fraction = sourcePosition - first;
            var firstValue = Downmix(source, first, channels, bytesPerSample, format);
            var secondValue = Downmix(source, second, channels, bytesPerSample, format);
            var sample = Math.Clamp(firstValue + (secondValue - firstValue) * fraction, -1d, 1d);
            var pcm = (short)Math.Round(sample * short.MaxValue, MidpointRounding.AwayFromZero);
            output[i * 2] = (byte)(pcm & 0xff);
            output[i * 2 + 1] = (byte)((pcm >> 8) & 0xff);
        }
        return new AudioPayload(output, 16_000, 1);
    }

    private static double Downmix(byte[] source, int frame, int channels, int bytesPerSample, WaveFormat format)
    {
        var offset = frame * channels * bytesPerSample;
        var sum = 0d;
        for (var channel = 0; channel < channels; channel++)
            sum += ReadSample(source, offset + channel * bytesPerSample, bytesPerSample, format);
        return sum / channels;
    }

    private static double ReadSample(byte[] source, int offset, int bytesPerSample, WaveFormat format)
    {
        if (offset < 0 || offset + bytesPerSample > source.Length) return 0;
        // WASAPI commonly reports WAVE_FORMAT_EXTENSIBLE. Its Encoding value only
        // says "Extensible"; the SubFormat GUID carries the PCM/IEEE-float type.
        var isFloat = format.Encoding == WaveFormatEncoding.IeeeFloat
            || format is WaveFormatExtensible extensible && extensible.SubFormat == IeeeFloatSubType;
        var isPcm = format.Encoding == WaveFormatEncoding.Pcm
            || format is WaveFormatExtensible pcmExtensible && pcmExtensible.SubFormat == PcmSubType;
        if (isFloat && bytesPerSample >= sizeof(float))
        {
            var value = BitConverter.ToSingle(source, offset);
            return float.IsFinite(value) ? value : 0;
        }
        if (!isPcm) return 0;
        return bytesPerSample switch
        {
            1 => (source[offset] - 128) / 128d,
            2 => BitConverter.ToInt16(source, offset) / 32768d,
            3 => (((source[offset + 2] & 0x80) != 0 ? unchecked((int)0xff000000) : 0) |
                  (source[offset] | source[offset + 1] << 8 | source[offset + 2] << 16)) / 8388608d,
            4 => BitConverter.ToInt32(source, offset) / 2147483648d,
            _ => 0
        };
    }

    private static readonly Guid PcmSubType = new("00000001-0000-0010-8000-00aa00389b71");
    private static readonly Guid IeeeFloatSubType = new("00000003-0000-0010-8000-00aa00389b71");
}

public sealed class ForegroundTargetService : IForegroundTargetService
{
    public TargetSnapshot Capture()
    {
        var hwnd = GetForegroundWindow();
        GetWindowThreadProcessId(hwnd, out var pid);
        string? processName = null;
        if (pid != 0)
        {
            try { processName = Process.GetProcessById((int)pid).ProcessName; } catch { }
        }
        return new TargetSnapshot(hwnd, pid, processName, DateTimeOffset.UtcNow);
    }

    public bool HasChanged(TargetSnapshot target)
    {
        var hwnd = GetForegroundWindow();
        if (hwnd != target.WindowHandle) return true;
        GetWindowThreadProcessId(hwnd, out var pid);
        return pid != target.ProcessId;
    }

    public bool IsElevated(TargetSnapshot target)
    {
        if (target.ProcessId == 0) return false;
        var process = OpenProcess(ProcessQueryLimitedInformation, false, target.ProcessId);
        if (process == 0) return false;
        try
        {
            if (!OpenProcessToken(process, TokenQuery, out var token)) return false;
            try
            {
                var elevation = new TokenElevation();
                var size = Marshal.SizeOf<TokenElevation>();
                return GetTokenInformation(token, TokenInformationClassElevation, ref elevation, size, out _)
                    && elevation.TokenIsElevated != 0;
            }
            finally { CloseHandle(token); }
        }
        finally { CloseHandle(process); }
    }

    private const uint ProcessQueryLimitedInformation = 0x1000;
    private const uint TokenQuery = 0x0008;
    private const int TokenInformationClassElevation = 20;
    [StructLayout(LayoutKind.Sequential)] private struct TokenElevation { public uint TokenIsElevated; }
    [DllImport("user32.dll")] private static extern nint GetForegroundWindow();
    [DllImport("user32.dll")] private static extern uint GetWindowThreadProcessId(nint hWnd, out uint processId);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern nint OpenProcess(uint access, bool inheritHandle, uint processId);
    [DllImport("advapi32.dll", SetLastError = true)] private static extern bool OpenProcessToken(nint processHandle, uint desiredAccess, out nint tokenHandle);
    [DllImport("advapi32.dll", SetLastError = true)] private static extern bool GetTokenInformation(nint tokenHandle, int tokenInformationClass, ref TokenElevation tokenInformation, int tokenInformationLength, out int returnLength);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern bool CloseHandle(nint handle);
}

/// <summary>Dedicated message-pump WH_KEYBOARD_LL hook for modifier-only gestures.</summary>
public sealed class LowLevelKeyboardHook : IDisposable
{
    private const int WhKeyboardLl = 13; private const int WmKeyDown = 0x0100; private const int WmKeyUp = 0x0101; private const int WmSysKeyDown = 0x0104; private const int WmSysKeyUp = 0x0105; private const int WmQuit = 0x0012; private readonly Thread _thread; private readonly AutoResetEvent _ready = new(false); private nint _hook; private uint _threadId; private readonly HookProc _callback; private readonly HashSet<uint> _pressed = [];
    public bool IsInstalled => _hook != 0;
    public event EventHandler<KeyboardHookEventArgs>? KeyEvent;
    public LowLevelKeyboardHook() { _callback = Callback; _thread = new Thread(Pump) { IsBackground = true, Name = "FlowerWhisp.KeyboardHook" }; _thread.Start(); _ready.WaitOne(); }
    private void Pump()
    {
        _threadId = GetCurrentThreadId();
        // Force creation of this thread's message queue before signalling readiness;
        // otherwise a very early WM_QUIT can be lost and Dispose would hang.
        PeekMessage(out _, 0, 0, 0, 0);
        _hook = SetWindowsHookEx(WhKeyboardLl, _callback, GetModuleHandle(null), 0);
        _ready.Set();
        if (_hook == 0) return;
        while (GetMessage(out var msg, 0, 0, 0) > 0)
        {
            TranslateMessage(ref msg);
            DispatchMessage(ref msg);
        }
        UnhookWindowsHookEx(_hook);
        _hook = 0;
        lock (_pressed)
        {
            foreach (var key in _pressed.ToArray()) RaiseKeyEvent(key, false, false);
            _pressed.Clear();
        }
    }

    private nint Callback(int code, nint wParam, nint lParam)
    {
        if (code >= 0)
        {
            var info = Marshal.PtrToStructure<KBDLLHOOKSTRUCT>(lParam);
            var down = wParam == WmKeyDown || wParam == WmSysKeyDown;
            var up = wParam == WmKeyUp || wParam == WmSysKeyUp;
            if (down || up)
            {
                var key = (uint)info.vkCode;
                var injected = (info.flags & 0x10) != 0 || (info.flags & 0x02) != 0;
                lock (_pressed)
                {
                    if (down) _pressed.Add(key); else _pressed.Remove(key);
                }
                RaiseKeyEvent(key, down, injected);
            }
        }
        return CallNextHookEx(_hook, code, wParam, lParam);
    }

    private void RaiseKeyEvent(uint key, bool down, bool injected)
    {
        try { KeyEvent?.Invoke(this, new KeyboardHookEventArgs(key, down, injected)); } catch { /* hooks must not take down the input thread */ }
    }

    public void Dispose()
    {
        if (_threadId != 0) PostThreadMessage(_threadId, WmQuit, 0, 0);
        if (_thread.IsAlive) _thread.Join(500);
        _ready.Dispose();
    }
    [StructLayout(LayoutKind.Sequential)] private struct KBDLLHOOKSTRUCT { public uint vkCode, scanCode, flags, time; public nint dwExtraInfo; }
    [StructLayout(LayoutKind.Sequential)] private struct MSG { public nint hwnd; public uint message; public nint wParam, lParam; public uint time; public int ptX, ptY; }
    private delegate nint HookProc(int code, nint wParam, nint lParam);
    [DllImport("user32.dll")] private static extern nint SetWindowsHookEx(int idHook, HookProc lpfn, nint hMod, uint threadId); [DllImport("user32.dll")] private static extern bool UnhookWindowsHookEx(nint hook); [DllImport("user32.dll")] private static extern nint CallNextHookEx(nint hook, int code, nint wParam, nint lParam); [DllImport("user32.dll")] private static extern int GetMessage(out MSG msg, nint hWnd, uint min, uint max); [DllImport("user32.dll")] private static extern bool TranslateMessage(ref MSG msg); [DllImport("user32.dll")] private static extern nint DispatchMessage(ref MSG msg); [DllImport("user32.dll")] private static extern bool PostThreadMessage(uint id, uint msg, nint w, nint l); [DllImport("user32.dll")] private static extern bool PeekMessage(out MSG msg, nint hWnd, uint min, uint max, uint remove); [DllImport("kernel32.dll")] private static extern nint GetModuleHandle(string? name); [DllImport("kernel32.dll")] private static extern uint GetCurrentThreadId();
}
public sealed record KeyboardHookEventArgs(uint VirtualKey, bool IsKeyDown, bool IsInjected);

public sealed class SendInputTextInsertionService : ITextInsertionService
{
    public Task<InsertionResult> CopyForManualPasteAsync(string text, CancellationToken cancellationToken = default) =>
        ClipboardManualPaste.TryCopyForManualPasteAsync(text, cancellationToken);

    public async Task<InsertionResult> InsertAsync(string text, CancellationToken cancellationToken = default)
    {
        if (string.IsNullOrWhiteSpace(text)) return new InsertionResult(InsertionOutcome.Failed, "Nothing to insert.");
        cancellationToken.ThrowIfCancellationRequested();
        var inputs = text.EnumerateRunes()
            .SelectMany(r => char.ConvertFromUtf32(r.Value).Select(ch => new[] { Key(ch, true), Key(ch, false) }).SelectMany(x => x))
            .ToArray();
        var sent = SendInput((uint)inputs.Length, inputs, Marshal.SizeOf<INPUT>());
        if (sent == (uint)inputs.Length) return new InsertionResult(InsertionOutcome.Inserted);
        // UIPI prevents a non-elevated process from injecting into an elevated
        // target. Surface the actionable outcome instead of claiming clipboard
        // paste is a reliable substitute (the target may reject Ctrl+V too).
        var foreground = new ForegroundTargetService();
        var target = foreground.Capture();
        if (foreground.IsElevated(target))
            return new InsertionResult(InsertionOutcome.TargetElevated, "The foreground target is elevated; Windows blocked direct input. Run FlowerWhisp elevated or paste manually.");
        return await ClipboardManualPaste.TryPasteAndRestoreAsync(text, cancellationToken).ConfigureAwait(false)
            ? new InsertionResult(InsertionOutcome.CopiedForManualPaste, "Target rejected direct input; text was pasted through the clipboard. Press Ctrl+V if the target did not accept it.")
            : new InsertionResult(InsertionOutcome.Failed, "Target rejected input and the clipboard fallback was unavailable.");
    }
    private static INPUT Key(int codePoint, bool down) => new() { type = 1, u = new INPUTUNION { ki = new KEYBDINPUT { wVk = 0, wScan = (ushort)codePoint, dwFlags = (uint)(0x0004 | (down ? 0 : 0x0002)), dwExtraInfo = 0 } } };
    internal static INPUT[] PasteKeys() => [VirtualKey(0x11, true), VirtualKey(0x56, true), VirtualKey(0x56, false), VirtualKey(0x11, false)];
    private static INPUT VirtualKey(ushort key, bool down) => new() { type = 1, u = new INPUTUNION { ki = new KEYBDINPUT { wVk = key, wScan = 0, dwFlags = down ? 0u : 0x0002u } } };
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint nInputs, INPUT[] pInputs, int cbSize);
    [StructLayout(LayoutKind.Sequential)] internal struct INPUT { public uint type; public INPUTUNION u; }
    [StructLayout(LayoutKind.Explicit)] internal struct INPUTUNION { [FieldOffset(0)] public KEYBDINPUT ki; }
    [StructLayout(LayoutKind.Sequential)] internal struct KEYBDINPUT { public ushort wVk, wScan; public uint dwFlags, time; public nint dwExtraInfo; }
}

/// <summary>Best-effort clipboard bridge used only when UIPI blocks SendInput.</summary>
internal static class ClipboardManualPaste
{
    private const uint CfUnicodeText = 13;
    private const uint CfText = 1;
    private const uint CfOemText = 7;
    private const uint CfLocale = 16;
    private const uint CfDib = 8;
    private const uint CfDibV5 = 17;
    private const uint CfHtml = 0; // registered formats are discovered by name where available
    private const uint CfRtf = 0;
    private const uint GmemMoveable = 0x0002;
    private const int ClipboardOpenAttempts = 5;
    private const int ClipboardRetryDelayMs = 10;
    private static readonly SemaphoreSlim Gate = new(1, 1);

    public static async Task<InsertionResult> TryCopyForManualPasteAsync(string text, CancellationToken cancellationToken)
    {
        if (string.IsNullOrWhiteSpace(text)) return new InsertionResult(InsertionOutcome.Failed, "Nothing to copy.");
        await Gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            cancellationToken.ThrowIfCancellationRequested();
            return TrySetText(text)
                ? new InsertionResult(InsertionOutcome.CopiedForManualPaste, "Text was copied to the clipboard for manual paste.")
                : new InsertionResult(InsertionOutcome.Failed, "The clipboard was unavailable.");
        }
        finally { Gate.Release(); }
    }

    public static async Task<bool> TryPasteAndRestoreAsync(string text, CancellationToken cancellationToken)
    {
        await Gate.WaitAsync(cancellationToken).ConfigureAwait(false);
        try
        {
            if (!TryReadSnapshot(out var previous)) return false;
            cancellationToken.ThrowIfCancellationRequested();
            if (!TrySetText(text)) return false;
            var sequenceAfterSet = GetClipboardSequenceNumber();
            var keys = SendInputTextInsertionService.PasteKeys();
            var sent = SendInput((uint)keys.Length, keys, Marshal.SizeOf<SendInputTextInsertionService.INPUT>());
            if (sent != (uint)keys.Length) return false;
            try { await Task.Delay(100, cancellationToken).ConfigureAwait(false); } catch (OperationCanceledException) { return true; }
            // Do not overwrite a clipboard value created by the target/application.
            if (GetClipboardSequenceNumber() == sequenceAfterSet) TryRestore(previous);
            return true;
        }
        finally { Gate.Release(); }
    }

    private sealed record ClipboardSnapshot(IReadOnlyList<ClipboardFormat> Formats);
    private sealed record ClipboardFormat(uint Format, byte[] Data);

    private static bool TryReadSnapshot(out ClipboardSnapshot snapshot)
    {
        snapshot = new ClipboardSnapshot([]);
        if (!TryOpenClipboard()) return false;
        try
        {
            var formats = new List<ClipboardFormat>();
            uint format = 0;
            while ((format = EnumClipboardFormats(format)) != 0)
            {
                // These formats cover normal text and the common rich/text
                // payloads exposed by Windows editors. Skip opaque handles
                // (bitmaps/metafiles) that cannot be safely cloned this way.
                if (!IsPracticalFormat(format)) continue;
                var handle = GetClipboardData(format);
                if (handle == 0) continue;
                var size = GlobalSize(handle);
                var pointer = GlobalLock(handle);
                if (pointer == 0 || size == 0) { if (pointer != 0) GlobalUnlock(handle); continue; }
                try { var data = new byte[(int)Math.Min(size, int.MaxValue)]; Marshal.Copy(pointer, data, 0, data.Length); formats.Add(new ClipboardFormat(format, data)); }
                finally { GlobalUnlock(handle); }
            }
            snapshot = new ClipboardSnapshot(formats);
            return true;
        }
        finally { CloseClipboard(); }
    }

    private static bool TrySetText(string value)
    {
        if (!TryOpenClipboard()) return false;
        try
        {
            if (!EmptyClipboard()) return false;
            var bytes = Encoding.Unicode.GetBytes(value + "\0");
            try
            {
                var handle = GlobalAlloc(GmemMoveable, (nuint)bytes.Length);
                if (handle == 0) return false;
                var pointer = GlobalLock(handle);
                if (pointer == 0) { GlobalFree(handle); return false; }
                Marshal.Copy(bytes, 0, pointer, bytes.Length);
                GlobalUnlock(handle);
                if (SetClipboardData(CfUnicodeText, handle) == 0) { GlobalFree(handle); return false; }
                return true;
            }
            finally { CryptographicOperations.ZeroMemory(bytes); }
        }
        finally { CloseClipboard(); }
    }

    private static void TryRestore(ClipboardSnapshot snapshot)
    {
        if (!TryOpenClipboard()) return;
        try
        {
            if (!EmptyClipboard()) return;
            foreach (var format in snapshot.Formats)
            {
                var handle = GlobalAlloc(GmemMoveable, (nuint)format.Data.Length);
                if (handle == 0) return;
                var pointer = GlobalLock(handle);
                if (pointer == 0) { GlobalFree(handle); return; }
                Marshal.Copy(format.Data, 0, pointer, format.Data.Length);
                GlobalUnlock(handle);
                if (SetClipboardData(format.Format, handle) == 0) GlobalFree(handle);
            }
        }
        finally { CloseClipboard(); }
    }

    private static bool IsPracticalFormat(uint format) => format is CfUnicodeText or CfText or CfOemText or CfLocale or CfDib or CfDibV5 || format >= 0xC000;

    private static bool TryOpenClipboard()
    {
        // Clipboard ownership is process-wide and other apps can hold it briefly.
        // Retry only for a small, bounded window so insertion never hangs.
        for (var attempt = 0; attempt < ClipboardOpenAttempts; attempt++)
        {
            if (OpenClipboard(0)) return true;
            if (attempt + 1 < ClipboardOpenAttempts) Thread.Sleep(ClipboardRetryDelayMs);
        }
        return false;
    }

    [DllImport("user32.dll", SetLastError = true)] private static extern bool OpenClipboard(nint owner);
    [DllImport("user32.dll", SetLastError = true)] private static extern bool CloseClipboard();
    [DllImport("user32.dll", SetLastError = true)] private static extern bool EmptyClipboard();
    [DllImport("user32.dll")] private static extern nint GetClipboardData(uint format);
    [DllImport("user32.dll")] private static extern nint SetClipboardData(uint format, nint memory);
    [DllImport("user32.dll")] private static extern uint EnumClipboardFormats(uint format);
    [DllImport("user32.dll")] private static extern uint GetClipboardSequenceNumber();
    [DllImport("kernel32.dll", SetLastError = true)] private static extern nint GlobalAlloc(uint flags, nuint bytes);
    [DllImport("kernel32.dll", SetLastError = true)] private static extern nint GlobalFree(nint memory);
    [DllImport("kernel32.dll")] private static extern nuint GlobalSize(nint memory);
    [DllImport("kernel32.dll")] private static extern nint GlobalLock(nint memory);
    [DllImport("kernel32.dll")] private static extern bool GlobalUnlock(nint memory);
    [DllImport("user32.dll", SetLastError = true)] private static extern uint SendInput(uint count, SendInputTextInsertionService.INPUT[] inputs, int size);
}

public sealed class DpapiSecretStore : ISecretStore
{
    private const int MaxKeyLength = 64;
    private readonly string _directory;
    public DpapiSecretStore(string? directory = null) => _directory = directory ?? Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "FlowerWhisp", "secrets");
    public async Task SetAsync(string key, string value, CancellationToken cancellationToken = default)
    {
        var path = PathFor(key);
        Directory.CreateDirectory(_directory);
        var plaintext = Encoding.UTF8.GetBytes(value);
        byte[]? protectedBytes = null;
        try
        {
            protectedBytes = ProtectedData.Protect(plaintext, null, DataProtectionScope.CurrentUser);
            await File.WriteAllBytesAsync(path, protectedBytes, cancellationToken).ConfigureAwait(false);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(plaintext);
            if (protectedBytes is not null) CryptographicOperations.ZeroMemory(protectedBytes);
        }
    }

    public async Task<string?> GetAsync(string key, CancellationToken cancellationToken = default)
    {
        var path = PathFor(key);
        if (!File.Exists(path)) return null;
        var protectedBytes = await File.ReadAllBytesAsync(path, cancellationToken).ConfigureAwait(false);
        byte[]? plaintext = null;
        try
        {
            plaintext = ProtectedData.Unprotect(protectedBytes, null, DataProtectionScope.CurrentUser);
            return Encoding.UTF8.GetString(plaintext);
        }
        finally
        {
            CryptographicOperations.ZeroMemory(protectedBytes);
            if (plaintext is not null) CryptographicOperations.ZeroMemory(plaintext);
        }
    }

    public Task DeleteAsync(string key, CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();
        var path = PathFor(key);
        if (File.Exists(path)) File.Delete(path);
        return Task.CompletedTask;
    }

    private string PathFor(string key)
    {
        if (string.IsNullOrWhiteSpace(key) || key.Length > MaxKeyLength ||
            key[0] == '.' || key[^1] == '.' ||
            key.Any(ch => !(char.IsAsciiLetterOrDigit(ch) || ch is '-' or '_')))
            throw new ArgumentException("Secret key must be 1-64 ASCII letters, digits, '-' or '_'.", nameof(key));
        return Path.Combine(_directory, key + ".bin");
    }
}

internal static class ProtectedData
{
    public static byte[] Protect(byte[] data, byte[]? entropy, DataProtectionScope scope) => Transform(data, entropy, true);
    public static byte[] Unprotect(byte[] data, byte[]? entropy, DataProtectionScope scope) => Transform(data, entropy, false);
    private static byte[] Transform(byte[] data, byte[]? entropy, bool protect)
    {
        // CryptProtectData/CryptUnprotectData allocate output with LocalAlloc;
        // input and entropy are our AllocHGlobal buffers. All three are cleared
        // and released in the finally block, including native-call failures.
        var input = new DATA_BLOB();
        var optional = new DATA_BLOB();
        var output = new DATA_BLOB();
        try
        {
            input = new DATA_BLOB(data);
            optional = entropy is null ? new DATA_BLOB() : new DATA_BLOB(entropy);
            var ok = protect
                ? CryptProtectData(ref input, null, ref optional, IntPtr.Zero, IntPtr.Zero, 0, ref output)
                : CryptUnprotectData(ref input, IntPtr.Zero, ref optional, IntPtr.Zero, IntPtr.Zero, 0, ref output);
            if (!ok) throw new System.ComponentModel.Win32Exception();
            return output.ToArray();
        }
        finally
        {
            input.Free();
            optional.Free();
            output.FreeLocal();
        }
    }
    [DllImport("crypt32.dll", SetLastError = true)] private static extern bool CryptProtectData(ref DATA_BLOB pDataIn, string? desc, ref DATA_BLOB entropy, nint reserved, nint prompt, uint flags, ref DATA_BLOB pDataOut);
    [DllImport("crypt32.dll", SetLastError = true)] private static extern bool CryptUnprotectData(ref DATA_BLOB pDataIn, nint desc, ref DATA_BLOB entropy, nint reserved, nint prompt, uint flags, ref DATA_BLOB pDataOut);
    [DllImport("kernel32.dll")] private static extern nint LocalFree(nint handle);
    [StructLayout(LayoutKind.Sequential)] private struct DATA_BLOB
    {
        public int cbData;
        public nint pbData;

        public DATA_BLOB(byte[] data)
        {
            cbData = data.Length;
            pbData = cbData == 0 ? nint.Zero : Marshal.AllocHGlobal(cbData);
            if (pbData != 0) Marshal.Copy(data, 0, pbData, cbData);
        }

        public byte[] ToArray()
        {
            var bytes = new byte[cbData];
            if (pbData != 0) Marshal.Copy(pbData, bytes, 0, cbData);
            return bytes;
        }

        public void Free()
        {
            if (pbData == 0) return;
            ZeroMemory(pbData, cbData);
            Marshal.FreeHGlobal(pbData);
            pbData = 0;
            cbData = 0;
        }

        public void FreeLocal()
        {
            if (pbData == 0) return;
            ZeroMemory(pbData, cbData);
            LocalFree(pbData);
            pbData = 0;
            cbData = 0;
        }

        private static void ZeroMemory(nint pointer, int length)
        {
            for (var index = 0; index < length; index++) Marshal.WriteByte(pointer, index, 0);
        }
    }
}
public enum DataProtectionScope { CurrentUser, LocalMachine }
