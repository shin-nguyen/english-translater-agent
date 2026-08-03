package com.example.translator.translation;

import com.example.translator.config.OpenRouterProperties;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import com.example.translator.translation.OpenRouterApiModels.ChatCompletionRequest;
import com.example.translator.translation.OpenRouterApiModels.ChatCompletionResponse;
import com.example.translator.translation.OpenRouterApiModels.ChatMessage;
import com.example.translator.translation.OpenRouterApiModels.Choice;
import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OpenRouterTranslationServiceImplTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ContextRepository contextRepository;

    private OpenRouterTranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        OpenRouterProperties properties = new OpenRouterProperties();
        properties.setModel("openai/gpt-oss-20b:free");
        service = new OpenRouterTranslationServiceImpl(restClient, properties, roleRepository, contextRepository, new ObjectMapper());
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
    void parseResponse_throwsOnCompletelyInvalidJson() {
        assertThatThrownBy(() -> service.parseResponse("not json at all"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void translate_retriesOnceOnMalformedJsonThenSucceeds() {
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec1 = mock(RestClient.ResponseSpec.class);
        RestClient.ResponseSpec responseSpec2 = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/chat/completions")).thenReturn(bodySpec);
        when(bodySpec.body(any(ChatCompletionRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec1, responseSpec2);

        ChatCompletionResponse malformed = new ChatCompletionResponse("id1",
                List.of(new Choice(new ChatMessage("assistant", "not json"))));
        ChatCompletionResponse valid = new ChatCompletionResponse("id2",
                List.of(new Choice(new ChatMessage("assistant",
                        "{\"detectedLanguage\":\"vi\",\"suggestedTitle\":\"t\",\"mainResult\":\"Hi\",\"alternatives\":[],\"analysis\":[]}"))));

        when(responseSpec1.body(ChatCompletionResponse.class)).thenReturn(malformed);
        when(responseSpec2.body(ChatCompletionResponse.class)).thenReturn(valid);

        TranslateResponse result = service.translate(new TranslateRequest("xin chao", null, null));

        assertThat(result.mainResult()).isEqualTo("Hi");
        verify(bodySpec, times(2)).retrieve();
    }
}
