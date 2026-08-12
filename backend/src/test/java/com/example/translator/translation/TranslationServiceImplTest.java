package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiModelConfigService;
import com.example.translator.aimodel.AiProviderType;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslationServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ContextRepository contextRepository;
    @Mock
    private AiModelConfigService aiModelConfigService;
    @Mock
    private AiProviderClientRegistry registry;
    @Mock
    private AiProviderClient providerClient;

    private TranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TranslationServiceImpl(roleRepository, contextRepository, new ObjectMapper(),
                aiModelConfigService, registry);
    }

    @Test
    void buildSystemPrompt_includesRoleAndContextDescriptions() {
        Role role = new Role("Developer", "Van phong ky thuat, suc tich");
        Context context = new Context("Comment Jira", "Ngan gon, di thang vao van de");

        String prompt = service.buildSystemPrompt(role, context);

        assertThat(prompt).contains("Developer").contains("Van phong ky thuat, suc tich");
        assertThat(prompt).contains("Comment Jira").contains("Ngan gon, di thang vao van de");
    }

    @Test
    void buildSystemPrompt_handlesNullRoleAndContext() {
        String prompt = service.buildSystemPrompt(null, null);

        assertThat(prompt).contains("chung chung");
    }

    @Test
    void parseResponse_parsesCleanJson() {
        String json = """
                {"detectedLanguage":"vi","suggestedTitle":"Test title","mainResult":"Hello world","alternatives":[{"text":"Hi there","style":"Casual","reason":"ok"}],"analysis":[{"original":"xin chao","improved":"hello","reason":"ok"}]}
                """;

        TranslateResponse response = service.parseResponse(json);

        assertThat(response.mainResult()).isEqualTo("Hello world");
        assertThat(response.detectedLanguage()).isEqualTo("vi");
        assertThat(response.alternatives()).extracting("text").containsExactly("Hi there");
    }

    @Test
    void parseResponse_stripsMarkdownCodeFences() {
        String json = "```json\n{\"detectedLanguage\":\"en\",\"suggestedTitle\":\"t\",\"mainResult\":\"Result\",\"alternatives\":[],\"analysis\":[]}\n```";

        TranslateResponse response = service.parseResponse(json);

        assertThat(response.mainResult()).isEqualTo("Result");
    }

    @Test
    void parseResponse_extractsJsonFromSurroundingProse() {
        String json = "Here is the JSON: {\"detectedLanguage\":\"en\",\"suggestedTitle\":\"t\",\"mainResult\":\"Result\",\"alternatives\":[],\"analysis\":[]} Hope that helps!";

        TranslateResponse response = service.parseResponse(json);

        assertThat(response.mainResult()).isEqualTo("Result");
    }

    @Test
    void parseResponse_throwsOnCompletelyInvalidJson() {
        assertThatThrownBy(() -> service.parseResponse("not json at all"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parseResponse_throwsWhenMainResultMissing() {
        String json = "{\"detectedLanguage\":\"vi\",\"suggestedTitle\":\"t\",\"alternatives\":[],\"analysis\":[]}";

        assertThatThrownBy(() -> service.parseResponse(json))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void translate_dispatchesThroughRegistryAndRetriesOnceOnMalformedJsonThenSucceeds() {
        AiModelConfig config = new AiModelConfig();
        config.setProvider(AiProviderType.OPENAI_COMPATIBLE);
        when(aiModelConfigService.getEnabledForDispatch(42L)).thenReturn(config);
        when(registry.get(AiProviderType.OPENAI_COMPATIBLE)).thenReturn(providerClient);
        when(providerClient.callModel(eq(config), any(), eq("xin chao")))
                .thenReturn("not json",
                        "{\"detectedLanguage\":\"vi\",\"suggestedTitle\":\"t\",\"mainResult\":\"Hi\",\"alternatives\":[],\"analysis\":[]}");

        TranslateResponse result = service.translate(new TranslateRequest("xin chao", null, null, 42L));

        assertThat(result.mainResult()).isEqualTo("Hi");
        verify(providerClient, times(2)).callModel(eq(config), any(), eq("xin chao"));
    }
}
