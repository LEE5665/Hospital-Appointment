using System.Windows;
using System.Windows.Controls;
using Project.ViewModels;
namespace Project.Views;
public partial class AppointmentView : UserControl
{
    public AppointmentView() { InitializeComponent(); }
    private async void OnLoaded(object sender, RoutedEventArgs e) {
        if (DataContext is AppointmentViewModel vm) await vm.RefreshCommand.ExecuteAsync(null);
    }
}
