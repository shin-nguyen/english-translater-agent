package com.example.translator.user;

import com.example.translator.auth.AuthDtos.UserSummary;
import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.user.UserDtos.AdminCreateUserRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserSummary> listAll() {
        return appUserRepository.findAll().stream().map(UserSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public UserSummary getById(Long id) {
        return UserSummary.from(getEntity(id));
    }

    public UserSummary create(AdminCreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (appUserRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }
        AppUser user = new AppUser(email, passwordEncoder.encode(request.password()),
                request.displayName().trim(), request.appRole());
        return UserSummary.from(appUserRepository.save(user));
    }

    public UserSummary updateRole(Long id, AppRole newRole) {
        AppUser user = getEntity(id);
        if (user.getAppRole() == AppRole.ADMIN && newRole != AppRole.ADMIN) {
            requireNotLastAdmin();
        }
        user.setAppRole(newRole);
        return UserSummary.from(user);
    }

    public UserSummary updateStatus(Long id, boolean enabled) {
        AppUser user = getEntity(id);
        if (!enabled && user.getAppRole() == AppRole.ADMIN) {
            requireNotLastAdmin();
        }
        user.setEnabled(enabled);
        return UserSummary.from(user);
    }

    public void delete(Long id) {
        AppUser user = getEntity(id);
        if (user.getAppRole() == AppRole.ADMIN) {
            requireNotLastAdmin();
        }
        appUserRepository.delete(user);
    }

    private void requireNotLastAdmin() {
        if (appUserRepository.countByAppRole(AppRole.ADMIN) <= 1) {
            throw new IllegalArgumentException("Cannot remove the last remaining admin");
        }
    }

    private AppUser getEntity(Long id) {
        return appUserRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AppUser", id));
    }
}
