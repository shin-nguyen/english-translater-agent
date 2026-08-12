package com.example.translator.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserDtos {

    private UserDtos() {
    }

    public record AdminCreateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank @Size(max = 100) String displayName,
            @NotNull AppRole appRole
    ) {
    }

    public record UpdateRoleRequest(@NotNull AppRole appRole) {
    }

    public record UpdateStatusRequest(boolean enabled) {
    }
}
