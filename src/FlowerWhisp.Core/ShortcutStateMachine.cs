namespace FlowerWhisp.Core;

public enum ShortcutKey { LeftCtrl, RightCtrl, LeftWin, RightWin, Space, Other }
public enum ShortcutEventKind { KeyDown, KeyUp, Escape, Cancel }
public sealed record ShortcutEvent(ShortcutEventKind Kind, ShortcutKey Key = ShortcutKey.Other, bool IsInjected = false);
public sealed record ShortcutSnapshot(
    DictationState State,
    DictationMode Mode,
    bool ShowConfirmation,
    bool ShouldStartRecording,
    bool ShouldStopRecording,
    bool ShouldInsert,
    bool ShouldCancel);

/// <summary>
/// Pure state machine for the global Ctrl+Win gesture.
///
/// The important distinction here is that the chord starts capture as soon as
/// both modifier families are down.  A subsequent Space key changes that same
/// capture to toggle mode; it never starts a second audio session.  Hold mode
/// finishes when the last Ctrl or Win key is released.  Toggle mode survives
/// modifier release and finishes on the next complete Ctrl+Win+Space chord.
/// </summary>
public sealed class ShortcutStateMachine
{
    private bool _leftCtrl;
    private bool _rightCtrl;
    private bool _leftWin;
    private bool _rightWin;
    private bool _space;
    private bool _other;
    private bool _captureActive;
    private bool _toggle;
    private bool _toggleChordReleased;
    private DictationState _state = DictationState.Idle;

    private bool CtrlDown => _leftCtrl || _rightCtrl;
    private bool WinDown => _leftWin || _rightWin;

    public ShortcutSnapshot Handle(ShortcutEvent input)
    {
        if (input.IsInjected) return Snapshot();

        if (input.Kind is ShortcutEventKind.Escape or ShortcutEventKind.Cancel)
        {
            _state = DictationState.Cancelled;
            var shouldCancel = _captureActive || _state == DictationState.Cancelled;
            _captureActive = false;
            return Snapshot(shouldCancel: shouldCancel);
        }

        if (input.Kind == ShortcutEventKind.KeyDown)
        {
            // Repeat messages are common with a low-level hook.  Treat them as
            // idempotent so a held key cannot start/stop a session repeatedly.
            if (!SetKey(input.Key, isDown: true)) return Snapshot();

            if (input.Key == ShortcutKey.Other)
            {
                if (_captureActive || _state == DictationState.PendingChord || _state == DictationState.Recording)
                {
                    _captureActive = false;
                    _state = DictationState.Cancelled;
                    return Snapshot(shouldCancel: true);
                }
                return Snapshot();
            }

            // The first complete Ctrl+Win chord starts an ordinary hold
            // capture.  PendingChord is intentionally retained so the UI can
            // distinguish "the chord is held" from toggle recording.
            if (!_captureActive && _state == DictationState.Idle && CtrlDown && WinDown)
            {
                _captureActive = true;
                _toggle = false;
                _toggleChordReleased = false;
                _state = DictationState.PendingChord;
                return Snapshot(shouldStartRecording: true);
            }

            // Space while the initial chord is held reclassifies that exact
            // capture.  There is deliberately no second ShouldStart signal.
            if (_captureActive && !_toggle && _state == DictationState.PendingChord && CtrlDown && WinDown && _space)
            {
                _toggle = true;
                _state = DictationState.Recording;
                return Snapshot();
            }

            // In toggle mode, a fresh complete chord is the tick/accept action.
            // The key-repeat guard above ensures this fires once even when the
            // hook reports autorepeat messages for Space.
            if (_captureActive && _toggle && _toggleChordReleased && CtrlDown && WinDown && _space)
            {
                _captureActive = false;
                _state = DictationState.ReadyToInsert;
                return Snapshot(showConfirmation: true, shouldStopRecording: true, shouldInsert: true);
            }

            return Snapshot();
        }

        if (input.Kind == ShortcutEventKind.KeyUp)
        {
            if (!SetKey(input.Key, isDown: false)) return Snapshot();

            if (!_captureActive) return Snapshot();

            if (_toggle)
            {
                // Toggle mode remains alive after the initial chord has gone
                // away.  Mark the next Ctrl+Win+Space as a fresh stop chord.
                if (!CtrlDown && !WinDown) _toggleChordReleased = true;
                return Snapshot();
            }

            // Hold mode stops only after the last Ctrl and last Win key have
            // both been released.  Left/right modifiers are tracked
            // independently, so releasing one side cannot end the capture
            // while its sibling is still down.
            if (!CtrlDown || !WinDown)
            {
                _captureActive = false;
                _state = DictationState.Processing;
                return Snapshot(shouldStopRecording: true, shouldInsert: true);
            }

            return Snapshot();
        }

        return Snapshot();
    }

    public void MarkReady() => _state = DictationState.ReadyToInsert;
    public void MarkInserted() => _state = DictationState.Inserted;
    public void MarkFailed() => _state = DictationState.Failed;

    public void Reset()
    {
        _leftCtrl = _rightCtrl = _leftWin = _rightWin = _space = _other = false;
        _captureActive = _toggle = _toggleChordReleased = false;
        _state = DictationState.Idle;
    }

    private bool SetKey(ShortcutKey key, bool isDown)
    {
        ref var value = ref GetKeyStorage(key);
        if (value == isDown) return false;
        value = isDown;
        return true;
    }

    private ref bool GetKeyStorage(ShortcutKey key)
    {
        switch (key)
        {
            case ShortcutKey.LeftCtrl: return ref _leftCtrl;
            case ShortcutKey.RightCtrl: return ref _rightCtrl;
            case ShortcutKey.LeftWin: return ref _leftWin;
            case ShortcutKey.RightWin: return ref _rightWin;
            case ShortcutKey.Space: return ref _space;
            default: return ref _other;
        }
    }

    private ShortcutSnapshot Snapshot(
        bool showConfirmation = false,
        bool shouldStartRecording = false,
        bool shouldStopRecording = false,
        bool shouldInsert = false,
        bool shouldCancel = false) =>
        new(_state, _toggle ? DictationMode.Toggle : DictationMode.Hold, showConfirmation,
            shouldStartRecording, shouldStopRecording, shouldInsert, shouldCancel);
}
