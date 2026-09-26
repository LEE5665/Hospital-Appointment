using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using System.Globalization;
using System.Net.Http;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public partial class DashboardViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    [ObservableProperty] private string todayLabel = DateTime.Today.ToString("yyyy.MM.dd · dddd", CultureInfo.GetCultureInfo("ko-KR"));
    [ObservableProperty] private string updatedLabel = "아직 조회하지 않았습니다.";
    [ObservableProperty] private string message = "";
    [ObservableProperty] private string appointmentCount = "—";
    [ObservableProperty] private string waitingCount = "—";
    [ObservableProperty] private string inProgressCount = "—";
    [ObservableProperty] private string completedCount = "—";
    [ObservableProperty] private IReadOnlyList<WaitingPreview> waitingPatients = Array.Empty<WaitingPreview>();
    [ObservableProperty] private IReadOnlyList<BookingPreview> upcomingAppointments = Array.Empty<BookingPreview>();
    [ObservableProperty] private IReadOnlyList<DoctorSummary> doctorSummaries = Array.Empty<DoctorSummary>();
    [ObservableProperty] private bool hasData;
    public event Action<string>? NavigationRequested;
    [RelayCommand] private void Navigate(string destination) => NavigationRequested?.Invoke(destination);

    [RelayCommand] private async Task RefreshAsync()
    {
        Message = "오늘의 현황을 불러오는 중입니다…";
        try
        {
            var now = DateTime.Now;
            string date = now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
            var bookingsTask = api.SendAsync<List<AppointmentRow>>(HttpMethod.Get, "appointments?date=" + date);
            var visitsTask = api.SendAsync<List<EncounterRow>>(HttpMethod.Get, "encounters?date=" + date);
            await Task.WhenAll(bookingsTask, visitsTask);
            var bookings = await bookingsTask;
            var visits = await visitsTask;
            AppointmentCount = bookings.Count(a => a.Status is "BOOKED" or "CHECKED_IN").ToString();
            WaitingCount = visits.Count(e => e.Status == "WAITING").ToString();
            InProgressCount = visits.Count(e => e.Status == "IN_PROGRESS").ToString();
            CompletedCount = visits.Count(e => e.Status == "COMPLETED").ToString();
            WaitingPatients = visits.Where(e => e.Status == "WAITING").OrderBy(e => e.RegisteredAt).Take(6)
                .Select(e => new WaitingPreview(e.PatientName, e.DoctorName, Math.Max(0, (int)(now - e.RegisteredAt).TotalMinutes))).ToList();
            UpcomingAppointments = bookings.Where(a => a.Status == "BOOKED").OrderBy(a => a.ScheduledAt).Take(6)
                .Select(a => new BookingPreview(a.ScheduledAt.ToString("HH:mm"), a.PatientName, a.DoctorName,
                    a.ScheduledAt < now ? "시간 경과" : "방문 예정", a.ScheduledAt < now)).ToList();
            DoctorSummaries = visits.Where(e => e.Status is "WAITING" or "IN_PROGRESS").GroupBy(e => e.DoctorId)
                .Select(g => new DoctorSummary(g.Key == null ? "담당 미지정" : g.First().DoctorName,
                    $"대기 {g.Count(e => e.Status == "WAITING")}명 · 진료 중 {g.Count(e => e.Status == "IN_PROGRESS")}명"))
                .OrderBy(d => d.Name).ToList();
            TodayLabel = now.ToString("yyyy.MM.dd · dddd", CultureInfo.GetCultureInfo("ko-KR"));
            UpdatedLabel = $"마지막 갱신 {DateTime.Now:HH:mm:ss}";
            HasData = true;
            Message = "오늘 예약은 취소 제외 · 대기시간과 예약 상태는 마지막 갱신 기준입니다.";
        }
        catch (Exception ex)
        {
            Message = (ex is HttpRequestException ? "서버 연결에 실패했습니다." : ex.Message)
                + (HasData ? " 이전 조회 결과입니다. 새로고침해 주세요." : " 새로고침으로 다시 시도해 주세요.");
        }
    }
}

public record WaitingPreview(string PatientName, string DoctorName, int Minutes)
{
    public string WaitingTime => $"{Minutes}분";
    public bool IsLongWait => Minutes >= 30;
}
public record BookingPreview(string Time, string PatientName, string DoctorName, string StatusLabel, bool IsOverdue);
public record DoctorSummary(string Name, string Summary);
