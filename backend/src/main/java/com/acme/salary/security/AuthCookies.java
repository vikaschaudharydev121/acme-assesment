package com.acme.salary.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * The session token lives in an HttpOnly cookie scoped to /api, so page scripts (and therefore XSS)
 * can never read it, and static asset requests never carry it.
 */
@Component
public class AuthCookies {

    public static final String ACCESS_TOKEN = "ACCESS_TOKEN";
    private static final String PATH = "/api";

    private final SecurityProperties properties;

    public AuthCookies(SecurityProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie issue(String token) {
        return base(token).maxAge(properties.jwt().ttl()).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    static Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> ACCESS_TOKEN.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(ACCESS_TOKEN, value)
                .httpOnly(true)
                .secure(properties.cookie().secure())
                .sameSite("Strict")
                .path(PATH);
    }
}
