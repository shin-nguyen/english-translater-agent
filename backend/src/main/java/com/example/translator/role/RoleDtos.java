package com.example.translator.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public class RoleDtos {

    private RoleDtos() {
    }

    public record RoleResponse(Long id, String name, String description, Instant createdAt) {
        public static RoleResponse from(Role role) {
            return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.getCreatedAt());
        }
    }

    public record RoleRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description
    ) {
    }
}
