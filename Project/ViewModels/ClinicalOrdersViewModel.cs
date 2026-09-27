using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public partial class ClinicalOrdersViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    private EncounterRow? context;
    private int generation;
    private int page;
    private int totalPages;
    private long totalElements;
    private bool loading;
    public bool IsEmbedded { get; }
    public bool IsBoard => !IsEmbedded;
    public bool IsDoctor => AuthService.Instance.CurrentUser?.Role == "DOCTOR";
    private bool IsPerformer => AuthService.Instance.CurrentUser?.Role is "DOCTOR" or "NURSE";
    private long? ActorId => AuthService.Instance.CurrentUser?.Id;
    public ObservableCollection<ClinicalOrderRow> Orders { get; } = new();
    [ObservableProperty] private ClinicalOrderRow? selectedOrder;
    [ObservableProperty] private string requestName = "";
    [ObservableProperty] private int requestTypeIndex;
    [ObservableProperty] private string instructions = "";
    [ObservableProperty] private string resultText = "";
    [ObservableProperty] private string cancellationText = "";
    [ObservableProperty] private DateTime? selectedDate;
    [ObservableProperty] private int typeIndex;
    [ObservableProperty] private int statusIndex;
    [ObservableProperty] private bool mineOnly;
    [ObservableProperty] private bool unreviewedOnly;
    [ObservableProperty] private bool includePatientHistory;
    [ObservableProperty] private bool hasDraft;
    [ObservableProperty] private bool isBusy;
    [ObservableProperty] private string message = "조회 버튼으로 요청과 결과를 확인하세요.";
    public string PageLabel => $"총 {totalElements}건 · {(totalPages == 0 ? 0 : page+1)} / {totalPages}페이지";
    public string ContextLabel => context == null ? "진료에서 환자를 선택하세요." : $"{context.PatientName} · {context.ChartNumber}";
    public bool IsIdle => !IsBusy;
    public bool CanSelect => !HasDraft && !IsBusy;
    public bool CanCreate => IsEmbedded && IsDoctor && context is { Status: "IN_PROGRESS" } e && e.DoctorId == ActorId && !IncludePatientHistory;
    public bool CanStart => IsPerformer && SelectedOrder?.Status == "REQUESTED";
    public bool CanComplete => IsPerformer && SelectedOrder is { Status: "IN_PROGRESS" } o && o.PerformedById == ActorId;
    public bool IsResultReadOnly => !CanComplete;
    public bool CanCancel => IsDoctor && SelectedOrder is { Status: "REQUESTED" } o && o.RequestedById == ActorId;
    public bool CanReview => IsDoctor && SelectedOrder is { Status: "COMPLETED", ReviewedAt: null } o && o.RequestedById == ActorId;
    public bool CanPrevious => page > 0 && CanSelect;
    public bool CanNext => page + 1 < totalPages && CanSelect;
    public event Action<long>? OpenEncounterRequested;

    public ClinicalOrdersViewModel() : this(false) {}
    public ClinicalOrdersViewModel(bool embedded) {
        IsEmbedded = embedded;
        StatusIndex = embedded ? 0 : 1;
    }
    public void SetEncounter(EncounterRow? row) {
        if (context?.Id == row?.Id) { context = row; NotifyActions(); return; }
        context = row; generation++; page = 0; IsBusy = false;
        loading = true;
        SelectedOrder = null; RequestName = ""; Instructions = ResultText = CancellationText = "";
        IncludePatientHistory = false; Orders.Clear();
        totalElements = 0; totalPages = 0;
        loading = false; UpdateDraft(); NotifyActions();
        OnPropertyChanged(nameof(ContextLabel)); OnPropertyChanged(nameof(PageLabel));
        if (row != null) _ = LoadContextAsync(generation);
    }
    private async Task LoadContextAsync(int expected) {
        IsBusy = true;
        try { await Load(expected); }
        catch (Exception ex) { if (expected == generation) Message = Error(ex); }
        finally { if (expected == generation) IsBusy = false; }
    }
    partial void OnSelectedOrderChanged(ClinicalOrderRow? value) {
        loading = true; ResultText = value?.Result ?? ""; CancellationText = ""; loading = false;
        UpdateDraft(); NotifyActions();
    }
    partial void OnRequestNameChanged(string value) => UpdateDraft();
    partial void OnInstructionsChanged(string value) => UpdateDraft();
    partial void OnResultTextChanged(string value) => UpdateDraft();
    partial void OnCancellationTextChanged(string value) => UpdateDraft();
    partial void OnHasDraftChanged(bool value) => NotifyActions();
    partial void OnIsBusyChanged(bool value) => NotifyActions();
    partial void OnIncludePatientHistoryChanged(bool value) { NotifyActions(); }
    partial void OnUnreviewedOnlyChanged(bool value) { if (value) StatusIndex = 4; }
    private void UpdateDraft() {
        if (!loading) HasDraft = !string.IsNullOrWhiteSpace(RequestName) || !string.IsNullOrWhiteSpace(Instructions)
            || ResultText != (SelectedOrder?.Result ?? "") || !string.IsNullOrWhiteSpace(CancellationText);
    }
    private void NotifyActions() {
        foreach (var name in new[] { nameof(IsIdle),nameof(CanSelect),nameof(CanCreate),nameof(CanStart),nameof(CanComplete),
            nameof(IsResultReadOnly),nameof(CanCancel),nameof(CanReview),nameof(CanPrevious),nameof(CanNext) }) OnPropertyChanged(name);
    }
    private static string Error(Exception ex) => ex is HttpRequestException ? "서버 연결에 실패했습니다. 입력 내용은 유지됩니다." : ex.Message;
    private async Task Run(Func<Task> action) {
        if (IsBusy) return;
        IsBusy = true;
        try { await action(); } catch (Exception ex) { Message = Error(ex); } finally { IsBusy = false; }
    }
    private async Task Load(int expected, long? selectId = null) {
        if (IsEmbedded && context == null) return;
        string status = StatusIndex switch { 1 => "ACTIVE",2 => "REQUESTED",3 => "IN_PROGRESS",4 => "COMPLETED",5 => "CANCELLED",_ => "ALL" };
        string path = $"orders?status={status}&page={page}&mine={(MineOnly ? "true" : "false")}&unreviewed={(UnreviewedOnly ? "true" : "false")}";
        if (TypeIndex != 0) path += "&type="+(TypeIndex == 1 ? "TEST" : "PROCEDURE");
        if (IsEmbedded) path += IncludePatientHistory ? $"&patientId={context!.PatientId}" : $"&encounterId={context!.Id}";
        else if (SelectedDate is { } date) path += "&date="+date.ToString("yyyy-MM-dd",CultureInfo.InvariantCulture);
        var result = await api.SendAsync<ClinicalOrderPage>(HttpMethod.Get,path);
        if (expected != generation) return;
        if (page > 0 && page >= result.TotalPages) { page = Math.Max(0,result.TotalPages-1); await Load(expected,selectId); return; }
        Orders.Clear(); foreach (var row in result.Items) Orders.Add(row);
        SelectedOrder = Orders.FirstOrDefault(o => o.Id == selectId);
        totalElements = result.TotalElements; totalPages = result.TotalPages;
        OnPropertyChanged(nameof(PageLabel)); NotifyActions();
        Message = Orders.Count == 0 ? "해당 조건의 요청이 없습니다." : "최신 요청순으로 조회했습니다. 항목을 선택하면 결과와 수행 이력을 볼 수 있습니다.";
    }
    [RelayCommand] private Task RefreshAsync() => Run(async () => {
        if (HasDraft) { Message = "작성 내용을 등록·완료하거나 입력 취소 후 조회하세요."; return; }
        page = 0; await Load(generation,SelectedOrder?.Id);
    });
    [RelayCommand] private Task PreviousAsync() => Run(async () => { if (HasDraft || page == 0) return; page--; await Load(generation); });
    [RelayCommand] private Task NextAsync() => Run(async () => { if (HasDraft || page+1 >= totalPages) return; page++; await Load(generation); });
    [RelayCommand] private void DiscardDraft() {
        RequestName = ""; Instructions = ""; ResultText = SelectedOrder?.Result ?? ""; CancellationText = ""; UpdateDraft();
        Message = "입력 내용을 취소했습니다.";
    }
    [RelayCommand] private Task CreateAsync() => Run(async () => {
        if (!CanCreate || context == null || string.IsNullOrWhiteSpace(RequestName)) { Message = "진행 중인 진료에서 검사·처치명을 입력하세요."; return; }
        if (ResultText != (SelectedOrder?.Result ?? "") || !string.IsNullOrWhiteSpace(CancellationText)) {
            Message = "작성 중인 결과·취소 사유를 먼저 처리하세요."; return;
        }
        var row = await api.SendAsync<ClinicalOrderRow>(HttpMethod.Post,"orders",new {
            encounterId = context.Id, itemName = RequestName.Trim(), type = RequestTypeIndex == 0 ? "TEST" : "PROCEDURE", instructions = Instructions });
        RequestName = ""; Instructions = ""; UpdateDraft();
        page = 0; StatusIndex = 0; TypeIndex = 0; UnreviewedOnly = false;
        await Load(generation,row.Id);
        Message = "요청을 등록했습니다. SOAP와 처방 내용은 변경하지 않았습니다.";
    });
    private Task Change(string action, string text) => Run(async () => {
        if (SelectedOrder is not { } selected) return;
        if (!string.IsNullOrWhiteSpace(RequestName) || !string.IsNullOrWhiteSpace(Instructions)) { Message = "새 요청을 등록하거나 입력 취소 후 처리하세요."; return; }
        if (action != "cancel" && !string.IsNullOrWhiteSpace(CancellationText)) { Message = "작성 중인 취소 사유를 처리하거나 입력 취소를 눌러 주세요."; return; }
        if (action != "complete" && ResultText != (SelectedOrder?.Result ?? "")) { Message = "작성 중인 결과를 먼저 저장하세요."; return; }
        if (action == "complete" && string.IsNullOrWhiteSpace(text)) { Message = "검사 결과 또는 처치 수행 내용을 입력하세요."; return; }
        if (action == "cancel" && string.IsNullOrWhiteSpace(text)) { Message = "취소 사유를 입력하세요."; return; }
        var row = await api.SendAsync<ClinicalOrderRow>(HttpMethod.Post,$"orders/{selected.Id}/{action}",new { version = selected.Version,text });
        SelectedOrder = row; UpdateDraft();
        await Load(generation,row.Id);
        Message = action switch { "start" => "수행을 시작했습니다.","complete" => "결과와 수행 내용을 저장했습니다.","cancel" => "요청을 취소했습니다.",_ => "결과 확인을 기록했습니다." };
    });
    [RelayCommand] private Task StartAsync() => CanStart ? Change("start","") : Task.CompletedTask;
    [RelayCommand] private Task CompleteAsync() => CanComplete ? Change("complete",ResultText) : Task.CompletedTask;
    [RelayCommand] private Task CancelAsync() => CanCancel ? Change("cancel",CancellationText) : Task.CompletedTask;
    [RelayCommand] private Task ReviewAsync() => CanReview ? Change("review","") : Task.CompletedTask;
    [RelayCommand] private void OpenEncounter() {
        if (HasDraft) { Message = "작성 중인 내용을 먼저 처리하세요."; return; }
        if (SelectedOrder is { } row) OpenEncounterRequested?.Invoke(row.EncounterId);
    }
}
