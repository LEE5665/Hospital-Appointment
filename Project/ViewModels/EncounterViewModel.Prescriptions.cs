using System.Collections.ObjectModel;
using System.Globalization;
using System.Net.Http;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Project.Models;

namespace Project.ViewModels;

public partial class EncounterViewModel
{
    private bool savingPrescriptions;
    private bool loadingPrescriptionEditor;
    public ObservableCollection<MedicationRow> MedicationResults { get; } = new();
    public ObservableCollection<PrescriptionRow> Prescriptions { get; } = new();
    [ObservableProperty] private string medicationQuery = "";
    [ObservableProperty] private MedicationRow? selectedMedication;
    [ObservableProperty] private PrescriptionRow? selectedPrescription;
    [ObservableProperty] private string prescriptionDose = "";
    [ObservableProperty] private string prescriptionUnit = "";
    [ObservableProperty] private string prescriptionFrequency = "";
    [ObservableProperty] private string prescriptionDays = "";
    [ObservableProperty] private string prescriptionInstructions = "";
    [ObservableProperty] private string medicationMessage = "약품명 또는 코드로 검색하세요. 최대 50개 표시합니다.";
    [ObservableProperty] [NotifyPropertyChangedFor(nameof(IsQueueSelectionEnabled))]
    private bool hasUnsavedPrescriptions;
    private bool prescriptionEditorDirty;
    public bool CanSelectPrescription => !prescriptionEditorDirty;

    partial void OnSelectedPrescriptionChanged(PrescriptionRow? value) {
        if (value == null) return;
        loadingPrescriptionEditor = true;
        var medication = MedicationResults.FirstOrDefault(m => m.Code == value.MedicationCode);
        if (medication == null) {
            medication = new(value.MedicationCode, value.Name, value.Manufacturer, value.Specification, "", "");
            MedicationResults.Add(medication);
        }
        SelectedMedication = medication;
        PrescriptionDose = value.Dose.ToString("0.###", CultureInfo.InvariantCulture);
        PrescriptionUnit = value.Unit;
        PrescriptionFrequency = value.Frequency.ToString();
        PrescriptionDays = value.Days.ToString();
        PrescriptionInstructions = value.Instructions;
        loadingPrescriptionEditor = false;
        prescriptionEditorDirty = false;
        UpdatePrescriptionDirty();
    }
    partial void OnPrescriptionDoseChanged(string value) => PrescriptionEditorChanged();
    partial void OnPrescriptionUnitChanged(string value) => PrescriptionEditorChanged();
    partial void OnPrescriptionFrequencyChanged(string value) => PrescriptionEditorChanged();
    partial void OnPrescriptionDaysChanged(string value) => PrescriptionEditorChanged();
    partial void OnPrescriptionInstructionsChanged(string value) => PrescriptionEditorChanged();
    partial void OnSelectedMedicationChanged(MedicationRow? value) => PrescriptionEditorChanged();
    private void PrescriptionEditorChanged() {
        if (loadingPrescriptionEditor) return;
        prescriptionEditorDirty = true;
        UpdatePrescriptionDirty();
    }
    private void UpdatePrescriptionDirty() {
        HasUnsavedPrescriptions = prescriptionEditorDirty || !Prescriptions.SequenceEqual(SelectedEncounter?.Prescriptions ?? []);
        OnPropertyChanged(nameof(CanSelectPrescription));
    }
    private void ClearPrescriptionEditor() {
        loadingPrescriptionEditor = true;
        SelectedPrescription = null; SelectedMedication = null;
        PrescriptionDose = PrescriptionUnit = PrescriptionFrequency = PrescriptionDays = PrescriptionInstructions = "";
        loadingPrescriptionEditor = false;
        prescriptionEditorDirty = false;
        UpdatePrescriptionDirty();
    }
    private void LoadPrescriptions(EncounterRow? row) {
        Prescriptions.Clear();
        foreach (var item in row?.Prescriptions ?? []) Prescriptions.Add(item);
        MedicationResults.Clear(); MedicationQuery = "";
        ClearPrescriptionEditor();
        MedicationMessage = "약품명 또는 코드로 검색하세요. 최대 50개 표시합니다.";
    }
    [RelayCommand] private void NewPrescription() => ClearPrescriptionEditor();
    [RelayCommand] private Task SearchMedicationsAsync() => Run(async () => {
        if (!CanWrite) return;
        MedicationResults.Clear();
        if (string.IsNullOrWhiteSpace(MedicationQuery)) { MedicationMessage = "약품명 또는 코드를 입력하세요."; return; }
        var rows = await api.SendAsync<List<MedicationRow>>(HttpMethod.Get,
            "medications?query=" + Uri.EscapeDataString(MedicationQuery.Trim()));
        foreach (var row in rows) MedicationResults.Add(row);
        MedicationMessage = rows.Count == 0 ? "검색 결과가 없습니다." : $"검색 결과 {rows.Count}개 · 약품과 제조사를 확인해 선택하세요.";
    });
    [RelayCommand] private void AddPrescription() {
        if (!CanWrite) return;
        if (SelectedMedication is not { } medication
            || !decimal.TryParse(PrescriptionDose, NumberStyles.AllowDecimalPoint, CultureInfo.InvariantCulture, out var dose)
            || dose < 0.001m || dose > 99999m || decimal.Round(dose, 3) != dose
            || string.IsNullOrWhiteSpace(PrescriptionUnit) || PrescriptionUnit.Trim().Length > 20
            || !int.TryParse(PrescriptionFrequency, out var frequency) || frequency < 1 || frequency > 24
            || !int.TryParse(PrescriptionDays, out var days) || days < 1 || days > 365
            || string.IsNullOrWhiteSpace(PrescriptionInstructions) || PrescriptionInstructions.Trim().Length > 200) {
            Message = "약품 선택 후 투여량(0.001~99999), 단위, 1일 횟수(1~24), 일수(1~365), 용법을 입력하세요."; return;
        }
        var row = new PrescriptionRow(medication.Code, medication.Name, medication.Manufacturer, medication.Specification,
            dose, PrescriptionUnit.Trim(), frequency, days, PrescriptionInstructions.Trim());
        if (SelectedPrescription is { } old && Prescriptions.Contains(old)) Prescriptions[Prescriptions.IndexOf(old)] = row;
        else {
            if (Prescriptions.Count >= 50) { Message = "처방은 최대 50개까지 등록할 수 있습니다."; return; }
            Prescriptions.Add(row);
        }
        ClearPrescriptionEditor();
        Message = "처방 목록에 반영했습니다. 처방 저장을 눌러 주세요.";
    }
    [RelayCommand] private void RemovePrescription() {
        if (!CanWrite || SelectedPrescription is not { } row) return;
        Prescriptions.Remove(row); ClearPrescriptionEditor();
    }
    [RelayCommand] private Task SavePrescriptionsAsync() => Run(async () => {
        if (!CanWrite || SelectedEncounter == null) return;
        if (prescriptionEditorDirty) { Message = "입력 중인 처방을 목록에 반영하거나 입력 초기화를 눌러 주세요."; return; }
        var row = await api.SendAsync<EncounterRow>(HttpMethod.Put, $"encounters/{SelectedEncounter.Id}/prescriptions",
            new { version = SelectedEncounter.Version, prescriptions = Prescriptions.Select(p => new {
                p.MedicationCode, p.Dose, p.Unit, p.Frequency, p.Days, p.Instructions }).ToArray() });
        // A prescription save updates the shared version but must not discard a SOAP draft.
        savingPrescriptions = true;
        try {
            var old = Encounters.FirstOrDefault(e => e.Id == row.Id);
            if (old != null) {
                row = row with { QueuePosition = old.QueuePosition };
                Encounters[Encounters.IndexOf(old)] = row;
                SelectedQueueItem = row;
            }
            SelectedEncounter = row;
        } finally { savingPrescriptions = false; }
        LoadPrescriptions(row);
        UpdateDirtyState();
        Message = "처방을 저장했습니다. 작성 중인 SOAP 내용은 별도로 기록 저장을 눌러 주세요.";
    });
}
