package com.acme.salary.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behaves like a browser over real HTTP: a cookie jar holds the HttpOnly session cookie and the
 * XSRF-TOKEN cookie. Guards against regressions where cookie-authenticated writes skip CSRF checks
 * (MockMvc's test CSRF repository cannot catch that).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.security.cookie.secure=false") // plain-HTTP test server; Secure cookies would not be sent
@ActiveProfiles("test")
class BrowserSessionHttpTest {

    private static final String EMPLOYEE_JSON = """
            {"firstName":"Csrf","lastName":"Probe","email":"csrf.probe@acme.example","jobTitle":"Engineer",
             "department":"Engineering","countryCode":"US","annualSalary":100000,"hireDate":"2022-03-01"}""";

    @LocalServerPort
    private int port;

    private CookieManager cookies;
    private HttpClient browser;

    @BeforeEach
    void newBrowser() {
        cookies = new CookieManager();
        browser = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    @Test
    void firstRequestIssuesCsrfCookieReadableBySpa() throws Exception {
        send(get("/api/v1/auth/me"));

        HttpCookie xsrf = cookie("XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse();
    }

    @Test
    void cookieAuthenticatedWriteWithoutCsrfHeaderIsRejected() throws Exception {
        signIn();

        HttpResponse<String> withoutCsrf = send(post("/api/v1/employees", EMPLOYEE_JSON).build());
        assertThat(withoutCsrf.statusCode()).isEqualTo(403);

        HttpResponse<String> withCsrf = send(post("/api/v1/employees", EMPLOYEE_JSON)
                .header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").getValue()).build());
        assertThat(withCsrf.statusCode()).isEqualTo(201);
    }

    @Test
    void sessionCookieIsHttpOnlyAndScopedToApi() throws Exception {
        signIn();

        HttpCookie token = cookie("ACCESS_TOKEN");
        assertThat(token.isHttpOnly()).isTrue();
        assertThat(token.getPath()).isEqualTo("/api");
        assertThat(send(get("/api/v1/auth/me")).body()).contains("\"role\":\"HR_MANAGER\"");
    }

    @Test
    void staleCookieDoesNotBlockSigningInAgain() throws Exception {
        send(get("/api/v1/auth/me"));
        HttpCookie stale = new HttpCookie("ACCESS_TOKEN", "not-a-valid-jwt");
        stale.setPath("/api");
        cookies.getCookieStore().add(URI.create(base()), stale);

        assertThat(signIn().statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> signIn() throws Exception {
        send(get("/api/v1/auth/me"));
        HttpResponse<String> response = send(post("/api/v1/auth/login",
                "{\"username\":\"manager\",\"password\":\"Manager-password-1\"}")
                .header("X-XSRF-TOKEN", cookie("XSRF-TOKEN").getValue()).build());
        assertThat(response.statusCode()).isEqualTo(200);
        return response;
    }

    private HttpRequest get(String path) {
        return HttpRequest.newBuilder(URI.create(base() + path)).GET().build();
    }

    private HttpRequest.Builder post(String path, String json) {
        return HttpRequest.newBuilder(URI.create(base() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return browser.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpCookie cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
                .filter(c -> c.getName().equals(name))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    private String base() {
        return "http://localhost:" + port;
    }
}
