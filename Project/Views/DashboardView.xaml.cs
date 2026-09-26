using System.Windows.Controls;
using System.Windows;
using Project.ViewModels;

namespace Project.Views;

public partial class DashboardView : UserControl
{
    public DashboardView()
    {
        InitializeComponent();
    }
    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (DataContext is DashboardViewModel vm && !vm.RefreshCommand.IsRunning)
            await vm.RefreshCommand.ExecuteAsync(null);
    }
}
