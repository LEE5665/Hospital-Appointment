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
    public ObservableCollection<DiagnosisSearchRow> DiagnosisResults { get; } = new();
    public ObservableCollection<EncounterDiagnosisRow> Diagnoses { get; } = new();
    [ObservableProperty] private string diagnosisQuery = "";
    [ObservableProperty] private DiagnosisSearchRow? selectedDiagnosisResult;
    [ObservableProperty] private EncounterDiagnosisRow? selectedDiagnosis;
    [ObservableProperty] private string diagnosisSearchMessage = "진단명·별칭 또는 코드로 검색하세요. 최대 50개가 표시됩니다.";
    public bool IsDoctor => AuthService.Instance.CurrentUser?.Role == "DOCTOR";
    [ObservableProperty] private bool mineOnly = AuthService.Instance.CurrentUser?.Role == "DOCTOR";
    [ObservableProperty] private DateTime? selectedDate = DateTime.Today;
    [ObservableProperty] private int filterIndex;
    [ObservableProperty] private EncounterRow? selectedEncounter;
    [ObservableProperty] private EncounterRow? selectedQueueItem;
    partial void OnSelectedQueueItemChanged(EncounterRow? value) {
        if (value != null) SelectedEncounter = value;
    }
    [ObservableProperty] private string subjective = "";
    [ObservableProperty] private string objective = "";
    [ObservableProperty] private string assessment = "";
    [ObservableProperty] private string plan = "";
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
        Subjective = value?.Subjective ?? "";
        Objective = value?.Objective ?? "";
        Assessment = value?.Assessment ?? "";
        Plan = value?.Plan ?? "";
        Diagnoses.Clear();
        foreach (var diagnosis in value?.Diagnoses ?? []) Diagnoses.Add(diagnosis);
        DiagnosisResults.Clear();
        SelectedDiagnosisResult = null;
        SelectedDiagnosis = null;
        DiagnosisQuery = "";
        DiagnosisSearchMessage = "진단명·별칭 또는 코드로 검색하세요. 최대 50개가 표시됩니다.";
        settingNote = false;
        HasUnsavedChanges = false;
        OnPropertyChanged(nameof(CanStart)); OnPropertyChanged(nameof(CanWrite));
    }
    partial void OnSubjectiveChanged(string value) => UpdateDirtyState();
    partial void OnObjectiveChanged(string value) => UpdateDirtyState();
    partial void OnAssessmentChanged(string value) => UpdateDirtyState();
    partial void OnPlanChanged(string value) => UpdateDirtyState();
    private void UpdateDirtyState() {
        if (!settingNote) HasUnsavedChanges = Subjective != (SelectedEncounter?.Subjective ?? "")
            || Objective != (SelectedEncounter?.Objective ?? "")
            || Assessment != (SelectedEncounter?.Assessment ?? "")
            || Plan != (SelectedEncounter?.Plan ?? "")
            || !Diagnoses.SequenceEqual(SelectedEncounter?.Diagnoses ?? []);
    }
    [RelayCommand] private Task SearchDiagnosesAsync() => Run(async () => {
        if (!CanWrite) return;
        DiagnosisResults.Clear();
        SelectedDiagnosisResult = null;
        if (string.IsNullOrWhiteSpace(DiagnosisQuery)) {
            DiagnosisSearchMessage = "진단명 또는 코드를 입력해 주세요.";
            return;
        }
        var rows = await api.SendAsync<List<DiagnosisSearchRow>>(HttpMethod.Get,
            "diagnoses?query=" + Uri.EscapeDataString(DiagnosisQuery.Trim()));
        foreach (var row in rows) DiagnosisResults.Add(row);
        SelectedDiagnosisResult = DiagnosisResults.FirstOrDefault();
        DiagnosisSearchMessage = rows.Count == 0 ? "검색 결과가 없습니다. 검색어와 진단코드 초기화 상태를 확인해 주세요."
            : rows.Count == 50 ? "최대 50개 표시 · 검색어를 구체적으로 입력하면 결과를 좁힐 수 있습니다." : $"검색 결과 {rows.Count}개";
    });
    [RelayCommand] private void AddDiagnosis() {
        if (!CanWrite || SelectedDiagnosisResult is not { } item) return;
        if (Diagnoses.Any(d => d.Code == item.Code)) { Message = "이미 추가한 진단입니다."; return; }
        if (Diagnoses.Count >= 30) { Message = "진단은 최대 30개까지 등록할 수 있습니다."; return; }
        var diagnosis = new EncounterDiagnosisRow(item.Code, item.Name, item.ClassificationVersion,
            !Diagnoses.Any(d => d.Principal) && item.PrincipalDiagnosisAllowed, item.PrincipalDiagnosisAllowed);
        Diagnoses.Add(diagnosis);
        SelectedDiagnosis = diagnosis;
        UpdateDirtyState();
        Message = "진단을 추가했습니다. 기록 저장을 누르면 SOAP과 함께 저장됩니다.";
    }
    [RelayCommand] private void RemoveDiagnosis() {
        if (!CanWrite || SelectedDiagnosis is not { } item) return;
        Diagnoses.Remove(item);
        SelectedDiagnosis = null;
        UpdateDirtyState();
    }
    [RelayCommand] private void SetPrincipalDiagnosis() {
        if (!CanWrite || SelectedDiagnosis is not { } item) return;
        if (!item.PrincipalDiagnosisAllowed) { Message = "주진단으로 사용할 수 없는 코드입니다."; return; }
        var code = item.Code;
        for (int i = 0; i < Diagnoses.Count; i++) Diagnoses[i] = Diagnoses[i] with { Principal = Diagnoses[i].Code == code };
        SelectedDiagnosis = Diagnoses.First(d => d.Code == code);
        UpdateDirtyState();
    }
    [RelayCommand] private void ImportReason() {
        if (!CanWrite || string.IsNullOrWhiteSpace(SelectedEncounter?.Reason)) return;
        if (!string.IsNullOrWhiteSpace(Subjective)) {
            Message = "S에 작성된 내용이 있습니다. 방문 사유는 위 내용을 참고해 추가해 주세요.";
            return;
        }
        Subjective = SelectedEncounter.Reason;
        Message = "방문 사유를 S에 가져왔습니다. 문진 내용을 보완해 주세요.";
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
    [RelayCommand] private Task SaveSoapAsync() => Save(false);
    [RelayCommand] private Task CompleteAsync() => Save(true);
    private Task Save(bool complete) => Run(async () => {
        if (!CanWrite || SelectedEncounter == null) return;
        if (complete && new[] { Subjective, Objective, Assessment, Plan }.All(string.IsNullOrWhiteSpace)) {
            Message = "SOAP 진료기록을 작성한 후 완료해 주세요.";
            return;
        }
        if (complete && Diagnoses.Count > 0 && Diagnoses.Count(d => d.Principal) != 1) {
            Message = "등록한 진단 중 주진단을 하나 지정해 주세요.";
            return;
        }
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Put, $"encounters/{SelectedEncounter.Id}/soap",
            new { subjective = Subjective, objective = Objective, assessment = Assessment, plan = Plan,
                complete, version = SelectedEncounter.Version,
                diagnoses = Diagnoses.Select(d => new { code = d.Code, principal = d.Principal }).ToArray() });
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
