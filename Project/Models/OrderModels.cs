namespace Project.Models;

public record OrderItemRow(long Id, string Code, string Name, string Type)
{
    public string Label => $"{(Type == "TEST" ? "검사" : "처치")} · {Name}";
}
public record ClinicalOrderRow(long Id, long EncounterId, long PatientId, string PatientName, string ChartNumber,
    string ItemCode, string ItemName, string Type, string Instructions, string Status,
    long RequestedById, string RequestedByName, DateTime RequestedAt,
    long? PerformedById, string PerformedByName, DateTime? StartedAt, DateTime? CompletedAt,
    string Result, string? CancellationReason, DateTime? CancelledAt,
    string? ReviewedByName, DateTime? ReviewedAt, long Version)
{
    public string TypeLabel => Type == "TEST" ? "검사" : "처치";
    public string StatusLabel => Status switch { "REQUESTED" => "요청", "IN_PROGRESS" => "수행 중", "COMPLETED" => "완료", _ => "취소" };
    public string ReviewLabel => Status != "COMPLETED" ? "—" : ReviewedAt == null ? "미확인" : "확인";
    public string DetailTitle => $"{PatientName} · {ChartNumber} · {ItemName}";
    public string Timeline => $"요청: {RequestedByName} {RequestedAt:yyyy-MM-dd HH:mm}\n"
        + $"수행: {PerformedByName} · 시작 {StartedAt:yyyy-MM-dd HH:mm} · 완료 {CompletedAt:yyyy-MM-dd HH:mm}\n"
        + (Status == "CANCELLED" ? $"취소: {CancelledAt:yyyy-MM-dd HH:mm} · {CancellationReason}"
            : ReviewedAt == null ? "의사 결과 확인: 미확인" : $"결과 확인: {ReviewedByName} {ReviewedAt:yyyy-MM-dd HH:mm}");
}
public record ClinicalOrderPage(List<ClinicalOrderRow> Items, int Page, int TotalPages, long TotalElements);
