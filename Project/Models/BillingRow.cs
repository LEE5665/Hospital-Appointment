namespace Project.Models;

public record BillingRow(long EncounterId, string PatientName, string ChartNumber, string DoctorName,
    DateTime CompletedAt, long? Amount, string? Method, string ReceivedByName, DateTime? PaidAt)
{
    public string StatusLabel => PaidAt == null ? "미수납" : "수납완료";
    public string AmountLabel => Amount.HasValue ? $"{Amount:N0}원" : "—";
    public string MethodLabel => Method switch { "CASH" => "현금", "CARD" => "카드", _ => "—" };
}
