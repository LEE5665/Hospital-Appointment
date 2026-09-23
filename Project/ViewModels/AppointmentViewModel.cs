using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public partial class AppointmentViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    public ObservableCollection<AppointmentRow> Appointments { get; } = new();
    public ObservableCollection<PatientRow> Patients { get; } = new();
    public ObservableCollection<DoctorRow> Doctors { get; } = new();
    public IReadOnlyList<string> Times { get; } = Enumerable.Range(0, 48).Select(i => $"{i / 2:00}:{i % 2 * 30:00}").ToList();
    [ObservableProperty] private DateTime? listDate = DateTime.Today;
    [ObservableProperty] private DateTime? bookingDate = DateTime.Today.AddDays(3);
    [ObservableProperty] private string bookingTime = "09:00";
    [ObservableProperty] private string query = "";
    [ObservableProperty] private string reason = "";
    [ObservableProperty] private string message = "예약은 방문 예정 정보입니다. 예약 당일 방문 접수로 전환해 주세요.";
    [ObservableProperty] private PatientRow? selectedPatient;
    [ObservableProperty] private DoctorRow? selectedDoctor;
    [ObservableProperty] private AppointmentRow? selectedAppointment;
    private AppointmentRow? editingAppointment;
    public string EditorTitle => editingAppointment == null ? "신규 예약" : "예약 상세 · 수정";
    public string SaveLabel => editingAppointment == null ? "예약 등록" : "예약 변경 저장";
    public string EditorPatientName => editingAppointment?.PatientName ?? SelectedPatient?.Name ?? "예약할 환자를 선택하세요";
    public bool CanChoosePatient => editingAppointment == null;
    public bool CanSaveBooking => editingAppointment == null || editingAppointment.Status == "BOOKED";
    partial void OnSelectedPatientChanged(PatientRow? value) => OnPropertyChanged(nameof(EditorPatientName));
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(IsIdle))] private bool isBusy;
    public bool IsIdle => !IsBusy;
    public bool CanCancel => SelectedAppointment?.Status == "BOOKED";
    public bool CanCheckIn => CanCancel && SelectedAppointment?.ScheduledAt.Date == DateTime.Today;
    partial void OnSelectedAppointmentChanged(AppointmentRow? value) {
        editingAppointment = value;
        if (value != null) {
            BookingDate = value.ScheduledAt.Date;
            BookingTime = value.ScheduledAt.ToString("HH:mm", CultureInfo.InvariantCulture);
            SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == value.DoctorId);
            if (SelectedDoctor == null && value.DoctorId != null) {
                SelectedDoctor = new DoctorRow(value.DoctorId, value.DoctorName, "현재 예약 의사");
                Doctors.Add(SelectedDoctor);
            }
            Reason = value.Reason ?? "";
        }
        OnPropertyChanged(nameof(CanCancel)); OnPropertyChanged(nameof(CanCheckIn));
        OnPropertyChanged(nameof(EditorTitle)); OnPropertyChanged(nameof(SaveLabel));
        OnPropertyChanged(nameof(EditorPatientName)); OnPropertyChanged(nameof(CanChoosePatient));
        OnPropertyChanged(nameof(CanSaveBooking));
    }
    [RelayCommand] private void NewBooking() {
        SelectedAppointment = null;
        SelectedPatient = null;
        BookingDate = DateTime.Today.AddDays(1); BookingTime = "09:00"; Reason = "";
        SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == null);
        Message = "환자를 선택한 후 신규 예약을 등록해 주세요.";
    }
    private async Task Run(Func<Task> action) {
        if (IsBusy) return;
        IsBusy = true;
        try { await action(); }
        catch (Exception ex) { Message = ex is HttpRequestException ? "서버 연결에 실패했습니다." : ex.Message; }
        finally { IsBusy = false; }
    }
    private async Task LoadAppointments(long? selectedId = null) {
        if (ListDate == null) throw new InvalidOperationException("조회 날짜를 선택해 주세요.");
        var rows = await api.SendAsync<List<AppointmentRow>>(HttpMethod.Get, "appointments?date=" + ListDate.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture));
        Appointments.Clear(); foreach (var row in rows) Appointments.Add(row);
        SelectedAppointment = Appointments.FirstOrDefault(a => a.Id == selectedId);
    }
    private async Task LoadPatients() {
        var id = SelectedPatient?.Id;
        var rows = await api.SendAsync<List<PatientRow>>(HttpMethod.Get, "patients?query=" + Uri.EscapeDataString(Query));
        Patients.Clear(); foreach (var row in rows) Patients.Add(row);
        SelectedPatient = Patients.FirstOrDefault(p => p.Id == id);
    }
    [RelayCommand] private Task RefreshAsync() => Run(async () => {
        var id = SelectedDoctor?.Id;
        var rows = await api.SendAsync<List<DoctorRow>>(HttpMethod.Get, "doctors");
        Doctors.Clear(); Doctors.Add(new DoctorRow(null, "미지정", null));
        foreach (var row in rows) Doctors.Add(row);
        SelectedDoctor = Doctors.FirstOrDefault(d => d.Id == id) ?? Doctors[0];
        await LoadPatients(); await LoadAppointments(SelectedAppointment?.Id);
        Message = $"{ListDate:yyyy-MM-dd} 예약 {Appointments.Count}건";
    });
    [RelayCommand] private Task SearchAsync() => Run(LoadPatients);
    [RelayCommand] private Task LoadDateAsync() => Run(async () => {
        await LoadAppointments(); Message = $"{ListDate:yyyy-MM-dd} 예약 {Appointments.Count}건";
    });
    [RelayCommand] private Task BookAsync() => Run(async () => {
        var editing = editingAppointment;
        if (!CanSaveBooking) throw new InvalidOperationException("접수 완료 또는 취소된 예약은 수정할 수 없습니다.");
        if ((editing == null && SelectedPatient == null) || BookingDate == null) throw new InvalidOperationException("환자와 예약 날짜를 선택해 주세요.");
        if (!TimeSpan.TryParseExact(BookingTime, @"hh\:mm", CultureInfo.InvariantCulture, out var time)) throw new InvalidOperationException("예약 시간을 선택해 주세요.");
        var scheduled = BookingDate.Value.Date.Add(time);
        if (scheduled <= DateTime.Now) throw new InvalidOperationException("현재 이후의 날짜와 시간을 선택해 주세요.");
        object payload = editing == null
            ? new { patientId = SelectedPatient!.Id, doctorId = SelectedDoctor?.Id,
                scheduledAt = scheduled.ToString("yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture), reason = Reason }
            : new { doctorId = SelectedDoctor?.Id, scheduledAt = scheduled.ToString("yyyy-MM-dd'T'HH:mm:ss", CultureInfo.InvariantCulture),
                reason = Reason, version = editing.Version };
        var row = await api.SendAsync<AppointmentRow>(editing == null ? HttpMethod.Post : HttpMethod.Put,
            editing == null ? "appointments" : $"appointments/{editing.Id}", payload);
        ListDate = row.ScheduledAt.Date;
        // Show the committed response even if a later list refresh fails.
        var old = Appointments.FirstOrDefault(a => a.Id == row.Id);
        if (old != null) Appointments.Remove(old);
        Appointments.Add(row); SelectedAppointment = row;
        string success = $"{row.PatientName} 환자의 {row.ScheduledAt:yyyy-MM-dd HH:mm} 예약이 " + (editing == null ? "등록되었습니다." : "변경되었습니다.");
        try { await LoadAppointments(row.Id); Message = success; }
        catch { Message = success + " 목록 갱신에 실패했습니다. 다시 조회해 주세요."; }
    });
    [RelayCommand] private Task CancelAsync() => Run(async () => {
        if (!CanCancel || SelectedAppointment == null) return;
        var row = await api.SendAsync<AppointmentRow>(HttpMethod.Post, $"appointments/{SelectedAppointment.Id}/cancel");
        await LoadAppointments(row.Id); Message = "예약이 취소되었습니다.";
    });
    [RelayCommand] private Task CheckInAsync() => Run(async () => {
        if (!CanCheckIn || SelectedAppointment == null) return;
        var row = await api.SendAsync<AppointmentRow>(HttpMethod.Post, $"appointments/{SelectedAppointment.Id}/check-in");
        await LoadAppointments(row.Id); Message = "방문 접수되었습니다. 진료 관리에서 대기 환자를 확인해 주세요.";
    });
}
