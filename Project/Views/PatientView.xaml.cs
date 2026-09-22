using System;
using System.Collections.Generic;
using System.Text;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Data;
using System.Windows.Documents;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Imaging;
using System.Windows.Shapes;

namespace Project.Views
{
    /// <summary>
    /// PatientView.xaml에 대한 상호 작용 논리
    /// </summary>
    public partial class PatientView : UserControl
    {
        public PatientView()
        {
            InitializeComponent();
        }

        private async void OnLoaded(object sender, RoutedEventArgs e)
        {
            if (DataContext is Project.ViewModels.PatientViewModel vm)
                await vm.RefreshCommand.ExecuteAsync(null);
        }

        private void OnBirthDateValidationError(object? sender, DatePickerDateValidationErrorEventArgs e)
        {
            e.ThrowException = false;
            if (DataContext is Project.ViewModels.PatientViewModel vm)
                vm.Message = "생년월일을 YYYY-MM-DD 형식으로 입력하거나 달력에서 선택해 주세요.";
        }

        private async void OnSavePatient(object sender, RoutedEventArgs e)
        {
            if (DataContext is not Project.ViewModels.PatientViewModel vm || vm.IsBusy) return;
            // Commit typed dates as well as calendar selections before executing save.
            var editor = BirthDatePicker.Template.FindName("PART_TextBox", BirthDatePicker) as TextBox;
            string text = (editor?.Text ?? BirthDatePicker.Text).Trim();
            if (!DateTime.TryParse(text, System.Globalization.CultureInfo.CurrentCulture,
                    System.Globalization.DateTimeStyles.None, out var date)
                && !DateTime.TryParseExact(text, new[] { "yyyy-MM-dd", "yyyyMMdd" },
                    System.Globalization.CultureInfo.InvariantCulture, System.Globalization.DateTimeStyles.None, out date))
            {
                vm.Message = "생년월일을 YYYY-MM-DD 형식으로 입력하거나 달력에서 선택해 주세요.";
                return;
            }
            BirthDatePicker.SelectedDate = date.Date;
            vm.BirthDate = date.Date;
            await vm.CreatePatientCommand.ExecuteAsync(null);
        }
    }
}
