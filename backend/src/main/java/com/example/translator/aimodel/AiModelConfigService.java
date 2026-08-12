package com.example.translator.aimodel;

import com.example.translator.aimodel.AiModelConfigDtos.AdminResponse;
import com.example.translator.aimodel.AiModelConfigDtos.CreateRequest;
import com.example.translator.aimodel.AiModelConfigDtos.OptionResponse;
import com.example.translator.aimodel.AiModelConfigDtos.UpdateRequest;
import com.example.translator.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class AiModelConfigService {

    private final AiModelConfigRepository repository;

    public AiModelConfigService(AiModelConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdminResponse> listAll() {
        return repository.findAllByOrderByLabelAsc().stream().map(AdminResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OptionResponse> listEnabledOptions() {
        return repository.findAllByEnabledTrueOrderByLabelAsc().stream().map(OptionResponse::from).toList();
    }

    public AdminResponse create(CreateRequest request) {
        if (request.isDefault()) {
            clearExistingDefault();
        }
        AiModelConfig config = new AiModelConfig();
        config.setLabel(request.label().trim());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl().trim());
        config.setApiKey(request.apiKey());
        config.setModelIdentifier(request.modelIdentifier().trim());
        config.setApiVersion(request.apiVersion());
        config.setMaxTokens(request.maxTokens());
        config.setTimeoutSeconds(request.timeoutSeconds());
        config.setEnabled(request.enabled());
        config.setDefault(request.isDefault());
        return AdminResponse.from(repository.save(config));
    }

    public AdminResponse update(Long id, UpdateRequest request) {
        AiModelConfig config = getEntity(id);
        if (request.isDefault() && !config.isDefault()) {
            clearExistingDefault();
        }
        config.setLabel(request.label().trim());
        config.setProvider(request.provider());
        config.setBaseUrl(request.baseUrl().trim());
        if (request.apiKey() != null && !request.apiKey().isBlank()) {
            config.setApiKey(request.apiKey());
        }
        config.setModelIdentifier(request.modelIdentifier().trim());
        config.setApiVersion(request.apiVersion());
        config.setMaxTokens(request.maxTokens());
        config.setTimeoutSeconds(request.timeoutSeconds());
        config.setEnabled(request.enabled());
        config.setDefault(request.isDefault());
        return AdminResponse.from(config);
    }

    public void delete(Long id) {
        repository.delete(getEntity(id));
    }

    /**
     * Resolves a client-supplied model config id to a real, currently-enabled row. The client
     * only ever sends an opaque id here — never a provider/baseUrl/model string directly.
     */
    @Transactional(readOnly = true)
    public AiModelConfig getEnabledForDispatch(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("modelConfigId is required");
        }
        AiModelConfig config = repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AiModelConfig", id));
        if (!config.isEnabled()) {
            throw new IllegalArgumentException("Selected AI model is not currently enabled");
        }
        return config;
    }

    private void clearExistingDefault() {
        repository.findAllByOrderByLabelAsc().stream()
                .filter(AiModelConfig::isDefault)
                .forEach(c -> c.setDefault(false));
    }

    private AiModelConfig getEntity(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("AiModelConfig", id));
    }
}
