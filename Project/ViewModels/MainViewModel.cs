using System.Threading.Tasks;
using System.Windows;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Services;
using Project.Views;

namespace Project.ViewModels
{
    public partial class MainViewModel : ObservableObject
    {
        private readonly DashboardViewModel dashboardViewModel = new();
        private readonly PatientViewModel patientViewModel = new();
        private readonly AppointmentViewModel appointmentViewModel = new();

        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(CurrentPageTitle))]
        private ObservableObject currentViewModel;

        public MainViewModel()
        {
            currentViewModel = patientViewModel;
            ShowDashboardCommand = new RelayCommand(() => CurrentViewModel = dashboardViewModel);
            ShowPatientsCommand = new RelayCommand(() => CurrentViewModel = patientViewModel);
            ShowAppointmentsCommand = new RelayCommand(() => CurrentViewModel = appointmentViewModel);
            LogoutCommand = new AsyncRelayCommand(LogoutAsync);
        }

        public string CurrentPageTitle => CurrentViewModel is AppointmentViewModel ? "예약 관리" : CurrentViewModel is PatientViewModel ? "환자 관리 · 외래 접수" : "대시보드";

        public string CurrentUserInfo
        {
            get
            {
                var user = AuthService.Instance.CurrentUser;
                if (user == null) return string.Empty;
                return $"{user.Name} ({user.RoleDescription ?? user.Role} · {user.Department})";
            }
        }

        public IRelayCommand ShowDashboardCommand { get; }
        public IRelayCommand ShowPatientsCommand { get; }
        public IRelayCommand ShowAppointmentsCommand { get; }
        public IAsyncRelayCommand LogoutCommand { get; }

        private async Task LogoutAsync()
        {
            await AuthService.Instance.LogoutAsync();

            Application.Current.ShutdownMode = ShutdownMode.OnExplicitShutdown;

            var currentMainWindow = Application.Current.MainWindow;
            var loginWindow = new LoginWindow();

            Application.Current.MainWindow = loginWindow;
            currentMainWindow?.Close();

            bool? result = loginWindow.ShowDialog();
            if (result == true)
            {
                var newMainWindow = new MainWindow();
                Application.Current.MainWindow = newMainWindow;
                Application.Current.ShutdownMode = ShutdownMode.OnMainWindowClose;
                newMainWindow.Show();
            }
            else
            {
                Application.Current.Shutdown();
            }
        }
    }
}
