package com.acme.salary.auth;

import com.acme.salary.common.error.ApiException;
import com.acme.salary.security.AuthCookies;
import com.acme.salary.security.Role;
import com.acme.salary.security.TokenService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    record LoginRequest(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 128) String password) {
        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=***]";
        }
    }

    record UserResponse(String username, Role role) {
    }

    static final class InvalidCredentialsException extends ApiException {
        InvalidCredentialsException() {
            // One message for unknown user, wrong password and disabled account: no account enumeration.
            super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password.");
        }
    }

    static final class AccountLockedException extends ApiException {
        AccountLockedException() {
            super(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Too many failed sign-in attempts. Please try again later.");
        }
    }

    private final AuthenticationManager authenticationManager;
    private final LoginAttemptService loginAttempts;
    private final TokenService tokens;
    private final AuthCookies cookies;

    AuthController(AuthenticationManager authenticationManager, LoginAttemptService loginAttempts,
                   TokenService tokens, AuthCookies cookies) {
        this.authenticationManager = authenticationManager;
        this.loginAttempts = loginAttempts;
        this.tokens = tokens;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        if (loginAttempts.isLocked(request.username())) {
            throw new AccountLockedException();
        }
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            loginAttempts.recordFailure(request.username());
            log.info("Failed sign-in attempt for username '{}'", forLog(request.username()));
            throw new InvalidCredentialsException();
        }
        loginAttempts.recordSuccess(request.username());

        UserResponse user = new UserResponse(authentication.getName(), roleOf(authentication));
        String token = tokens.issue(user.username(), user.role());
        log.info("User '{}' signed in", user.username());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(token).toString())
                .body(user);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        return new UserResponse(authentication.getName(), roleOf(authentication));
    }

    /** Usernames are attacker-controlled; strip line breaks so they cannot forge log entries. */
    private static String forLog(String value) {
        return value.replaceAll("[\\r\\n\\t]", "_");
    }

    private static Role roleOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> Role.valueOf(a.substring("ROLE_".length())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Authenticated user has no role"));
    }
}
