using System.Collections.ObjectModel;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public record HistoryVisit(long Id, DateTime RegisteredAt, DateTime? StartedAt, DateTime? CompletedAt,
    string DoctorName, string Status, string? Reason)
{
    public string StatusLabel => Status switch { "WAITING" => "대기", "IN_PROGRESS" => "진료 중", "COMPLETED" => "완료", _ => "취소" };
    public string Times => $"접수 {RegisteredAt:yyyy-MM-dd HH:mm}\n시작 {StartedAt:yyyy-MM-dd HH:mm} · 완료 {CompletedAt:yyyy-MM-dd HH:mm}";
}
public record HistoryPage(List<HistoryVisit> Items, int Page, int TotalPages, long TotalElements);

public partial class PatientHistoryViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    private long? patientId;
    private int generation;
    private int detailGeneration;
    private int page;
    private int totalPages;
    private long totalElements;
    public ObservableCollection<HistoryVisit> Visits { get; } = new();
    [ObservableProperty] private HistoryVisit? selectedVisit;
    [ObservableProperty] private EncounterRow? record;
    [ObservableProperty] private bool isLoading;
    [ObservableProperty] private string message = "환자를 선택하면 전체 방문 기록을 조회합니다.";
    [ObservableProperty] private string patientLabel = "";
    public bool CanRefresh => patientId != null && !IsLoading;
    public bool CanPrevious => CanRefresh && page > 0;
    public bool CanNext => CanRefresh && page + 1 < totalPages;
    public string PageLabel => $"전체 {totalElements}건 · {(totalPages == 0 ? 0 : page+1)} / {totalPages}페이지 · 최신 방문순";
    public void SetPatient(EncounterRow? row) {
        if (patientId == row?.PatientId) return;
        patientId = row?.PatientId; generation++; detailGeneration++;
        page = 0; totalPages = 0; totalElements = 0;
        Visits.Clear(); SelectedVisit = null; Record = null;
        PatientLabel = row == null ? "" : $"{row.PatientName} · {row.ChartNumber}";
        IsLoading = false; NotifyPage();
        Message = row == null ? "환자를 선택하면 전체 방문 기록을 조회합니다." : "방문 기록을 불러오는 중입니다…";
        if (patientId != null) _ = LoadAsync();
    }
    partial void OnIsLoadingChanged(bool value) => NotifyPage();
    private void NotifyPage() {
        OnPropertyChanged(nameof(CanRefresh)); OnPropertyChanged(nameof(CanPrevious));
        OnPropertyChanged(nameof(CanNext)); OnPropertyChanged(nameof(PageLabel));
    }
    partial void OnSelectedVisitChanged(HistoryVisit? value) {
        Record = null;
        int revision = ++detailGeneration;
        if (value != null) _ = LoadRecordAsync(value.Id,generation,revision);
    }
    private async Task LoadRecordAsync(long id, int expected, int revision) {
        Message = "선택한 진료기록을 불러오는 중입니다…";
        try {
            var result = await api.SendAsync<EncounterRow>(HttpMethod.Get,$"encounters/{id}");
            if (expected != generation || revision != detailGeneration || result.PatientId != patientId) return;
            Record = result;
            Message = "조회 전용 기록입니다. 현재 작성 중인 진료 내용은 유지됩니다.";
        } catch (Exception ex) {
            if (expected == generation && revision == detailGeneration) Message = Error(ex);
        }
    }
    private async Task LoadAsync() {
        if (patientId == null) return;
        int expected = generation;
        IsLoading = true;
        Visits.Clear();
        SelectedVisit = null; Record = null; detailGeneration++;
        try {
            var result = await api.SendAsync<HistoryPage>(HttpMethod.Get,$"patients/{patientId}/encounters?page={page}");
            if (expected != generation) return;
            if (page > 0 && page >= result.TotalPages) { page = Math.Max(0,result.TotalPages-1); await LoadAsync(); return; }
            Visits.Clear(); foreach (var visit in result.Items) Visits.Add(visit);
            totalElements = result.TotalElements; totalPages = result.TotalPages;
            Message = Visits.Count == 0 ? "등록된 방문 기록이 없습니다." : "방문을 선택하면 SOAP·진단·처방을 확인할 수 있습니다. 현재 방문도 포함합니다.";
        } catch (Exception ex) { if (expected == generation) Message = Error(ex); }
        finally { if (expected == generation) { IsLoading = false; NotifyPage(); } }
    }
    private static string Error(Exception ex) => ex is HttpRequestException ? "기록 조회에 실패했습니다. 새로고침으로 다시 시도하세요." : ex.Message;
    [RelayCommand] private Task RefreshAsync() {
        if (!CanRefresh) return Task.CompletedTask;
        page = 0; return LoadAsync();
    }
    [RelayCommand] private Task PreviousAsync() {
        if (!CanPrevious) return Task.CompletedTask;
        page--; return LoadAsync();
    }
    [RelayCommand] private Task NextAsync() {
        if (!CanNext) return Task.CompletedTask;
        page++; return LoadAsync();
    }
}
