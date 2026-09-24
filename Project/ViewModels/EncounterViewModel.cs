using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public partial class EncounterViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    private int page;
    private int totalPages;
    private long totalElements;
    private bool settingNote;
    public ObservableCollection<EncounterRow> Encounters { get; } = new();
    public bool IsDoctor => AuthService.Instance.CurrentUser?.Role == "DOCTOR";
    [ObservableProperty] private bool mineOnly = AuthService.Instance.CurrentUser?.Role == "DOCTOR";
    [ObservableProperty] private DateTime? selectedDate = DateTime.Today;
    [ObservableProperty] private int filterIndex;
    [ObservableProperty] private EncounterRow? selectedEncounter;
    [ObservableProperty] private EncounterRow? selectedQueueItem;
    partial void OnSelectedQueueItemChanged(EncounterRow? value) {
        if (value != null) SelectedEncounter = value;
    }
    [ObservableProperty] private string note = "";
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(IsQueueSelectionEnabled))] private bool hasUnsavedChanges;
    public bool IsQueueSelectionEnabled => !HasUnsavedChanges;
    [ObservableProperty] private string message = "접수 시각이 빠른 순서입니다. 완료 기록은 필터에서 따로 조회하세요.";
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(IsIdle))] private bool isBusy;
    public bool IsIdle => !IsBusy;
    public string PageLabel => $"총 {totalElements}명 · {(totalPages == 0 ? 0 : page + 1)} / {totalPages}페이지 · 25명씩";
    public bool CanPrevious => page > 0;
    public bool CanNext => page + 1 < totalPages;
    public bool CanStart => IsDoctor && SelectedEncounter is { Status: "WAITING" } e && (e.DoctorId == null || e.DoctorId == AuthService.Instance.CurrentUser?.Id);
    public bool CanWrite => IsDoctor && SelectedEncounter is { Status: "IN_PROGRESS" } e && e.DoctorId == AuthService.Instance.CurrentUser?.Id;
    partial void OnSelectedEncounterChanged(EncounterRow? value) {
        settingNote = true;
        Note = value?.Note ?? "";
        settingNote = false;
        HasUnsavedChanges = false;
        OnPropertyChanged(nameof(CanStart)); OnPropertyChanged(nameof(CanWrite));
    }
    partial void OnNoteChanged(string value) {
        if (!settingNote) HasUnsavedChanges = value != (SelectedEncounter?.Note ?? "");
    }
    private bool CanReload() {
        if (!HasUnsavedChanges) return true;
        Message = "작성 중인 진료기록을 먼저 저장해 주세요.";
        return false;
    }
    private async Task Run(Func<Task> action) {
        if (IsBusy) return;
        IsBusy = true;
        try { await action(); }
        catch (Exception ex) { Message = ex is HttpRequestException ? "서버 연결에 실패했습니다." : ex.Message; }
        finally { IsBusy = false; }
    }
    private async Task LoadPage(long? selectId = null) {
        if (SelectedDate == null) throw new InvalidOperationException("조회 날짜를 선택해 주세요.");
        string filter = FilterIndex switch { 1 => "WAITING", 2 => "IN_PROGRESS", 3 => "COMPLETED", _ => "ACTIVE" };
        string path = "encounters/queue?date=" + SelectedDate.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture)
            + $"&filter={filter}&mine={(IsDoctor && MineOnly ? "true" : "false")}&page={page}&size=25";
        var result = await api.SendAsync<EncounterPage>(HttpMethod.Get, path);
        if (page > 0 && page >= result.TotalPages) {
            page = Math.Max(0, result.TotalPages - 1);
            await LoadPage(selectId); return;
        }
        totalElements = result.TotalElements; totalPages = result.TotalPages;
        Encounters.Clear();
        for (int i = 0; i < result.Items.Count; i++) Encounters.Add(result.Items[i] with { QueuePosition = page * 25 + i + 1 });
        SelectedQueueItem = Encounters.FirstOrDefault(e => e.Id == selectId);
        SelectedEncounter = SelectedQueueItem;
        OnPropertyChanged(nameof(PageLabel)); OnPropertyChanged(nameof(CanPrevious)); OnPropertyChanged(nameof(CanNext));
    }
    [RelayCommand] private Task RefreshAsync() => Run(async () => {
        if (!CanReload()) return;
        var id = SelectedEncounter?.Id;
        page = 0; await LoadPage(id);
        Message = totalElements == 0 ? "해당 조건의 환자가 없습니다." : "접수 시각순으로 조회했습니다. 목록 순서는 현재 필터 기준입니다.";
    });
    [RelayCommand] private Task PreviousAsync() => Run(async () => { if (!CanPrevious || !CanReload()) return; page--; await LoadPage(); });
    [RelayCommand] private Task NextAsync() => Run(async () => { if (!CanNext || !CanReload()) return; page++; await LoadPage(); });
    public Task OpenEncounterAsync(long id) => Run(async () => {
        if (!CanReload()) return;
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Get, $"encounters/{id}");
        SelectedDate = row.RegisteredAt.Date;
        FilterIndex = row.Status == "COMPLETED" ? 3 : 0;
        page = 0; await LoadPage(); SelectedEncounter = row;
        Message = "선택한 환자의 진료를 열었습니다.";
    });
    [RelayCommand] private Task StartAsync() => Run(async () => {
        if (!CanStart || SelectedEncounter == null) return;
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Post, $"encounters/{SelectedEncounter.Id}/start");
        await LoadPage(row.Id);
        if (SelectedEncounter == null) SelectedEncounter = row;
        Message = "진료를 시작했습니다.";
    });
    [RelayCommand] private Task SaveNoteAsync() => Save(false);
    [RelayCommand] private Task CompleteAsync() => Save(true);
    private Task Save(bool complete) => Run(async () => {
        if (!CanWrite || SelectedEncounter == null) return;
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Put, $"encounters/{SelectedEncounter.Id}/note",
            new { content = Note, complete, version = SelectedEncounter.Version });
        var old = Encounters.FirstOrDefault(e => e.Id == row.Id);
        if (old != null) {
            row = row with { QueuePosition = old.QueuePosition };
            Encounters[Encounters.IndexOf(old)] = row;
            SelectedQueueItem = row;
        }
        SelectedEncounter = row;
        if (complete) {
            await LoadPage();
            Message = "진료 완료. 기본 목록에서 제외되며 완료 기록에서 조회할 수 있습니다.";
        } else Message = "진료기록이 저장되었습니다.";
    });
}
