namespace Project.Models;
public record AppointmentRow(long Id, long PatientId, string PatientName, string Phone, long? DoctorId,
    string DoctorName, DateTime ScheduledAt, string? Reason, string Status, long? EncounterId, long Version = 0)
{
    public string StatusLabel => Status switch { "BOOKED" => "예약", "CHECKED_IN" => "접수 완료", _ => "취소" };
    public string BookingLabel => $"{ScheduledAt:HH:mm} · {DoctorName} · {Reason}";
}
public record PatientRow(long Id, string ChartNumber, string Name, DateTime BirthDate, string Gender, string Phone, string? Address,
    string? Allergies, string? MedicalHistory, string? Memo)
{
    public string GenderLabel => Gender == "MALE" ? "남성" : "여성";
}
public record DoctorRow(long? Id, string Name, string? Department)
{
    public string Label => string.IsNullOrEmpty(Department) ? Name : $"{Name} · {Department}";
}
public record EncounterRow(long Id, long PatientId, string PatientName, string ChartNumber, long? DoctorId,
    string DoctorName, string Status, DateTime RegisteredAt, string? Reason,
    string Subjective, string Objective, string Assessment, string Plan, long Version, List<EncounterDiagnosisRow> Diagnoses)
{
    public int QueuePosition { get; init; }
    public string WaitingTime => Status == "WAITING" ? $"{Math.Max(0, (int)(DateTime.Now - RegisteredAt).TotalMinutes)}분" : "—";
    public string StatusLabel => Status switch { "WAITING" => "진료 대기", "IN_PROGRESS" => "진료 중", "COMPLETED" => "진료 완료", _ => "취소" };
}
public record EncounterPage(List<EncounterRow> Items, int Page, int Size, long TotalElements, int TotalPages);
public record DiagnosisSearchRow(string Code, string Name, string EnglishName, string ClassificationVersion,
    bool PrincipalDiagnosisAllowed)
{
    public string Label => $"{Code} · {Name}" + (PrincipalDiagnosisAllowed ? "" : " (주진단 불가)");
}
public record EncounterDiagnosisRow(string Code, string Name, string ClassificationVersion, bool Principal,
    bool PrincipalDiagnosisAllowed)
{
    public string KindLabel => Principal ? "주진단" : "부진단";
}
