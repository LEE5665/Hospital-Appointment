using System.Collections.ObjectModel;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;
namespace Project.ViewModels;
public partial class PatientViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    public ObservableCollection<PatientRow> Patients { get; } = new();
    public ObservableCollection<DoctorRow> Doctors { get; } = new();
    public ObservableCollection<EncounterRow> Encounters { get; } = new();
    public IReadOnlyList<string> BookingTimes { get; } = Enumerable.Range(0, 48).Select(i => $"{i / 2:00}:{i % 2 * 30:00}").ToList();
    [ObservableProperty] private DateTime? futureBookingDate = DateTime.Today.AddDays(1);
    [ObservableProperty] private string futureBookingTime = "09:00";
    [ObservableProperty] private DoctorRow? futureBookingDoctor;
    [ObservableProperty] private string futureBookingReason = "";
    [RelayCommand] private Task BookAppointmentAsync() => Run(async () => {
        if (SelectedPatient == null || FutureBookingDate == null) throw new InvalidOperationException("등록된 환자와 예약 날짜를 선택해 주세요.");
        if (!TimeSpan.TryParseExact(FutureBookingTime, @"hh\:mm", System.Globalization.CultureInfo.InvariantCulture, out var time))
            throw new InvalidOperationException("예약 시간을 선택해 주세요.");
        var scheduled = FutureBookingDate.Value.Date.Add(time);
        if (scheduled <= DateTime.Now) throw new InvalidOperationException("현재 이후의 날짜와 시간을 선택해 주세요.");
        var row = await api.SendAsync<AppointmentRow>(HttpMethod.Post, "appointments", new {
            patientId = SelectedPatient.Id, doctorId = FutureBookingDoctor?.Id,
            scheduledAt = scheduled.ToString("yyyy-MM-dd'T'HH:mm:ss", System.Globalization.CultureInfo.InvariantCulture), reason = FutureBookingReason
        });
        FutureBookingReason = "";
        Message = $"{row.PatientName} 환자의 {row.ScheduledAt:yyyy-MM-dd HH:mm} 예약이 등록되었습니다. 예약 관리에서 변경할 수 있습니다.";
        await LoadTodayAppointmentsAsync();
    });
    public ObservableCollection<AppointmentRow> TodayAppointments { get; } = new();
    [ObservableProperty] private AppointmentRow? selectedAppointment;
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(CanRegister))] private bool appointmentsLoading;
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(CanRegister))] private bool appointmentsReady;
    [ObservableProperty] private string appointmentMessage = "환자를 선택하면 오늘 예약을 확인합니다.";
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(CanOpenRegisteredVisit))] private EncounterRow? registeredVisit;
    private long appointmentRequest;
    public bool HasAppointments => TodayAppointments.Count > 0;
    public bool CanEditReception => !HasAppointments && AppointmentsReady && RegisteredVisit == null;
    public bool CanRegister => HasPatient && AppointmentsReady && !AppointmentsLoading && (!HasAppointments || SelectedAppointment != null) && RegisteredVisit == null;
    public bool CanOpenRegisteredVisit => RegisteredVisit != null;
    public string RegisterLabel => HasAppointments ? "선택 예약으로 접수 → 진료 대기" : "바로 접수 → 진료 대기";
    public event Action<EncounterRow>? OpenEncounterRequested;
    [RelayCommand] private void OpenRegisteredVisit() {
        if (RegisteredVisit != null) OpenEncounterRequested?.Invoke(RegisteredVisit);
    }
    partial void OnRegisteredVisitChanged(EncounterRow? value) => NotifyReception();
    partial void OnSelectedAppointmentChanged(AppointmentRow? value) {
        if (value != null) {
            SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == value.DoctorId);
            Reason = value.Reason ?? "";
        }
        OnPropertyChanged(nameof(CanRegister));
    }
    private void NotifyReception() {
        OnPropertyChanged(nameof(HasAppointments)); OnPropertyChanged(nameof(CanEditReception));
        OnPropertyChanged(nameof(CanRegister)); OnPropertyChanged(nameof(RegisterLabel));
    }
    [RelayCommand] private Task ReloadAppointmentsAsync() => LoadTodayAppointmentsAsync();
    private async Task LoadTodayAppointmentsAsync() {
        long request = ++appointmentRequest;
        var patientId = SelectedPatient?.Id;
        TodayAppointments.Clear(); SelectedAppointment = null; AppointmentsReady = false;
        AppointmentsLoading = patientId != null;
        AppointmentMessage = patientId == null ? "환자를 선택하면 오늘 예약을 확인합니다." : "오늘 예약 확인 중…";
        NotifyReception();
        if (patientId == null) return;
        try {
            var rows = await api.SendAsync<List<AppointmentRow>>(HttpMethod.Get, $"appointments/patient/{patientId}/today");
            if (request != appointmentRequest) return;
            foreach (var row in rows) TodayAppointments.Add(row);
            SelectedAppointment = TodayAppointments.Count == 1 ? TodayAppointments[0] : null;
            AppointmentsReady = true;
            AppointmentMessage = rows.Count == 0 ? "오늘 예약 없음 · 바로 접수할 수 있습니다." : $"오늘 예약 {rows.Count}건 · 연결할 예약을 확인해 주세요.";
        } catch (Exception ex) {
            if (request == appointmentRequest) AppointmentMessage = "예약 확인 실패 · 다시 확인해 주세요. " + ex.Message;
        } finally {
            if (request == appointmentRequest) { AppointmentsLoading = false; NotifyReception(); }
        }
    }
    [ObservableProperty] private string query = "";
    [ObservableProperty] private string patientName = "";
    [ObservableProperty] private DateTime? birthDate;
    [ObservableProperty] private int genderIndex;
    [ObservableProperty] private string phone = "";
    [ObservableProperty] private string address = "";
    [ObservableProperty] private string allergies = "";
    [ObservableProperty] private string medicalHistory = "";
    [ObservableProperty] private string memo = "";
    public string DetailTitle => SelectedPatient == null ? "신규 환자 등록" : "환자 상세 정보";
    public string SaveLabel => SelectedPatient == null ? "환자 등록" : "변경사항 저장";
    public bool HasPatient => SelectedPatient != null;
    partial void OnSelectedPatientChanged(PatientRow? value) {
        PatientName = value?.Name ?? "";
        BirthDate = value?.BirthDate;
        GenderIndex = value?.Gender == "FEMALE" ? 1 : 0;
        Phone = value?.Phone ?? "";
        Address = value?.Address ?? "";
        Allergies = value?.Allergies ?? "";
        MedicalHistory = value?.MedicalHistory ?? "";
        Memo = value?.Memo ?? "";
        Reason = "";
        RegisteredVisit = null;
        FutureBookingReason = "";
        FutureBookingDoctor = Doctors.FirstOrDefault(d => d.Id == null);
        SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == null);
        OnPropertyChanged(nameof(DetailTitle));
        OnPropertyChanged(nameof(SaveLabel));
        OnPropertyChanged(nameof(HasPatient));
        _ = LoadTodayAppointmentsAsync();
    }
    [RelayCommand] private void NewPatient() {
        SelectedPatient = null;
        OnSelectedPatientChanged(null);
        Message = "새 환자의 기본정보와 참고사항을 입력해 주세요.";
    }
    [ObservableProperty] private string reason = "";
    [ObservableProperty] private PatientRow? selectedPatient;
    [ObservableProperty] private DoctorRow? selectedDoctor;
    [ObservableProperty] private EncounterRow? selectedEncounter;
    [ObservableProperty] private string note = "";
    [ObservableProperty] private DateTime selectedDate = DateTime.Today;
    [ObservableProperty] private string message = "환자를 등록하거나 검색한 후 접수해 주세요.";
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(IsIdle))] private bool isBusy;
    public bool IsIdle => !IsBusy;
    public bool CanStart => AuthService.Instance.CurrentUser?.Role == "DOCTOR" && SelectedEncounter is { Status: "WAITING" } e && (e.DoctorId == null || e.DoctorId == AuthService.Instance.CurrentUser?.Id);
    public bool CanWrite => AuthService.Instance.CurrentUser?.Role == "DOCTOR" && SelectedEncounter is { Status: "IN_PROGRESS" } e && e.DoctorId == AuthService.Instance.CurrentUser?.Id;
    partial void OnSelectedEncounterChanged(EncounterRow? value) {
        Note = value?.Note ?? "";
        OnPropertyChanged(nameof(CanStart)); OnPropertyChanged(nameof(CanWrite));
    }
    private async Task Run(Func<Task> action) {
        if (IsBusy) return;
        IsBusy = true;
        try { await action(); }
        catch (Exception ex) { Message = ex is HttpRequestException ? "서버 연결 실패: 백엔드 실행 상태를 확인해 주세요." : ex.Message; }
        finally { IsBusy = false; }
    }
    private async Task LoadPatients() {
        var selectedId = SelectedPatient?.Id;
        var rows = await api.SendAsync<List<PatientRow>>(HttpMethod.Get, "patients?query=" + Uri.EscapeDataString(Query));
        Patients.Clear(); foreach (var row in rows) Patients.Add(row);
        SelectedPatient = Patients.FirstOrDefault(p => p.Id == selectedId);
    }
    private async Task LoadEncounters(long? id = null) {
        var rows = await api.SendAsync<List<EncounterRow>>(HttpMethod.Get, $"encounters?date={SelectedDate:yyyy-MM-dd}");
        Encounters.Clear(); foreach (var row in rows) Encounters.Add(row);
        SelectedEncounter = Encounters.FirstOrDefault(e => e.Id == id);
    }
    public Task OpenEncounterAsync(long id) => Run(async () => {
        SelectedDate = DateTime.Today;
        await LoadEncounters(id);
        Message = "접수된 환자를 선택했습니다. 담당 의사는 진료를 시작할 수 있습니다.";
    });
    [RelayCommand] private Task RefreshAsync() => Run(async () => {
        var id = SelectedEncounter?.Id;
        var doctors = await api.SendAsync<List<DoctorRow>>(HttpMethod.Get, "doctors");
        var doctorId = SelectedDoctor?.Id;
        Doctors.Clear(); Doctors.Add(new DoctorRow(null, "미지정 · 진료 시작 시 배정", null));
        foreach (var doctor in doctors) Doctors.Add(doctor);
        SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == doctorId) ?? Doctors[0];
        FutureBookingDoctor = Doctors.FirstOrDefault(d => d.Id == FutureBookingDoctor?.Id) ?? Doctors[0];
        await LoadPatients();
        Message = $"환자 검색 결과 {Patients.Count}명 · 최대 100명 · 진료 대기는 진료 관리에서 확인하세요.";
    });
    [RelayCommand] private Task SearchAsync() => Run(LoadPatients);
    [RelayCommand] private Task CreatePatientAsync() => Run(async () => {
        if (string.IsNullOrWhiteSpace(PatientName) || BirthDate == null || string.IsNullOrWhiteSpace(Phone))
            throw new InvalidOperationException("이름, 생년월일, 연락처를 입력해 주세요.");
        if (BirthDate.Value.Date > DateTime.Today) throw new InvalidOperationException("생년월일은 오늘 이후일 수 없습니다.");
        bool updating = SelectedPatient != null;
        var patient = await api.SendAsync<PatientRow>(updating ? HttpMethod.Put : HttpMethod.Post,
            updating ? $"patients/{SelectedPatient!.Id}" : "patients",
            new { name = PatientName, birthDate = BirthDate.Value.ToString("yyyy-MM-dd"), gender = GenderIndex == 0 ? "MALE" : "FEMALE", phone = Phone, address = Address, allergies = Allergies, medicalHistory = MedicalHistory, memo = Memo });
        // Use the saved response directly: a failed follow-up search must not turn
        // a successful registration into an apparent failure or trigger duplicates.
        var existing = Patients.FirstOrDefault(p => p.Id == patient.Id);
        if (existing == null) Patients.Insert(0, patient);
        else Patients[Patients.IndexOf(existing)] = patient;
        SelectedPatient = patient;
        OnSelectedPatientChanged(patient);
        Message = updating ? "환자 정보가 저장되었습니다." : "환자가 등록되었습니다.";
    });
    [RelayCommand] private Task RegisterAsync() => Run(async () => {
        if (SelectedPatient == null) throw new InvalidOperationException("접수할 환자를 선택해 주세요.");
        if (!CanRegister) throw new InvalidOperationException("오늘 예약 확인을 마친 후 연결할 예약을 선택해 주세요.");
        EncounterRow row;
        try {
            row = await api.SendAsync<EncounterRow>(HttpMethod.Post, "encounters", new { patientId = SelectedPatient.Id,
                doctorId = SelectedDoctor?.Id, reason = Reason, appointmentId = SelectedAppointment?.Id });
        } catch {
            await LoadTodayAppointmentsAsync();
            throw;
        }
        RegisteredVisit = row;
        Message = $"{row.PatientName} 환자가 진료 대기에 등록되었습니다. 담당: {row.DoctorName}";
        await LoadTodayAppointmentsAsync();
    });
    [RelayCommand] private Task StartAsync() => Run(async () => {
        if (!CanStart || SelectedEncounter == null) return;
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Post, $"encounters/{SelectedEncounter.Id}/start");
        await LoadEncounters(row.Id); Message = "담당 의사가 확정되었습니다. 진료기록을 작성해 주세요.";
    });
    [RelayCommand] private Task SaveNoteAsync() => Save(false);
    [RelayCommand] private Task CompleteAsync() => Save(true);
    private Task Save(bool complete) => Run(async () => {
        if (!CanWrite || SelectedEncounter == null) return;
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Put, $"encounters/{SelectedEncounter.Id}/note", new { content = Note, complete, version = SelectedEncounter.Version });
        await LoadEncounters(row.Id); Message = complete ? "진료가 완료되었습니다. 완료된 기록은 조회만 가능합니다." : "진료기록이 저장되었습니다.";
    });
}
