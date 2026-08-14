using Microsoft.UI.Xaml;
using Microsoft.UI.Windowing;
using System.Runtime.InteropServices;
using FlowerWhisp.Core;

namespace FlowerWhisp;

/// <summary>
/// Always-readable recording surface backed by the same DictationRuntime as
/// MainPage.  Closing the window only closes this view; the static app slot is
/// cleared so the next Open pill action creates a fresh WinUI Window instance.
/// </summary>
public sealed partial class PillWindow : Window
{
    private bool _recording;
    private bool _updatingMode;

    public PillWindow()
    {
        InitializeComponent();
        ExtendsContentIntoTitleBar = true;
        SetTitleBar(null);
        ConfigureNativePill();
        Closed += OnClosed;
        if (App.Runtime is not null) App.Runtime.StateChanged += OnRuntimeStateChanged;
        UpdateFromRuntime(new DictationRuntimeState(
            DictationState.Idle, App.Runtime?.Mode ?? DictationMode.Hold,
            false, false, "Ready · hold Ctrl + Win to speak."));
    }

    private void OnClosed(object sender, WindowEventArgs args)
    {
        if (App.Runtime is not null) App.Runtime.StateChanged -= OnRuntimeStateChanged;
        App.OnPillClosed(this);
    }

    private void OnModeChanged(object sender, RoutedEventArgs e)
    {
        if (_updatingMode) return;
        var toggle = ToggleModeCheckBox.IsChecked == true;
        if (!_recording)
        {
            StateTitle.Text = toggle ? "Toggle mode ready" : "Ready to record";
            StateDescription.Text = toggle
                ? "Press Start, then finish to insert the prepared text."
                : "Hold Ctrl + Win to speak. Release to finish.";
        }
    }

    private async void OnActionClick(object sender, RoutedEventArgs e)
    {
        if (App.Runtime is null)
        {
            StateTitle.Text = "Runtime unavailable";
            StateDescription.Text = "Restart FlowerWhisp to initialize microphone and provider services.";
            return;
        }

        if (App.Runtime.IsRecording)
            await App.Runtime.FinishFromUiAsync();
        else
            await App.Runtime.StartFromUiAsync(ToggleModeCheckBox.IsChecked == true);
    }

    private async void OnInsertClick(object sender, RoutedEventArgs e)
    {
        if (App.Runtime is not null && App.Runtime.IsRecording)
            await App.Runtime.FinishFromUiAsync();
    }

    private void OnKeepEditingClick(object sender, RoutedEventArgs e)
    {
        StateDescription.Text = "The runtime inserts automatically when a capture finishes; review the ledger if the target changed.";
    }

    private async void OnDiscardClick(object sender, RoutedEventArgs e)
    {
        if (App.Runtime is not null) await App.Runtime.CancelFromUiAsync();
    }

    private void OnRuntimeStateChanged(object? sender, DictationRuntimeState state)
    {
        if (!DispatcherQueue.HasThreadAccess)
        {
            DispatcherQueue.TryEnqueue(() => UpdateFromRuntime(state));
            return;
        }
        UpdateFromRuntime(state);
    }

    private void UpdateFromRuntime(DictationRuntimeState state)
    {
        _recording = state.IsRecording;
        _updatingMode = true;
        ToggleModeCheckBox.IsChecked = state.Mode == DictationMode.Toggle;
        ToggleModeCheckBox.IsEnabled = !state.IsRecording;
        _updatingMode = false;

        StateDot.Fill = new Microsoft.UI.Xaml.Media.SolidColorBrush(state.State switch
        {
            DictationState.Failed => Microsoft.UI.Colors.OrangeRed,
            DictationState.Cancelled => Microsoft.UI.Colors.Gray,
            DictationState.Processing => Microsoft.UI.Colors.Gold,
            DictationState.Inserted => Microsoft.UI.Colors.MediumSeaGreen,
            _ when state.IsRecording => Microsoft.UI.Colors.OrangeRed,
            _ => Microsoft.UI.Colors.CornflowerBlue
        });
        StateTitle.Text = state.State switch
        {
            DictationState.Processing => "Finishing",
            DictationState.Inserted => "Inserted",
            DictationState.Cancelled => "Cancelled",
            DictationState.Failed => "Needs attention",
            _ when state.IsRecording => "Listening",
            _ => state.Mode == DictationMode.Toggle ? "Toggle mode ready" : "Ready to record"
        };
        StateDescription.Text = state.Message;
        ActionButton.Content = state.IsRecording
            ? (state.IsProcessing ? "Processing..." : "Finish recording")
            : "Start";
        // Hold mode never shows accept/discard. Toggle mode exposes both while
        // capture is live; Accept finishes/inserts and Discard cancels.
        ConfirmationPanel.Visibility = state.IsRecording && state.Mode == DictationMode.Toggle && !state.IsProcessing
            ? Visibility.Visible
            : Visibility.Collapsed;
        WaveProgress.Value = state.IsRecording ? (state.IsProcessing ? 88 : state.Mode == DictationMode.Toggle ? 72 : 58) : state.State == DictationState.Failed ? 0 : 14;
        TimerText.Text = state.IsRecording ? "LIVE" : "00:00";
    }

    private void ConfigureNativePill()
    {
        var hwnd = WinRT.Interop.WindowNative.GetWindowHandle(this);
        var styles = GetWindowLongPtr(hwnd, GwlExStyle).ToInt64();
        SetWindowLongPtr(hwnd, GwlExStyle, new nint(styles | WsExNoActivate | WsExToolWindow));

        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.IsAlwaysOnTop = true;
            presenter.IsResizable = false;
            presenter.IsMaximizable = false;
            presenter.IsMinimizable = false;
            presenter.SetBorderAndTitleBar(false, false);
        }

        AppWindow.Resize(new Windows.Graphics.SizeInt32(460, 190));
        var display = DisplayArea.GetFromWindowId(AppWindow.Id, DisplayAreaFallback.Primary);
        if (display is not null)
        {
            var work = display.WorkArea;
            AppWindow.Move(new Windows.Graphics.PointInt32(
                work.X + Math.Max(0, (work.Width - 460) / 2),
                work.Y + Math.Max(0, work.Height - 206)));
        }
    }

    private void OnCloseClick(object sender, RoutedEventArgs e) => Close();

    private const int GwlExStyle = -20;
    private const long WsExToolWindow = 0x00000080L;
    private const long WsExNoActivate = 0x08000000L;
    [DllImport("user32.dll", EntryPoint = "GetWindowLongPtrW")]
    private static extern nint GetWindowLongPtr(nint hwnd, int index);
    [DllImport("user32.dll", EntryPoint = "SetWindowLongPtrW")]
    private static extern nint SetWindowLongPtr(nint hwnd, int index, nint value);
}
