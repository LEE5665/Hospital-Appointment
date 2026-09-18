using System;
using System.Collections.Generic;
using System.Text;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;

namespace Project.ViewModels
{
    public partial class MainViewModel : ObservableObject
    {
        private readonly DashboardViewModel dashboardViewModel = new();
        private readonly PatientViewModel patientViewModel = new();
        // CurrentViewModel 변경 시 화면과 제목을 함께 갱신합니다.
        [ObservableProperty]
        [NotifyPropertyChangedFor(nameof(CurrentPageTitle))]
        private ObservableObject currentViewModel;

        public MainViewModel()
        {
            currentViewModel = dashboardViewModel;
            ShowDashboardCommand = new RelayCommand(() => CurrentViewModel = dashboardViewModel);
            ShowPatientsCommand = new RelayCommand(() => CurrentViewModel = patientViewModel);
        }

        public string CurrentPageTitle => CurrentViewModel is PatientViewModel ? "환자 관리" : "대시보드";
        public IRelayCommand ShowDashboardCommand { get; }
        public IRelayCommand ShowPatientsCommand { get; }

    }
}
