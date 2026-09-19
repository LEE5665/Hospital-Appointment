using System.Windows;
using System.Windows.Input;
using Project.Services;

namespace Project.Views
{
    public partial class LoginWindow : Window
    {
        public LoginWindow()
        {
            InitializeComponent();
        }

        private async void OnLoginClick(object sender, RoutedEventArgs e)
        {
            await AttemptLoginAsync();
        }

        private async void OnKeyDown(object sender, KeyEventArgs e)
        {
            if (e.Key == Key.Enter)
            {
                await AttemptLoginAsync();
            }
        }

        private async Task AttemptLoginAsync()
        {
            string email = EmailBox.Text.Trim();
            string password = PasswordBox.Password;

            if (string.IsNullOrWhiteSpace(email))
            {
                ShowError("이메일을 입력해 주세요.");
                EmailBox.Focus();
                return;
            }

            if (string.IsNullOrWhiteSpace(password))
            {
                ShowError("비밀번호를 입력해 주세요.");
                PasswordBox.Focus();
                return;
            }

            SetLoading(true);

            var (success, errorMessage) = await AuthService.Instance.LoginAsync(email, password);

            SetLoading(false);

            if (success)
            {
                DialogResult = true;
            }
            else
            {
                ShowError(errorMessage ?? "로그인에 실패했습니다.");
            }
        }

        private void ShowError(string message)
        {
            ErrorTextBlock.Text = message;
            ErrorTextBlock.Visibility = Visibility.Visible;
        }

        private void SetLoading(bool isLoading)
        {
            LoginButton.IsEnabled = !isLoading;
            LoginButton.Content = isLoading ? "로그인 중..." : "로그인";
            if (isLoading)
            {
                ErrorTextBlock.Visibility = Visibility.Collapsed;
            }
        }
    }
}
