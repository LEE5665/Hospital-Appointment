using System.Windows;
using System.Windows.Controls;
using Project.ViewModels;

namespace Project.Views;

public partial class ClinicalOrdersView : UserControl
{
    public ClinicalOrdersView() { InitializeComponent(); }
    private async void OnLoaded(object sender, RoutedEventArgs e) {
        if (DataContext is ClinicalOrdersViewModel vm && !vm.IsBusy)
            await vm.RefreshCommand.ExecuteAsync(null);
    }
}
