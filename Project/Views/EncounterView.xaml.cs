using System.Windows;
using System.Windows.Controls;
using Project.ViewModels;
namespace Project.Views;

public partial class EncounterView : UserControl
{
    public EncounterView() { InitializeComponent(); }
    private async void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (DataContext is EncounterViewModel vm) await vm.RefreshCommand.ExecuteAsync(null);
    }
}
