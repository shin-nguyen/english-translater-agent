package com.example.translator.aimodel;

import com.example.translator.aimodel.AiModelConfigDtos.AdminResponse;
import com.example.translator.aimodel.AiModelConfigDtos.CreateRequest;
import com.example.translator.aimodel.AiModelConfigDtos.UpdateRequest;
import com.example.translator.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiModelConfigServiceTest {

    @Mock
    private AiModelConfigRepository repository;

    @InjectMocks
    private AiModelConfigService service;

    private static AiModelConfig existing(Long id, boolean isDefault) {
        AiModelConfig config = new AiModelConfig();
        ReflectionTestUtils.setField(config, "id", id);
        config.setLabel("Existing");
        config.setProvider(AiProviderType.OPENAI_COMPATIBLE);
        config.setBaseUrl("https://example.com");
        config.setApiKey("old-key");
        config.setModelIdentifier("model-x");
        config.setEnabled(true);
        config.setDefault(isDefault);
        return config;
    }

    @Test
    void create_withDefaultTrue_clearsDefaultOnOtherRows() {
        AiModelConfig other = existing(1L, true);
        when(repository.findAllByOrderByLabelAsc()).thenReturn(List.of(other));
        when(repository.save(any(AiModelConfig.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateRequest request = new CreateRequest("New", AiProviderType.ANTHROPIC, "https://api.anthropic.com",
                "key", "claude-x", null, 1536, 20, true, true);

        AdminResponse response = service.create(request);

        assertThat(other.isDefault()).isFalse();
        assertThat(response.isDefault()).isTrue();
    }

    @Test
    void update_withBlankApiKey_keepsExistingKey() {
        AiModelConfig config = existing(5L, false);
        when(repository.findById(5L)).thenReturn(Optional.of(config));

        UpdateRequest request = new UpdateRequest("Updated label", AiProviderType.OPENAI_COMPATIBLE,
                "https://example.com", "", "model-x", null, 1536, 20, true, false);

        service.update(5L, request);

        assertThat(config.getApiKey()).isEqualTo("old-key");
        assertThat(config.getLabel()).isEqualTo("Updated label");
    }

    @Test
    void update_withNonBlankApiKey_replacesExistingKey() {
        AiModelConfig config = existing(5L, false);
        when(repository.findById(5L)).thenReturn(Optional.of(config));

        UpdateRequest request = new UpdateRequest("Existing", AiProviderType.OPENAI_COMPATIBLE,
                "https://example.com", "new-key", "model-x", null, 1536, 20, true, false);

        service.update(5L, request);

        assertThat(config.getApiKey()).isEqualTo("new-key");
    }

    @Test
    void getEnabledForDispatch_throwsIllegalArgumentWhenIdNull() {
        assertThatThrownBy(() -> service.getEnabledForDispatch(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEnabledForDispatch_throwsResourceNotFoundWhenUnknown() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEnabledForDispatch(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getEnabledForDispatch_throwsIllegalArgumentWhenDisabled() {
        AiModelConfig config = existing(7L, false);
        config.setEnabled(false);
        when(repository.findById(7L)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.getEnabledForDispatch(7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getEnabledForDispatch_returnsConfigWhenEnabled() {
        AiModelConfig config = existing(7L, false);
        when(repository.findById(7L)).thenReturn(Optional.of(config));

        AiModelConfig result = service.getEnabledForDispatch(7L);

        assertThat(result).isSameAs(config);
    }
}
