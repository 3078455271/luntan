package com.luntan.identity.api;

import com.luntan.identity.api.dto.IdentityDtos;
import com.luntan.identity.application.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public IdentityDtos.UserView getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userService.getProfile(Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/me")
    public IdentityDtos.UserView updateProfile(@AuthenticationPrincipal Jwt jwt,
                                               @Valid @RequestBody IdentityDtos.UpdateProfileRequest request) {
        return userService.updateProfile(Long.valueOf(jwt.getSubject()), request);
    }
}