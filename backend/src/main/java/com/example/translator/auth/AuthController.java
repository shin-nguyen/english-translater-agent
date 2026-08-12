package com.example.translator.auth;

import com.example.translator.auth.AuthDtos.AuthResponse;
import com.example.translator.auth.AuthDtos.ChangePasswordRequest;
import com.example.translator.auth.AuthDtos.LoginRequest;
import com.example.translator.auth.AuthDtos.SignupRequest;
import com.example.translator.auth.AuthDtos.UserSummary;
import com.example.translator.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        return authService.signup(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserSummary me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal.getId());
    }

    @PostMapping("/change-password")
    public void changePassword(@AuthenticationPrincipal UserPrincipal principal,
                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request);
    }
}
