package com.example.translator.user;

import com.example.translator.auth.AuthDtos.UserSummary;
import com.example.translator.user.UserDtos.AdminCreateUserRequest;
import com.example.translator.user.UserDtos.UpdateRoleRequest;
import com.example.translator.user.UserDtos.UpdateStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserSummary> list() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    public UserSummary get(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary create(@Valid @RequestBody AdminCreateUserRequest request) {
        return userService.create(request);
    }

    @PatchMapping("/{id}/role")
    public UserSummary setRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest request) {
        return userService.updateRole(id, request.appRole());
    }

    @PatchMapping("/{id}/status")
    public UserSummary setStatus(@PathVariable Long id, @Valid @RequestBody UpdateStatusRequest request) {
        return userService.updateStatus(id, request.enabled());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
