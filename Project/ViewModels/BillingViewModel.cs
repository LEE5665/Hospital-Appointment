using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;
using Project.Services;

namespace Project.ViewModels;

public partial class BillingViewModel : ObservableObject
{
    private readonly ClinicService api = new();
    public ObservableCollection<BillingRow> Rows { get; } = new();
    public bool HasPermission => AuthService.Instance.CurrentUser?.Role is "ADMIN" or "NURSE";
    public bool IsIdle => !IsBusy;
    public bool CanPay => HasPermission && !IsBusy && SelectedRow is { PaidAt: null };
    [ObservableProperty] private DateTime? selectedDate = DateTime.Today;
    [ObservableProperty] private BillingRow? selectedRow;
    [ObservableProperty] private string amountText = "";
    [ObservableProperty] private int methodIndex;
    [ObservableProperty] private bool isBusy;
    [ObservableProperty] private string message = "진료 완료된 환자를 선택해 수납하세요.";

    partial void OnSelectedRowChanged(BillingRow? value) {
        AmountText = value?.Amount?.ToString(CultureInfo.InvariantCulture) ?? "";
        MethodIndex = value?.Method == "CARD" ? 1 : 0;
        OnPropertyChanged(nameof(CanPay));
    }
    partial void OnIsBusyChanged(bool value) {
        OnPropertyChanged(nameof(IsIdle)); OnPropertyChanged(nameof(CanPay));
    }
    [RelayCommand]
    private async Task RefreshAsync() {
        if (IsBusy) return;
        if (!HasPermission) { Message = "간호사 또는 관리자 계정으로 수납할 수 있습니다."; return; }
        IsBusy = true;
        try {
            var date = (SelectedDate ?? DateTime.Today).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
            var rows = await api.SendAsync<List<BillingRow>>(HttpMethod.Get, $"billing?date={date}");
            SelectedRow = null; Rows.Clear();
            foreach (var row in rows) Rows.Add(row);
            Message = rows.Count == 0 ? "해당 날짜에 완료된 진료가 없습니다." : $"진료 완료 {rows.Count}건 · 미수납 {rows.Count(r => r.PaidAt == null)}건";
        } catch (Exception ex) { Message = Error(ex); }
        finally { IsBusy = false; }
    }
    [RelayCommand]
    private async Task PayAsync() {
        if (!CanPay || SelectedRow is not { } row) return;
        if (!long.TryParse(AmountText.Trim(), NumberStyles.None, CultureInfo.InvariantCulture, out var amount)
            || amount < 1 || amount > 999999999) {
            Message = "금액은 1~999,999,999원 사이의 정수로 입력하세요."; return;
        }
        IsBusy = true;
        try {
            var paid = await api.SendAsync<BillingRow>(HttpMethod.Post, $"billing/{row.EncounterId}/payment",
                new { amount, method = MethodIndex == 1 ? "CARD" : "CASH" });
            Rows[Rows.IndexOf(row)] = paid; SelectedRow = paid;
            Message = $"{paid.PatientName}님 {paid.AmountLabel} 수납 완료했습니다.";
        } catch (Exception ex) { Message = Error(ex); }
        finally { IsBusy = false; }
    }
    private static string Error(Exception ex) => ex is HttpRequestException
        ? "서버 연결에 실패했습니다. 새로고침하여 수납 상태를 확인하세요." : ex.Message;
}
