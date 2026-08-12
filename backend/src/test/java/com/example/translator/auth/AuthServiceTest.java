package com.example.translator.auth;

import com.example.translator.auth.AuthDtos.AuthResponse;
import com.example.translator.auth.AuthDtos.ChangePasswordRequest;
import com.example.translator.auth.AuthDtos.LoginRequest;
import com.example.translator.auth.AuthDtos.SignupRequest;
import com.example.translator.security.JwtService;
import com.example.translator.user.AppRole;
import com.example.translator.user.AppUser;
import com.example.translator.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void signup_firstEverUserBecomesAdmin() {
        when(appUserRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(appUserRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("token");

        AuthResponse response = authService.signup(new SignupRequest("admin@example.com", "password123", "Admin"));

        assertThat(response.user().appRole()).isEqualTo(AppRole.ADMIN);
        assertThat(response.token()).isEqualTo("token");
    }

    @Test
    void signup_subsequentUserBecomesBasicUser() {
        when(appUserRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(appUserRepository.count()).thenReturn(1L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateToken(any(AppUser.class))).thenReturn("token");

        AuthResponse response = authService.signup(new SignupRequest("user@example.com", "password123", "User"));

        assertThat(response.user().appRole()).isEqualTo(AppRole.USER);
    }

    @Test
    void signup_rejectsDuplicateEmail() {
        when(appUserRepository.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("dup@example.com", "password123", "Dup")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).save(any());
    }

    @Test
    void login_authenticatesThenIssuesToken() {
        AppUser user = new AppUser("user@example.com", "hashed", "User", AppRole.USER);
        when(appUserRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateToken(user)).thenReturn("token");

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "password123"));

        ArgumentCaptor<org.springframework.security.authentication.UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("user@example.com");
        assertThat(response.token()).isEqualTo("token");
    }

    @Test
    void changePassword_rejectsWrongCurrentPassword() {
        AppUser user = new AppUser("user@example.com", "hashed", "User", AppRole.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(1L, new ChangePasswordRequest("wrong", "newpassword")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changePassword_updatesHashWhenCurrentPasswordCorrect() {
        AppUser user = new AppUser("user@example.com", "hashed", "User", AppRole.USER);
        ReflectionTestUtils.setField(user, "id", 1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("newpassword")).thenReturn("new-hashed");

        authService.changePassword(1L, new ChangePasswordRequest("correct", "newpassword"));

        assertThat(user.getPasswordHash()).isEqualTo("new-hashed");
    }
}
