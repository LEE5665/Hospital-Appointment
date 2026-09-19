using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Http;
using System.Text.Json;
using System.Threading.Tasks;
using Project.Models;

namespace Project.Services
{
    public class AuthService
    {
        private static readonly Lazy<AuthService> _instance = new(() => new AuthService());
        public static AuthService Instance => _instance.Value;

        private readonly HttpClient _httpClient;
        private readonly CookieContainer _cookieContainer;

        public MemberResponse? CurrentUser { get; private set; }
        public bool IsAuthenticated => CurrentUser != null;

        private AuthService()
        {
            _cookieContainer = new CookieContainer();
            var handler = new HttpClientHandler
            {
                CookieContainer = _cookieContainer,
                UseCookies = true
            };

            _httpClient = new HttpClient(handler)
            {
                BaseAddress = new Uri("http://localhost:8080")
            };
        }

        public async Task<(bool Success, string? ErrorMessage)> LoginAsync(string email, string password)
        {
            try
            {
                var formData = new Dictionary<string, string>
                {
                    { "email", email.Trim() },
                    { "password", password }
                };

                var content = new FormUrlEncodedContent(formData);
                var response = await _httpClient.PostAsync("/api/auth/login", content);

                if (response.IsSuccessStatusCode)
                {
                    // 로그인 성공 시 내 정보(/api/auth/me) 조회하여 CurrentUser에 보관
                    var meResponse = await _httpClient.GetAsync("/api/auth/me");
                    if (meResponse.IsSuccessStatusCode)
                    {
                        var json = await meResponse.Content.ReadAsStringAsync();
                        CurrentUser = JsonSerializer.Deserialize<MemberResponse>(json);
                    }
                    return (true, null);
                }

                // 로그인 실패 메시지 파싱
                var errorJson = await response.Content.ReadAsStringAsync();
                try
                {
                    using var doc = JsonDocument.Parse(errorJson);
                    if (doc.RootElement.TryGetProperty("message", out var msgElement))
                    {
                        return (false, msgElement.GetString());
                    }
                }
                catch
                {
                    // JSON 파싱 실패 시 기본 메시지
                }

                return (false, "이메일 또는 비밀번호가 올바르지 않습니다.");
            }
            catch (Exception ex)
            {
                return (false, $"서버에 연결할 수 없습니다: {ex.Message}");
            }
        }

        public async Task LogoutAsync()
        {
            try
            {
                await _httpClient.PostAsync("/api/auth/logout", null);
            }
            catch
            {
                // 무시
            }
            finally
            {
                CurrentUser = null;
            }
        }
    }
}
