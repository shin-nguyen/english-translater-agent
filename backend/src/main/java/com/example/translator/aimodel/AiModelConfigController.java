package com.example.translator.aimodel;

import com.example.translator.aimodel.AiModelConfigDtos.AdminResponse;
import com.example.translator.aimodel.AiModelConfigDtos.CreateRequest;
import com.example.translator.aimodel.AiModelConfigDtos.OptionResponse;
import com.example.translator.aimodel.AiModelConfigDtos.UpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai-models")
public class AiModelConfigController {

    private final AiModelConfigService service;

    public AiModelConfigController(AiModelConfigService service) {
        this.service = service;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public List<AdminResponse> list() {
        return service.listAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminResponse create(@Valid @RequestBody CreateRequest request) {
        return service.create(request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public AdminResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return service.update(id, request);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    // any authenticated user (basic or admin) — literal path segment, matches before "/{id}"
    @GetMapping("/enabled")
    public List<OptionResponse> listEnabled() {
        return service.listEnabledOptions();
    }
}
