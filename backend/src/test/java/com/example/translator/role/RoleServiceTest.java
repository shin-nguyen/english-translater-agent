package com.example.translator.role;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.role.RoleDtos.RoleRequest;
import com.example.translator.role.RoleDtos.RoleResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private RoleService roleService;

    @Test
    void findAll_returnsRolesOrderedByName() {
        Role dev = new Role("Developer", "tech");
        when(roleRepository.findAllByOrderByNameAsc()).thenReturn(List.of(dev));

        List<RoleResponse> result = roleService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Developer");
    }

    @Test
    void create_trimsNameAndSaves() {
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponse response = roleService.create(new RoleRequest("  PM  ", "manages stuff"));

        assertThat(response.name()).isEqualTo("PM");
        verify(roleRepository).save(any(Role.class));
    }

    @Test
    void getEntity_throwsWhenNotFound() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roleService.getEntity(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_removesExistingRole() {
        Role role = new Role("QA", "testing");
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role));

        roleService.delete(1L);

        verify(roleRepository).delete(role);
    }
}
