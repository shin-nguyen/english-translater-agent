package com.example.translator.translation;

import com.example.translator.config.AnthropicProperties;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import com.example.translator.translation.ClaudeApiModels.ContentBlock;
import com.example.translator.translation.ClaudeApiModels.MessageResponse;
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
class ClaudeTranslationServiceImplTest {

    @Mock
    private RestClient restClient;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ContextRepository contextRepository;

    private ClaudeTranslationServiceImpl service;

    @BeforeEach
    void setUp() {
        AnthropicProperties properties = new AnthropicProperties();
        properties.setModel("claude-sonnet-5");
        service = new ClaudeTranslationServiceImpl(restClient, properties, roleRepository, contextRepository, new ObjectMapper());
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
    void translate_retriesOnceOnMalformedJsonThenSucceeds() {
        RestClient.RequestBodyUriSpec uriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec bodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec1 = mock(RestClient.ResponseSpec.class);
        RestClient.ResponseSpec responseSpec2 = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(uriSpec);
        when(uriSpec.uri("/v1/messages")).thenReturn(bodySpec);
        when(bodySpec.body(any(ClaudeApiModels.MessageRequest.class))).thenReturn(bodySpec);
        when(bodySpec.retrieve()).thenReturn(responseSpec1, responseSpec2);

        MessageResponse malformed = new MessageResponse("id1", List.of(new ContentBlock("text", "not json")), "end_turn");
        MessageResponse valid = new MessageResponse("id2", List.of(new ContentBlock("text",
                "{\"detectedLanguage\":\"vi\",\"suggestedTitle\":\"t\",\"mainResult\":\"Hi\",\"alternatives\":[],\"analysis\":[]}")), "end_turn");

        when(responseSpec1.body(MessageResponse.class)).thenReturn(malformed);
        when(responseSpec2.body(MessageResponse.class)).thenReturn(valid);

        TranslateResponse result = service.translate(new TranslateRequest("xin chao", null, null));

        assertThat(result.mainResult()).isEqualTo("Hi");
        verify(bodySpec, times(2)).retrieve();
    }
}
