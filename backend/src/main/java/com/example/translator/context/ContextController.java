package com.example.translator.context;

import com.example.translator.context.ContextDtos.ContextRequest;
import com.example.translator.context.ContextDtos.ContextResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/contexts")
public class ContextController {

    private final ContextService contextService;

    public ContextController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public List<ContextResponse> findAll() {
        return contextService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContextResponse create(@Valid @RequestBody ContextRequest request) {
        return contextService.create(request);
    }

    @PutMapping("/{id}")
    public ContextResponse update(@PathVariable Long id, @Valid @RequestBody ContextRequest request) {
        return contextService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        contextService.delete(id);
    }
}
