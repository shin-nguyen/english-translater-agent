package com.example.translator.translation;

import com.example.translator.aimodel.AiModelConfig;
import com.example.translator.aimodel.AiModelConfigService;
import com.example.translator.common.AiServiceException;
import com.example.translator.context.Context;
import com.example.translator.context.ContextRepository;
import com.example.translator.role.Role;
import com.example.translator.role.RoleRepository;
import com.example.translator.translation.TranslationDtos.TranslateRequest;
import com.example.translator.translation.TranslationDtos.TranslateResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TranslationServiceImpl implements TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationServiceImpl.class);
    private static final String UNSPECIFIED = "Không chỉ định (chung chung, không có vai trò/ngữ cảnh cụ thể)";

    private static final String SYSTEM_PROMPT_TEMPLATE = """
            Bạn là trợ lý ngôn ngữ Việt-Anh chuyên cho dân IT (developer, tech lead, BA, PM, QA...), \
            giúp họ giao tiếp chuyên nghiệp hơn trong công việc hàng ngày: họp online, làm rõ yêu cầu, \
            comment Jira, chat với team, viết email/report.

            Input của người dùng có thể là tiếng Việt, tiếng Anh, hoặc pha trộn cả hai (dân IT hay chêm \
            tiếng Anh vào câu tiếng Việt) — hãy tự nhận diện ngôn ngữ đầu vào, KHÔNG cần người dùng khai báo trước.
            - Nếu input là tiếng Việt, hoặc pha trộn (chêm tiếng Anh vào câu tiếng Việt): dịch toàn bộ sang \
            tiếng Anh tự nhiên, đúng ngữ pháp, chuyên nghiệp.
            - Nếu input đã hoàn toàn là tiếng Anh: KHÔNG dịch song song, mà sửa ngữ pháp và nâng cấp văn phong \
            cho tự nhiên/chuyên nghiệp hơn. Output vẫn phải là tiếng Anh, không phải bản dịch.

            Vai trò người dùng đang đóng (điều chỉnh văn phong theo vai trò này): %s
            Ngữ cảnh giao tiếp (điều chỉnh độ trang trọng, độ dài câu theo ngữ cảnh này): %s

            Hãy trả lời bằng DUY NHẤT một JSON object hợp lệ theo ĐÚNG schema dưới đây — không kèm markdown \
            code fence, không kèm bất kỳ lời giải thích hay text nào khác ngoài JSON:
            {
              "detectedLanguage": "vi | en | mixed",
              "suggestedTitle": "tiêu đề bằng TIẾNG ANH, Title Case, khoảng 4-7 từ, nêu CỤ THỂ chủ đề/hành động/đối tượng chính trong nội dung (tên tính năng, bug, deadline, người liên quan...). TUYỆT ĐỐI không dùng cụm chung chung kiểu 'Work Update', 'Team Discussion', 'Status Report' nếu nội dung có chi tiết cụ thể hơn để dùng",
              "mainResult": "bản kết quả tiếng Anh chính, tự nhiên, đúng ngữ pháp, chuyên nghiệp, phù hợp vai trò/ngữ cảnh ở trên",
              "alternatives": [
                {
                  "text": "phương án thay thế bằng tiếng Anh, khác biệt rõ rệt về văn phong so với mainResult",
                  "style": "nhãn ngắn 1-2 từ TIẾNG ANH mô tả phong cách của phương án này, ví dụ: Formal, Casual, Concise, Direct, Diplomatic",
                  "reason": "giải thích ngắn gọn bằng tiếng Việt: nên dùng phương án này khi nào / với ai, so với mainResult"
                }
              ],
              "analysis": [
                {
                  "original": "cụm từ/câu trích dẫn CHÍNH XÁC từ input gốc của người dùng mà bạn đã sửa hoặc dịch theo cách đáng chú ý",
                  "improved": "phần tương ứng trong mainResult sau khi đã sửa/dịch",
                  "reason": "giải thích ngắn gọn TẠI SAO lại sửa/dịch như vậy (quy tắc ngữ pháp, từ ngữ tự nhiên hơn, hoặc lý do chọn cách dịch này)"
                }
              ]
            }
            Với mảng "alternatives": tối đa 2 phương án, mỗi phương án phải thực sự khác biệt về văn \
            phong (không lặp lại ý tương tự nhau hay chỉ đổi vài từ). Nếu không có phương án nào đáng \
            đưa ra, trả về mảng rỗng [].
            Với mảng "analysis": chỉ liệt kê tối đa 3 điểm THỰC SỰ đáng chú ý và hữu ích để người dùng \
            học được điều gì đó — ưu tiên chất lượng hơn số lượng. "original" phải là trích dẫn chính \
            xác từ input gốc, không diễn giải lại. Nếu input đã hoàn hảo hoặc không có điểm gì đáng nói, \
            trả về mảng rỗng [].
            """;

    private static final String RETRY_SUFFIX = """

            QUAN TRỌNG: Ở lượt trước bạn đã trả lời KHÔNG đúng định dạng JSON yêu cầu. \
            Lần này hãy CHỈ trả về một JSON object hợp lệ duy nhất, không có bất kỳ ký tự, \
            markdown, hay lời giải thích nào khác bao quanh nó.
            """;

    private final RoleRepository roleRepository;
    private final ContextRepository contextRepository;
    private final ObjectMapper objectMapper;
    private final AiModelConfigService aiModelConfigService;
    private final AiProviderClientRegistry registry;

    public TranslationServiceImpl(RoleRepository roleRepository,
                                   ContextRepository contextRepository,
                                   ObjectMapper objectMapper,
                                   AiModelConfigService aiModelConfigService,
                                   AiProviderClientRegistry registry) {
        this.roleRepository = roleRepository;
        this.contextRepository = contextRepository;
        this.objectMapper = objectMapper;
        this.aiModelConfigService = aiModelConfigService;
        this.registry = registry;
    }

    @Override
    public TranslateResponse translate(TranslateRequest request) {
        Role role = request.roleId() != null ? roleRepository.findById(request.roleId()).orElse(null) : null;
        Context context = request.contextId() != null ? contextRepository.findById(request.contextId()).orElse(null) : null;

        String systemPrompt = buildSystemPrompt(role, context);

        AiModelConfig config = aiModelConfigService.getEnabledForDispatch(request.modelConfigId());
        AiProviderClient client = registry.get(config.getProvider());

        String rawResponse = client.callModel(config, systemPrompt, request.text());
        try {
            return parseResponse(rawResponse);
        } catch (RuntimeException firstFailure) {
            log.warn("AI response failed JSON parsing, retrying once with stricter instructions: {}", firstFailure.getMessage());
            String retryResponse = client.callModel(config, systemPrompt + RETRY_SUFFIX, request.text());
            try {
                return parseResponse(retryResponse);
            } catch (RuntimeException secondFailure) {
                throw new AiServiceException(
                        "AI đã trả về dữ liệu không đúng định dạng mong đợi, kể cả sau khi thử lại. Vui lòng thử lại sau.",
                        secondFailure);
            }
        }
    }

    String buildSystemPrompt(Role role, Context context) {
        String roleDescription = describe(role != null ? role.getDescription() : null, role != null ? role.getName() : null);
        String contextDescription = describe(context != null ? context.getDescription() : null, context != null ? context.getName() : null);
        return SYSTEM_PROMPT_TEMPLATE.formatted(roleDescription, contextDescription);
    }

    private String describe(String description, String name) {
        if (description == null || description.isBlank()) {
            return name == null || name.isBlank() ? UNSPECIFIED : name;
        }
        return (name == null || name.isBlank() ? "" : name + " — ") + description;
    }

    TranslateResponse parseResponse(String raw) {
        String cleaned = stripCodeFences(raw);
        TranslateResponse parsed = tryParse(cleaned);
        if (parsed == null) {
            String extracted = extractJsonObject(cleaned);
            if (extracted != null) {
                parsed = tryParse(extracted);
            }
        }
        if (parsed == null) {
            throw new IllegalStateException("AI response is not valid JSON: " + truncate(raw));
        }
        if (parsed.mainResult() == null || parsed.mainResult().isBlank()) {
            throw new IllegalStateException("AI response missing mainResult field");
        }
        if (parsed.detectedLanguage() == null || parsed.detectedLanguage().isBlank()) {
            throw new IllegalStateException("AI response missing detectedLanguage field");
        }
        return parsed;
    }

    private TranslateResponse tryParse(String json) {
        try {
            return objectMapper.readValue(json, TranslateResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\r?\\n", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }
}
