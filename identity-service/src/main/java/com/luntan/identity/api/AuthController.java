package com.luntan.identity.api;

import com.luntan.identity.api.dto.IdentityDtos;
import com.luntan.identity.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public IdentityDtos.AuthResponse register(@Valid @RequestBody IdentityDtos.RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public IdentityDtos.AuthResponse login(@Valid @RequestBody IdentityDtos.LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public IdentityDtos.AuthResponse refresh(@Valid @RequestBody IdentityDtos.RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Jwt jwt,
                       @Valid @RequestBody IdentityDtos.LogoutRequest request) {
        authService.logout(Long.valueOf(jwt.getSubject()), request.refreshToken());
    }
}