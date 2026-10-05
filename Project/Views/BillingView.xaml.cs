using System.Windows;
using System.Windows.Controls;
using Project.ViewModels;

namespace Project.Views;

public partial class BillingView : UserControl
{
    public BillingView() { InitializeComponent(); }
    private async void OnLoaded(object sender, RoutedEventArgs e) {
        if (DataContext is BillingViewModel vm && !vm.IsBusy)
            await vm.RefreshCommand.ExecuteAsync(null);
    }
}
