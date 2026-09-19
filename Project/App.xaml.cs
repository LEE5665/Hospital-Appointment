using System.Windows;
using Project.Models;
using Project.Services;
using Project.Views;

namespace Project
{
    public partial class App : Application
    {
        public static MemberResponse? CurrentUser => AuthService.Instance.CurrentUser;

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);

            // WPF 기본 ShutdownMode(OnLastWindowClose)로 인해
            // LoginWindow가 닫히는 순간 앱이 종료되는 것을 방지
            ShutdownMode = ShutdownMode.OnExplicitShutdown;

            var loginWindow = new LoginWindow();
            bool? result = loginWindow.ShowDialog();

            if (result == true)
            {
                var mainWindow = new MainWindow();
                MainWindow = mainWindow;
                // 이제 메인 창이 닫힐 때 앱이 정상 종료되도록 설정
                ShutdownMode = ShutdownMode.OnMainWindowClose;
                mainWindow.Show();
            }
            else
            {
                Shutdown();
            }
        }
    }
}
