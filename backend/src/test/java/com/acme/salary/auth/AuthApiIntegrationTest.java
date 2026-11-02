package com.acme.salary.auth;

import com.acme.salary.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the real login flow: BCrypt, JWT issuing, the cookie and token validation. */
class AuthApiIntegrationTest extends IntegrationTest {

    @Test
    void successfulLoginSetsHardenedCookieThatAuthenticatesLaterRequests() throws Exception {
        MockHttpServletResponse response = login("manager", "Manager-password-1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("manager"))
                .andExpect(jsonPath("$.role").value("HR_MANAGER"))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("ACCESS_TOKEN="),
                        containsString("HttpOnly"),
                        containsString("Secure"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api"))))
                .andReturn().getResponse();

        Cookie token = response.getCookie("ACCESS_TOKEN");
        assertThat(token).isNotNull();
        assertThat(response.getContentAsString()).doesNotContain(token.getValue());

        mvc.perform(get("/api/v1/auth/me").cookie(token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR_MANAGER"));
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGenericError() throws Exception {
        login("viewer", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
        login("nobody", "whatever-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void repeatedFailuresLockTheAccountEvenForTheRightPassword() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("lockme", "wrong-password").andExpect(status().isUnauthorized());
        }

        login("lockme", "wrong-password")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"));
    }

    @Test
    void tamperedOrForeignTokensAreRejected() throws Exception {
        mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("ACCESS_TOKEN", "eyJhbGciOiJub25lIn0.eyJzdWIiOiJ4In0.")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutExpiresTheCookie() throws Exception {
        Cookie token = login("viewer", "Viewer-password-1").andReturn().getResponse().getCookie("ACCESS_TOKEN");

        mvc.perform(post("/api/v1/auth/logout").cookie(token).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(containsString("ACCESS_TOKEN="), containsString("Max-Age=0"))));
    }

    @Test
    void loginRequiresCsrfToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("username", "viewer", "password", "Viewer-password-1"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mvc.perform(get("/api/v1/auth/me"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void actuatorExposesOnlyHealth() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/actuator/env").with(manager())).andExpect(status().isForbidden());
    }

    private ResultActions login(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("username", username, "password", password))));
    }
}
