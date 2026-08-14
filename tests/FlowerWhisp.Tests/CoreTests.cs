using FlowerWhisp.Application;
using FlowerWhisp.Core;

namespace FlowerWhisp.Tests;

public class CoreTests
{
    [Fact]
    public void HoldChord_starts_on_complete_chord_down_and_releases_to_insert()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        var started = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        Assert.Equal(DictationState.PendingChord, started.State);
        Assert.True(started.ShouldStartRecording);
        Assert.False(started.ShouldStopRecording);

        var finished = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.LeftWin));
        Assert.True(finished.ShouldStopRecording);
        Assert.True(finished.ShouldInsert);
        Assert.False(finished.ShowConfirmation);
    }

    [Fact]
    public void Space_reclassifies_same_capture_and_second_chord_stops_and_accepts()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        var initial = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        Assert.True(initial.ShouldStartRecording);

        var recording = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.Space));
        Assert.Equal(DictationMode.Toggle, recording.Mode);
        Assert.Equal(DictationState.Recording, recording.State);
        Assert.False(recording.ShouldStartRecording);
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.Space));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.LeftWin));

        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        var ready = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.Space));
        Assert.Equal(DictationState.ReadyToInsert, ready.State);
        Assert.Equal(DictationMode.Toggle, ready.Mode);
        Assert.True(ready.ShowConfirmation);
        Assert.True(ready.ShouldStopRecording);
        Assert.True(ready.ShouldInsert);
    }

    [Fact]
    public void Injected_events_are_ignored_and_escape_cancels()
    {
        var machine = new ShortcutStateMachine();
        var ignored = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl, true));
        Assert.Equal(DictationState.Idle, ignored.State);
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        var cancelled = machine.Handle(new ShortcutEvent(ShortcutEventKind.Escape));
        Assert.True(cancelled.ShouldCancel);
        Assert.Equal(DictationState.Cancelled, cancelled.State);
    }

    [Fact]
    public void Right_side_modifiers_and_duplicate_keydowns_are_deterministic()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.RightWin));
        var started = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.RightCtrl));
        Assert.True(started.ShouldStartRecording);
        var repeat = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.RightCtrl));
        Assert.False(repeat.ShouldStartRecording);
        var done = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.RightCtrl));
        Assert.True(done.ShouldStopRecording);
    }

    [Fact]
    public void Left_and_right_modifier_hold_is_not_ended_by_releasing_one_side()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.RightCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        var firstRelease = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.LeftCtrl));
        Assert.False(firstRelease.ShouldStopRecording);
        var finalRelease = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyUp, ShortcutKey.RightCtrl));
        Assert.True(finalRelease.ShouldStopRecording);
    }

    [Fact]
    public void Cancel_event_resets_pending_without_starting_capture()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        var cancelled = machine.Handle(new ShortcutEvent(ShortcutEventKind.Cancel));
        Assert.Equal(DictationState.Cancelled, cancelled.State);
        Assert.False(cancelled.ShouldStartRecording);
    }

    [Fact]
    public void Other_key_cancels_pending_chord()
    {
        var machine = new ShortcutStateMachine();
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftCtrl));
        machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.LeftWin));
        var cancelled = machine.Handle(new ShortcutEvent(ShortcutEventKind.KeyDown, ShortcutKey.Other));
        Assert.True(cancelled.ShouldCancel);
        Assert.Equal(DictationState.Cancelled, cancelled.State);
    }

    [Fact]
    public void Groq_policy_intersects_authenticated_models_without_substitution()
    {
        var allowed = ProviderPolicy.FilterPolishModels([
            new ProviderModel("openai/gpt-oss-20b"),
            new ProviderModel("llama-3.1-8b-instant"),
            new ProviderModel("qwen-unsupported")]);
        Assert.Single(allowed);
        Assert.Contains("openai/gpt-oss-20b", allowed);
        Assert.DoesNotContain("llama-3.1-8b-instant", allowed);
        Assert.DoesNotContain("qwen-unsupported", allowed);
        Assert.Throws<InvalidOperationException>(() => ProviderPolicy.SelectDefaultPolish([new ProviderModel("llama-3.1-8b-instant")]));
    }

    [Fact]
    public async Task NeverStore_skips_history_but_aggregate_can_live()
    {
        var temp = Path.Combine(Path.GetTempPath(), "flowerwhisp-tests", Guid.NewGuid().ToString());
        var repo = new FlowerWhisp.Infrastructure.JsonDictationRepository(Path.Combine(temp, "dictations.json"));
        var usage = new FlowerWhisp.Infrastructure.JsonUsageAggregateRepository(Path.Combine(temp, "usage.json"));
        await usage.RecordAsync(new UsageAggregate(DateOnly.FromDateTime(DateTime.UtcNow), 1, 2, 10));
        Assert.Empty(await repo.ListAsync());
        Assert.Single(await usage.ListAsync());
    }

    [Fact]
    public async Task Orchestrator_start_only_opens_audio_and_finish_runs_full_lifecycle_in_order()
    {
        var events = new List<string>();
        var capture = new FakeCapture(events);
        var provider = new FakeTranscriber(events, "spoken words");
        var insertion = new FakeInsertion(events);
        var repository = new FakeDictationRepository(events);
        var usage = new FakeUsageRepository(events);
        var orchestrator = new DictationOrchestrator(
            capture, [provider], null, insertion, new FakeForeground(events), repository, usage);

        var session = await orchestrator.StartAsync(new DictationOptions(TranscriptionBackend.LocalWhisper));
        Assert.NotEqual(Guid.Empty, session.RequestId);
        Assert.Equal(["target", "start"], events);
        Assert.NotNull(orchestrator.ActiveSession);

        var result = await orchestrator.FinishAsync();
        Assert.Equal("spoken words", result.FinalText);
        Assert.Null(orchestrator.ActiveSession);
        Assert.Equal(["target", "start", "stop", "transcribe", "insert", "save", "usage"], events);
    }

    [Fact]
    public async Task Orchestrator_cancel_stops_and_discards_without_provider_or_persistence()
    {
        var events = new List<string>();
        var capture = new FakeCapture(events);
        var provider = new FakeTranscriber(events, "must not run");
        var repository = new FakeDictationRepository(events);
        var usage = new FakeUsageRepository(events);
        var orchestrator = new DictationOrchestrator(
            capture, [provider], null, new FakeInsertion(events), new FakeForeground(events), repository, usage);

        await orchestrator.StartAsync(new DictationOptions(TranscriptionBackend.LocalWhisper));
        await orchestrator.CancelAsync();
        Assert.Null(orchestrator.ActiveSession);
        Assert.Equal(["target", "start", "cancel"], events);
        Assert.Equal(0, provider.Calls);
        Assert.Equal(0, repository.Saves);
        Assert.Equal(0, usage.Calls);
    }

    [Fact]
    public async Task Orchestrator_changed_target_copies_for_manual_paste_without_inserting()
    {
        var events = new List<string>();
        var insertion = new FakeInsertion(events);
        var foreground = new FakeForeground(events) { Changed = true };
        var orchestrator = new DictationOrchestrator(
            new FakeCapture(events), [new FakeTranscriber(events, "private transcript")], null,
            insertion, foreground, new FakeDictationRepository(events), new FakeUsageRepository(events));

        var result = await orchestrator.ExecuteAsync(new DictationOptions(TranscriptionBackend.LocalWhisper));

        Assert.Equal(InsertionOutcome.TargetChanged, result.Insertion.Outcome);
        Assert.Equal(0, insertion.InsertCalls);
        Assert.Equal(1, insertion.ManualCopyCalls);
        Assert.DoesNotContain("private transcript", result.Insertion.Detail, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Orchestrator_elevated_target_copies_for_manual_paste_without_inserting()
    {
        var events = new List<string>();
        var insertion = new FakeInsertion(events);
        var foreground = new FakeForeground(events) { Elevated = true };
        var orchestrator = new DictationOrchestrator(
            new FakeCapture(events), [new FakeTranscriber(events, "private transcript")], null,
            insertion, foreground, new FakeDictationRepository(events), new FakeUsageRepository(events));

        var result = await orchestrator.ExecuteAsync(new DictationOptions(TranscriptionBackend.LocalWhisper));

        Assert.Equal(InsertionOutcome.TargetElevated, result.Insertion.Outcome);
        Assert.Equal(0, insertion.InsertCalls);
        Assert.Equal(1, insertion.ManualCopyCalls);
        Assert.DoesNotContain("private transcript", result.Insertion.Detail, StringComparison.Ordinal);
    }

    private sealed class FakeCapture(List<string> events) : IAudioCaptureService
    {
        public bool IsRecording { get; private set; }
        public Task StartAsync(CancellationToken cancellationToken = default) { events.Add("start"); IsRecording = true; return Task.CompletedTask; }
        public Task<AudioPayload> StopAsync(CancellationToken cancellationToken = default) { events.Add("stop"); IsRecording = false; return Task.FromResult(new AudioPayload(new byte[3200])); }
        public Task CancelAsync(CancellationToken cancellationToken = default) { events.Add("cancel"); IsRecording = false; return Task.CompletedTask; }
        public ValueTask DisposeAsync() => ValueTask.CompletedTask;
    }

    private sealed class FakeTranscriber(List<string> events, string text) : ITranscriptionProvider
    {
        public int Calls { get; private set; }
        public TranscriptionBackend Backend => TranscriptionBackend.LocalWhisper;
        public Task<TranscriptionResult> TranscribeAsync(TranscriptionRequest request, CancellationToken cancellationToken = default)
        { events.Add("transcribe"); Calls++; return Task.FromResult(new TranscriptionResult(request.RequestId, text, "en", TimeSpan.FromSeconds(1))); }
    }

    private sealed class FakeInsertion(List<string> events) : ITextInsertionService
    {
        public int InsertCalls { get; private set; }
        public int ManualCopyCalls { get; private set; }
        public Task<InsertionResult> InsertAsync(string text, CancellationToken cancellationToken = default)
        { events.Add("insert"); InsertCalls++; return Task.FromResult(new InsertionResult(InsertionOutcome.Inserted)); }
        public Task<InsertionResult> CopyForManualPasteAsync(string text, CancellationToken cancellationToken = default)
        { events.Add("manual-copy"); ManualCopyCalls++; return Task.FromResult(new InsertionResult(InsertionOutcome.CopiedForManualPaste)); }
    }

    private sealed class FakeForeground(List<string> events) : IForegroundTargetService
    {
        public bool Changed { get; init; }
        public bool Elevated { get; init; }
        public TargetSnapshot Capture() { events.Add("target"); return new(1, 2, "test", DateTimeOffset.UtcNow); }
        public bool HasChanged(TargetSnapshot target) => Changed;
        public bool IsElevated(TargetSnapshot target) => Elevated;
    }

    private sealed class FakeDictationRepository(List<string> events) : IDictationRepository
    {
        public int Saves { get; private set; }
        public Task SaveAsync(DictationRecord record, CancellationToken cancellationToken = default)
        { events.Add("save"); Saves++; return Task.CompletedTask; }
        public Task<IReadOnlyList<DictationRecord>> ListAsync(CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<DictationRecord>>([]);
        public Task DeleteAsync(Guid id, CancellationToken cancellationToken = default) => Task.CompletedTask;
    }

    private sealed class FakeUsageRepository(List<string> events) : IUsageAggregateRepository
    {
        public int Calls { get; private set; }
        public Task RecordAsync(UsageAggregate aggregate, CancellationToken cancellationToken = default)
        { events.Add("usage"); Calls++; return Task.CompletedTask; }
        public Task<IReadOnlyList<UsageAggregate>> ListAsync(CancellationToken cancellationToken = default) => Task.FromResult<IReadOnlyList<UsageAggregate>>([]);
    }
}
