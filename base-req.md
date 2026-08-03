# Prompt cho Claude Code — Web App Dịch Thuật Tiếng Anh Chuyên Nghiệp Cho Dev

Copy toàn bộ nội dung dưới đây và đưa cho Claude Code để bắt đầu implement.

---

## 1. Bối cảnh & Mục tiêu

Xây dựng một web app hỗ trợ dịch văn bản tiếng Việt sang tiếng Anh chuyên nghiệp, phục vụ dân IT (dev, lead, BA, PM...) trong công việc hàng ngày: họp online, làm rõ yêu cầu, comment Jira, chat với team...

App không chỉ dịch đơn thuần, mà còn:
- Điều chỉnh câu chữ, ngữ pháp cho tự nhiên và chuyên nghiệp hơn, phù hợp với **role** người dùng đang đóng và **ngữ cảnh** giao tiếp cụ thể.
- Đưa ra nhận xét/gợi ý cải thiện (analysis) cho bản dịch.
- Cho phép lưu lại kết quả như một **note** để tra cứu, học tập lại sau này.

## 2. Tech stack

- **Backend**: Java 21, Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Validation)
- **Database**: PostgreSQL (dùng Flyway để quản lý migration)
- **AI/LLM**: Gọi Anthropic Claude API (model `claude-sonnet-4-6` hoặc tương đương) qua REST để thực hiện dịch + phân tích. Đóng gói thành 1 `TranslationService` riêng, dễ thay provider sau này (OpenAI, v.v.) nếu cần — dùng interface + implementation.
- **Frontend**: React (Vite) + TypeScript, gọi REST API của backend. Dùng Tailwind CSS + `lucide-react` (icon) để UI đơn giản nhưng đẹp mắt, hiện đại, thân thiện — không chỉ là form CRUD khô khan (chi tiết ở mục 8).
- **Build**: Maven cho backend.
- **Auth**: Chưa cần multi-user phức tạp ở bản đầu — giả định single-user hoặc auth đơn giản bằng API key/local login (ghi rõ giả định này, có thể mở rộng sau bằng Spring Security + JWT).

> Nếu bạn muốn dùng Thymeleaf (server-side render) thay vì React SPA, có thể nói rõ; mặc định prompt này build theo hướng REST API + SPA để tách bạch rõ ràng và dễ mở rộng sau này (mobile app, v.v.).

## 3. Chức năng chi tiết

### 3.1 Quản lý Role (vai trò)
- CRUD cho Role: `name` (VD: Developer, Tech Lead, BA, PM, Tester...), `description` (mô tả ngắn để AI hiểu văn phong phù hợp với role này, VD: "Developer: văn phong kỹ thuật, súc tích, đi thẳng vào vấn đề").
- Seed sẵn vài role mặc định: Developer, Tech Lead, BA, PM, QA/Tester.
- User có thể thêm/sửa/xoá role tuỳ ý.

### 3.2 Quản lý Context (ngữ cảnh giao tiếp)
- CRUD cho Context: `name` (VD: Meeting online, Clarify requirement, Comment Jira, Team chat...), `description` (mô tả để AI biết mức độ trang trọng, độ dài câu phù hợp).
- Seed sẵn: Meeting online, Clarify requirement, Comment Jira, Team chat, Email/Report.
- User có thể thêm/sửa/xoá context tuỳ ý.

### 3.3 Dịch thuật & Chỉnh sửa (chức năng lõi)

**Quan trọng: input KHÔNG bắt buộc là tiếng Việt.** User có thể nhập tiếng Việt hoặc tiếng Anh (thậm chí pha trộn cả hai, kiểu dân IT hay gõ). Agent cần **tự nhận diện ngôn ngữ đầu vào** và xử lý theo 2 nhánh:
- Nếu input là **tiếng Việt** → dịch sang tiếng Anh, đồng thời chuẩn hoá văn phong theo role/context.
- Nếu input **đã là tiếng Anh** → KHÔNG dịch, mà **chỉnh sửa/nâng cấp** câu đó: sửa ngữ pháp, chọn từ tự nhiên/chuyên nghiệp hơn, đúng văn phong phù hợp role/context — output vẫn là tiếng Anh, không phải bản dịch song song.
- Nếu input **pha trộn** (VD chêm tiếng Anh vào câu tiếng Việt) → hiểu là tiếng Việt, dịch/chuẩn hoá toàn bộ sang tiếng Anh.

Flow:
1. User chọn 1 Role + 1 Context (2 dropdown, có thể để trống = "chung chung").
2. User nhập văn bản (tiếng Việt hoặc tiếng Anh, textarea).
3. Gửi lên backend → backend gọi AI với prompt được build động dựa trên role + context đã chọn; để AI tự nhận diện ngôn ngữ input (không cần FE detect trước).
4. Trả về:
   - **`detectedLanguage`**: ngôn ngữ AI nhận diện được ở input (`vi` / `en` / `mixed`), để FE hiển thị cho user biết agent đã hiểu đúng ý chưa.
   - **Bản kết quả chính** (tiếng Anh, đã chuẩn hoá ngữ pháp, chuyên nghiệp, phù hợp role/context — dù input là tiếng Việt hay tiếng Anh, output field này luôn là bản tiếng Anh tốt nhất).
   - **1-2 phương án thay thế** (option khác về văn phong: formal hơn/casual hơn, hoặc ngắn gọn hơn).
   - **Phân tích/gợi ý cải thiện**: các điểm về ngữ pháp, từ vựng, cách diễn đạt của input gốc (dù là tiếng Việt hay tiếng Anh) có thể cải thiện, giải thích ngắn gọn tại sao được điều chỉnh như vậy (giúp user học được qua từng lần dùng — kể cả khi họ đã viết tiếng Anh sẵn).
- Thiết kế prompt gửi cho AI cần system prompt rõ ràng, đưa role description + context description vào, yêu cầu AI **tự phát hiện ngôn ngữ input** và xử lý theo đúng 2 nhánh trên, đồng thời trả về **JSON có cấu trúc** (để backend parse dễ dàng), ví dụ:
```json
{
  "detectedLanguage": "vi",
  "suggestedTitle": "Xin gia hạn deadline estimate task",
  "mainResult": "...",
  "alternatives": ["...", "..."],
  "analysis": [
    {"point": "Ngữ pháp", "comment": "..."},
    {"point": "Từ vựng", "comment": "..."}
  ]
}
```
- Xử lý lỗi khi AI trả JSON không hợp lệ (retry hoặc fallback thông báo lỗi rõ ràng cho user).

### 3.4 Lưu Note
- Sau khi có kết quả dịch/chỉnh sửa, user bấm "Save as Note".
- **Agent tự động sinh ra một `title` (tên note) ngắn gọn, dễ gợi nhớ** dựa trên nội dung + role + context, để user dễ tìm lại sau này (VD input về việc xin thêm thời gian estimate task → title gợi ý: "Xin gia hạn deadline estimate task"). Title nên ngắn (khoảng 5-8 từ), nêu đúng trọng tâm nội dung, không cần dịch — có thể để tiếng Việt hoặc tiếng Anh tuỳ theo ngôn ngữ input gốc cho tự nhiên.
  - Cách làm: xin luôn field `suggestedTitle` trong cùng JSON response của `/api/translate` (đỡ phải gọi AI thêm 1 lần), để FE hiển thị sẵn title gợi ý ngay khi có kết quả dịch.
  - User có thể sửa lại title này trước khi lưu, hoặc sửa lại sau trong màn hình chi tiết note — title do AI gen chỉ là gợi ý mặc định, không ép buộc.
- Note lưu: `title`, văn bản input gốc (kèm ngôn ngữ đã nhận diện: vi/en/mixed), bản kết quả tiếng Anh (chính + alternatives), phân tích/gợi ý, Role tag, Context tag, thời gian tạo.
- User có thể sửa lại note thủ công trước/sau khi lưu (VD: chọn 1 trong các alternative làm bản chính).

### 3.5 Quản lý Notes (thư viện tra cứu)
- Danh sách note dạng list/card, hiển thị: tiếng Việt (rút gọn), tiếng Anh, tag role, tag context, ngày tạo.
- Filter theo Role, theo Context, search full-text theo nội dung (tiếng Việt hoặc tiếng Anh).
- Xem chi tiết 1 note (đầy đủ bản dịch + phân tích).
- Xoá / sửa note.

## 4. Kiến trúc đề xuất (backend)

```
com.example.translator
 ├── role/          (Role entity, repository, service, controller)
 ├── context/       (Context entity, repository, service, controller)
 ├── note/          (Note entity, repository, service, controller)
 ├── translation/   (TranslationController, TranslationService interface,
 │                   ClaudeTranslationService impl, DTO cho request/response AI)
 ├── config/        (cấu hình RestClient/WebClient gọi Claude API, CORS, v.v.)
 └── common/        (exception handler, DTO chung, validation)
```

- Áp dụng layered architecture: Controller → Service → Repository.
- DTO riêng cho API layer, không expose Entity trực tiếp.
- Global exception handler trả lỗi dạng JSON chuẩn (status, message, timestamp).

## 5. Database schema (gợi ý)

- `roles`: id, name, description, created_at
- `contexts`: id, name, description, created_at
- `notes`: id, title (do AI gợi ý, user có thể sửa), original_text (input gốc, có thể vi/en/mixed), detected_language (vi/en/mixed), english_result (bản kết quả tiếng Anh chính), alternatives (JSON hoặc bảng con), analysis (JSON hoặc bảng con), role_id (FK), context_id (FK), created_at, updated_at

Dùng Flyway migration scripts (`V1__init.sql`, ...) để tạo schema + seed data mặc định cho roles/contexts.

## 6. API endpoints (gợi ý)

```
GET    /api/roles
POST   /api/roles
PUT    /api/roles/{id}
DELETE /api/roles/{id}

GET    /api/contexts
POST   /api/contexts
PUT    /api/contexts/{id}
DELETE /api/contexts/{id}

POST   /api/translate            # body: { text, roleId, contextId } -> AI tự nhận diện ngôn ngữ input (vi/en/mixed),
                                  # trả detectedLanguage + kết quả tiếng Anh đã chuẩn hoá + phân tích (không lưu)

GET    /api/notes                # hỗ trợ query param: roleId, contextId, keyword (search theo title + nội dung), page, size
POST   /api/notes                # lưu note (thường gọi sau khi có kết quả từ /translate)
GET    /api/notes/{id}
PUT    /api/notes/{id}
DELETE /api/notes/{id}
```

## 7. Tích hợp AI

- Đọc API key từ biến môi trường (`ANTHROPIC_API_KEY`), không hardcode.
- Dùng `RestClient` (Spring 6) hoặc `WebClient` để gọi `https://api.anthropic.com/v1/messages`.
- System prompt cần nêu rõ: "Bạn là trợ lý ngôn ngữ Việt-Anh chuyên cho dân IT. Input có thể là tiếng Việt, tiếng Anh, hoặc pha trộn — hãy tự nhận diện ngôn ngữ. Nếu input là tiếng Việt (hoặc pha trộn), hãy dịch sang tiếng Anh tự nhiên, chuyên nghiệp, đúng ngữ pháp, phù hợp vai trò và ngữ cảnh. Nếu input đã là tiếng Anh, đừng dịch, mà hãy sửa ngữ pháp và nâng cấp văn phong cho tự nhiên/chuyên nghiệp hơn, phù hợp vai trò và ngữ cảnh. Luôn trả về JSON đúng schema, không kèm giải thích ngoài JSON."
- Có timeout hợp lý (VD 15-20s) và xử lý lỗi network/timeout rõ ràng, trả message thân thiện cho FE.

## 8. Yêu cầu UI/UX (frontend)

**Thẩm mỹ**: đơn giản không có nghĩa là sơ sài. UI cần **đẹp mắt, hiện đại, thân thiện**, cảm giác như một sản phẩm SaaS chỉn chu chứ không phải form CRUD khô khan. Cụ thể:
- Dùng Tailwind CSS + một bộ màu chủ đạo nhất quán (VD: 1 màu primary cho action chính, neutral gray cho nền/text phụ), có dark mode là điểm cộng nhưng không bắt buộc.
- Typography rõ ràng, khoảng cách (spacing) thoáng, bo góc mềm mại (rounded corners), có shadow/border nhẹ để phân tách khối nội dung thay vì kẻ line cứng.
- Có micro-interaction cơ bản: loading state (spinner/skeleton) khi gọi AI dịch (vì có thể mất vài giây), animation chuyển mượt khi hiện kết quả, toast/notification nhỏ khi lưu note thành công hoặc có lỗi.
- Icon sử dụng nhất quán (gợi ý dùng bộ icon như `lucide-react`) thay vì text thuần cho các action (copy, save, delete, edit...).
- Nút "Copy" nhanh cho bản dịch chính (vì user sẽ hay copy-paste kết quả vào Jira/chat/email) — đây là chi tiết nhỏ nhưng ảnh hưởng lớn đến trải nghiệm thực tế.
- Responsive tối thiểu cho màn hình laptop phổ biến (không cần tối ưu mobile sâu, nhưng không được vỡ layout).

**Bố cục chức năng**:
- Trang chính: 2 dropdown (Role, Context) + textarea nhập văn bản (placeholder gợi ý ví dụ câu, đặt cảm hứng dùng thử) + nút "Dịch".
- Kết quả hiển thị: bản kết quả chính nổi bật (font lớn hơn, dễ đọc), badge nhỏ hiển thị `detectedLanguage` để user biết agent hiểu input là tiếng gì; các alternative dạng tab/toggle gọn; phần phân tích hiển thị dạng list có icon/màu phân loại (VD: chấm màu cam cho "Ngữ pháp", xanh cho "Từ vựng") để dễ quét mắt.
- Nút "Lưu note" ngay dưới kết quả, có phản hồi rõ ràng khi lưu thành công.
- Trang quản lý Role/Context: bảng đơn giản + modal thêm/sửa (tránh chuyển trang liên tục, giữ trải nghiệm mượt).
- Trang Notes: dạng card list, mỗi card hiển thị **title (nổi bật, làm tiêu đề card)** + input rút gọn + kết quả rút gọn + tag role/context (dạng pill/badge có màu) + ngày tạo; có bộ lọc theo role/context và ô tìm kiếm nổi bật ở đầu trang (search theo title + nội dung).
- Ưu tiên tốc độ thao tác (vì đây là tool dùng nhanh trong lúc làm việc) — hạn chế số click cần thiết để hoàn thành 1 tác vụ dịch.

## 9. Yêu cầu phi chức năng

- Viết unit test cho service layer (đặc biệt logic build prompt và parse response AI).
- Validate input (không cho dịch text rỗng, giới hạn độ dài text hợp lý, VD 2000 ký tự).
- README hướng dẫn: cách set `ANTHROPIC_API_KEY`, cách chạy backend (`mvn spring-boot:run`), chạy frontend (`npm run dev`), cách migrate DB.
- Cấu hình `application.yml` tách theo profile (`local`, `prod`).

## 10. Việc cần làm (đề xuất thứ tự triển khai)

1. Khởi tạo project Spring Boot + cấu hình DB + Flyway + entity Role/Context/Note.
2. CRUD API cho Role và Context (kèm seed data mặc định).
3. Tích hợp Claude API cho `/api/translate`, thiết kế prompt + parse JSON response.
4. API cho Note (CRUD + filter/search).
5. Frontend: trang dịch thuật chính.
6. Frontend: trang quản lý Role/Context.
7. Frontend: trang Notes (list, filter, detail).
8. Viết test, hoàn thiện README, polish UI.

---

**Ghi chú cho Claude Code**: Nếu có phần nào chưa rõ (VD: cách xác thực user, deploy ở đâu), hãy đưa ra giả định hợp lý, ghi chú lại trong README, và tiếp tục triển khai thay vì dừng lại hỏi quá nhiều — có thể hỏi lại 1 lần nếu thực sự cần thiết trước khi bắt đầu code.
