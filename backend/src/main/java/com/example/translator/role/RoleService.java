package com.example.translator.role;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.role.RoleDtos.RoleRequest;
import com.example.translator.role.RoleDtos.RoleResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;

    public RoleService(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> findAll() {
        return roleRepository.findAllByOrderByNameAsc().stream()
                .map(RoleResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Role getEntity(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Role", id));
    }

    public RoleResponse create(RoleRequest request) {
        Role role = new Role(request.name().trim(), request.description());
        return RoleResponse.from(roleRepository.save(role));
    }

    public RoleResponse update(Long id, RoleRequest request) {
        Role role = getEntity(id);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        return RoleResponse.from(roleRepository.save(role));
    }

    public void delete(Long id) {
        Role role = getEntity(id);
        roleRepository.delete(role);
    }
}
