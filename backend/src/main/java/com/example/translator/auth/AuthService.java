package com.example.translator.auth;

import com.example.translator.auth.AuthDtos.AuthResponse;
import com.example.translator.auth.AuthDtos.ChangePasswordRequest;
import com.example.translator.auth.AuthDtos.LoginRequest;
import com.example.translator.auth.AuthDtos.SignupRequest;
import com.example.translator.auth.AuthDtos.UserSummary;
import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.security.JwtService;
import com.example.translator.user.AppRole;
import com.example.translator.user.AppUser;
import com.example.translator.user.AppUserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder,
                        JwtService jwtService, AuthenticationManager authenticationManager) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse signup(SignupRequest request) {
        String email = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        AppRole role = appUserRepository.count() == 0 ? AppRole.ADMIN : AppRole.USER;
        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()), request.displayName().trim(), role);
        appUserRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));
        AppUser user = appUserRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", request.email()));
        return new AuthResponse(jwtService.generateToken(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return UserSummary.from(getEntity(userId));
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        AppUser user = getEntity(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private AppUser getEntity(Long userId) {
        return appUserRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", userId));
    }
}
