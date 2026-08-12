package com.example.translator.user;

import com.example.translator.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private static AppUser admin(Long id) {
        AppUser user = new AppUser("admin@example.com", "hash", "Admin", AppRole.ADMIN);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @Test
    void updateRole_rejectsDemotingLastAdmin() {
        AppUser theAdmin = admin(1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(theAdmin));
        when(appUserRepository.countByAppRole(AppRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateRole(1L, AppRole.USER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRole_allowsDemotingWhenMultipleAdminsExist() {
        AppUser theAdmin = admin(1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(theAdmin));
        when(appUserRepository.countByAppRole(AppRole.ADMIN)).thenReturn(2L);

        userService.updateRole(1L, AppRole.USER);

        assertThat(theAdmin.getAppRole()).isEqualTo(AppRole.USER);
    }

    @Test
    void updateStatus_rejectsDisablingLastAdmin() {
        AppUser theAdmin = admin(1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(theAdmin));
        when(appUserRepository.countByAppRole(AppRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.updateStatus(1L, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_rejectsDeletingLastAdmin() {
        AppUser theAdmin = admin(1L);
        when(appUserRepository.findById(1L)).thenReturn(Optional.of(theAdmin));
        when(appUserRepository.countByAppRole(AppRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> userService.delete(1L))
                .isInstanceOf(IllegalArgumentException.class);
        verify(appUserRepository, never()).delete(any());
    }

    @Test
    void delete_allowsDeletingNonAdminUser() {
        AppUser basicUser = new AppUser("user@example.com", "hash", "User", AppRole.USER);
        ReflectionTestUtils.setField(basicUser, "id", 2L);
        when(appUserRepository.findById(2L)).thenReturn(Optional.of(basicUser));

        userService.delete(2L);

        verify(appUserRepository).delete(basicUser);
    }

    @Test
    void getById_throwsWhenUserNotFound() {
        when(appUserRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
