package com.example.translator.context;

import com.example.translator.common.ResourceNotFoundException;
import com.example.translator.context.ContextDtos.ContextRequest;
import com.example.translator.context.ContextDtos.ContextResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ContextService {

    private final ContextRepository contextRepository;

    public ContextService(ContextRepository contextRepository) {
        this.contextRepository = contextRepository;
    }

    @Transactional(readOnly = true)
    public List<ContextResponse> findAll() {
        return contextRepository.findAllByOrderByNameAsc().stream()
                .map(ContextResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Context getEntity(Long id) {
        return contextRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Context", id));
    }

    public ContextResponse create(ContextRequest request) {
        Context context = new Context(request.name().trim(), request.description());
        return ContextResponse.from(contextRepository.save(context));
    }

    public ContextResponse update(Long id, ContextRequest request) {
        Context context = getEntity(id);
        context.setName(request.name().trim());
        context.setDescription(request.description());
        return ContextResponse.from(contextRepository.save(context));
    }

    public void delete(Long id) {
        Context context = getEntity(id);
        contextRepository.delete(context);
    }
}
