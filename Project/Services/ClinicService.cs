using System.Net.Http;
using System.Net.Http.Json;
using System.Text.Json;
namespace Project.Services;
public class ClinicService
{
    public async Task<T> SendAsync<T>(HttpMethod method, string path, object? body = null)
    {
        using var request = new HttpRequestMessage(method, "/api/clinic/" + path);
        if (body != null) request.Content = JsonContent.Create(body);
        using var response = await AuthService.Instance.Client.SendAsync(request);
        if (!response.IsSuccessStatusCode)
        {
            string message = $"요청 실패 ({(int)response.StatusCode})";
            if (response.StatusCode == System.Net.HttpStatusCode.Unauthorized)
                message = "로그인 세션이 만료되었습니다. 로그아웃 후 다시 로그인해 주세요.";
            try {
                using var error = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
                if (error.RootElement.TryGetProperty("message", out var value) && !string.IsNullOrWhiteSpace(value.GetString())) message = value.GetString()!;
            } catch (JsonException) { }
            throw new InvalidOperationException(message);
        }
        return await response.Content.ReadFromJsonAsync<T>() ?? throw new InvalidOperationException("응답이 비어 있습니다.");
    }
}
