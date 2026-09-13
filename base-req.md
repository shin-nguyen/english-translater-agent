# Prompt cho Claude Code — Web App Dịch Thuật Tiếng Anh Chuyên Nghiệp Cho Dev

Copy toàn bộ nội dung dưới đây và đưa cho Claude Code (hoặc bất kỳ coding agent nào) để build từ đầu ra một web app tương đương.

---

## 1. Bối cảnh & Mục tiêu

Xây dựng một web app hỗ trợ dịch văn bản tiếng Việt sang tiếng Anh chuyên nghiệp, phục vụ dân IT (dev, lead, BA, PM, QA...) trong công việc hàng ngày: họp online, làm rõ yêu cầu, comment Jira, chat với team, viết email/report.

App không chỉ dịch đơn thuần, mà còn:
- Điều chỉnh câu chữ, ngữ pháp cho tự nhiên và chuyên nghiệp hơn, phù hợp với **role** người dùng đang đóng và **ngữ cảnh** giao tiếp cụ thể.
- Đưa ra nhận xét/gợi ý cải thiện (analysis) cho bản dịch, trích dẫn chính xác phần đã sửa trong input gốc để user học được qua từng lần dùng.
- Đưa ra 1-2 phương án thay thế (alternatives) kèm nhãn văn phong và lý do nên dùng khi nào.
- Cho phép lưu lại kết quả như một **note** để tra cứu, học tập lại sau này.
- Là app **nhiều người dùng** (multi-user) có đăng nhập riêng, không phải single-user/demo — mỗi user có thư viện note riêng.
- Cho phép **admin cấu hình nhiều AI model song song** (khác provider/API key khác nhau) qua UI, không hardcode/env var — user chọn model muốn dùng mỗi lần dịch.

## 2. Tech stack

- **Backend**: Java 21, Spring Boot 3.x (Spring Web, Spring Data JPA, Spring Validation, **Spring Security**).
- **Database**: PostgreSQL, dùng Flyway để quản lý migration.
- **Auth**: JWT bearer token, stateless (không session), mật khẩu hash bằng BCrypt. Hai role hệ thống: `ADMIN` và `USER`. **Tài khoản đăng ký đầu tiên tự động thành `ADMIN`** (không seed sẵn tài khoản admin, không cần invite flow); mọi tài khoản đăng ký sau đó là `USER` thường. Admin có thể promote/demote, bật/tắt, xoá user khác — nhưng **không được phép hạ quyền/khoá/xoá admin cuối cùng còn lại trong hệ thống** (tránh khoá hết quyền quản trị).
- **AI/LLM**: Không hardcode 1 provider/API key cố định. Thiết kế provider-agnostic:
  - 1 interface `TranslationService` + 1 implementation dùng chung logic build prompt/parse response/retry.
  - 1 interface `AiProviderClient` với ít nhất 2 implementation: gọi **Anthropic Messages API** (`https://api.anthropic.com/v1/messages`) và gọi **bất kỳ endpoint Chat Completions tương thích OpenAI** nào (để dùng được với OpenRouter, hoặc server tự host như vLLM/llama.cpp/Ollama).
  - Thông tin kết nối AI model (provider, base URL, API key, model id, max tokens, timeout...) là **dữ liệu lưu trong DB, quản lý qua admin UI** — KHÔNG dùng biến môi trường kiểu `ANTHROPIC_API_KEY`. Cho phép nhiều model config tồn tại song song, mỗi model có thể bật/tắt (`enabled`) và đánh dấu 1 model mặc định (`isDefault`, tối đa 1 model được là default). API key phải được **mã hoá at rest** (VD AES-256-GCM qua JPA `AttributeConverter`), không bao giờ trả lại API key qua API kể cả cho admin.
  - User chọn 1 AI model (trong số các model đang `enabled`) mỗi khi dịch — không có model mặc định ẩn hay failover tự động giữa các provider.
- **Frontend**: React (Vite) + TypeScript, gọi REST API của backend. Dùng Tailwind CSS + `lucide-react` (icon) để UI đơn giản nhưng đẹp mắt, hiện đại, thân thiện, có dark mode toggle — không chỉ là form CRUD khô khan (chi tiết ở mục 9).
- **Build**: Maven cho backend, npm/Vite cho frontend. Viết Dockerfile multi-stage cho cả backend/frontend + `docker-compose.yml` (Postgres + backend + frontend) để chạy full stack bằng 1 lệnh.

## 3. Xác thực & phân quyền người dùng

- `POST /api/auth/signup`: email + password (tối thiểu 8 ký tự) + displayName. Người đăng ký đầu tiên trong hệ thống nhận `appRole = ADMIN`, tất cả người sau là `USER`.
- `POST /api/auth/login`: trả JWT + thông tin user.
- `GET /api/auth/me`: thông tin user đang đăng nhập (đọc từ JWT).
- `POST /api/auth/change-password`: đổi mật khẩu, yêu cầu nhập đúng mật khẩu hiện tại.
- Endpoint quản lý user (`/api/users/**`) chỉ dành cho `ADMIN`: liệt kê, tạo user mới, đổi role, bật/tắt tài khoản, xoá user.
- Mọi endpoint trừ `signup`/`login` yêu cầu JWT hợp lệ (`Authorization: Bearer <token>`).
- **Roles/Contexts (khái niệm "vai trò giao tiếp" ở mục 4.1/4.2 — khác hoàn toàn với `ADMIN`/`USER` ở trên) vẫn dùng chung, mở cho MỌI user đã đăng nhập**, không giới hạn admin — quyết định thiết kế có chủ đích, không phải thiếu sót.
- **Notes phải riêng tư tuyệt đối theo từng user, kể cả admin cũng không được xem note của người khác.** Truy cập note không thuộc về mình phải trả `404` (không phải `403`), để không lộ việc note đó có tồn tại hay không.
- Quản lý AI model (`/api/ai-models/**`, trừ endpoint liệt kê rút gọn cho việc chọn model) chỉ dành cho `ADMIN`.

## 4. Chức năng chi tiết

### 4.1 Quản lý Role (vai trò giao tiếp — không phải role phân quyền)
- CRUD cho Role: `name` (VD: Developer, Tech Lead, BA, PM, Tester...), `description` (mô tả ngắn để AI hiểu văn phong phù hợp với role này).
- Seed sẵn vài role mặc định: Developer, Tech Lead, BA, PM, QA/Tester.
- Bất kỳ user nào đã đăng nhập có thể thêm/sửa/xoá role tuỳ ý.

### 4.2 Quản lý Context (ngữ cảnh giao tiếp)
- CRUD cho Context: `name` (VD: Meeting online, Clarify requirement, Comment Jira, Team chat...), `description` (mô tả mức độ trang trọng, độ dài câu phù hợp).
- Seed sẵn: Meeting online, Clarify requirement, Comment Jira, Team chat, Email/Report.
- Bất kỳ user nào đã đăng nhập có thể thêm/sửa/xoá context tuỳ ý.

### 4.3 Quản lý AI Model (admin)
- CRUD (admin-only) cho AI model config: `label` (tên gợi nhớ), `provider` (enum, VD `ANTHROPIC` / `OPENAI_COMPATIBLE`), `baseUrl`, `apiKey` (chỉ ghi, không đọc lại qua API), `modelIdentifier`, `apiVersion` (tuỳ chọn), `maxTokens`, `timeoutSeconds`, `enabled`, `isDefault`.
- Ràng buộc tối đa 1 config `isDefault = true` tại một thời điểm (nên enforce cả ở DB constraint lẫn service).
- 1 endpoint riêng, cho **mọi user đã đăng nhập** gọi được (không cần quyền admin): trả danh sách rút gọn `{id, label}` các model đang `enabled`, để FE hiển thị dropdown chọn model lúc dịch.

### 4.4 Dịch thuật & Chỉnh sửa (chức năng lõi)

**Quan trọng: input KHÔNG bắt buộc là tiếng Việt.** User có thể nhập tiếng Việt hoặc tiếng Anh (thậm chí pha trộn cả hai, kiểu dân IT hay gõ). Agent cần **tự nhận diện ngôn ngữ đầu vào** và xử lý theo 2 nhánh:
- Nếu input là **tiếng Việt** → dịch sang tiếng Anh, đồng thời chuẩn hoá văn phong theo role/context.
- Nếu input **đã là tiếng Anh** → KHÔNG dịch, mà **chỉnh sửa/nâng cấp** câu đó: sửa ngữ pháp, chọn từ tự nhiên/chuyên nghiệp hơn, đúng văn phong phù hợp role/context — output vẫn là tiếng Anh, không phải bản dịch song song.
- Nếu input **pha trộn** (VD chêm tiếng Anh vào câu tiếng Việt) → hiểu là tiếng Việt, dịch/chuẩn hoá toàn bộ sang tiếng Anh.

Flow:
1. User chọn 1 Role + 1 Context (2 dropdown, có thể để trống = "chung chung") + **1 AI Model** (dropdown bắt buộc, lấy từ danh sách model đang enabled).
2. User nhập văn bản (tiếng Việt hoặc tiếng Anh, textarea, giới hạn tối đa 2000 ký tự, hiển thị bộ đếm ký tự).
3. Gửi lên backend → backend build system prompt động dựa trên role + context đã chọn, lấy đúng AI model config theo `modelConfigId`, gọi provider tương ứng; để AI tự nhận diện ngôn ngữ input (không cần FE detect trước).
4. Trả về:
   - **`detectedLanguage`**: ngôn ngữ AI nhận diện được ở input (`vi` / `en` / `mixed`).
   - **`suggestedTitle`**: tiêu đề gợi ý **bằng tiếng Anh, Title Case**, khoảng 4-7 từ, nêu cụ thể chủ đề/hành động/đối tượng chính (không dùng cụm chung chung như "Work Update" nếu nội dung có chi tiết cụ thể hơn) — sinh trong CÙNG lần gọi AI, không gọi thêm lượt riêng.
   - **`mainResult`**: bản kết quả chính (tiếng Anh, đã chuẩn hoá ngữ pháp, chuyên nghiệp, phù hợp role/context — dù input là tiếng Việt hay tiếng Anh, field này luôn là bản tiếng Anh tốt nhất).
   - **`alternatives`**: tối đa 2 phương án thay thế, mỗi phương án gồm `text` (nội dung), `style` (nhãn ngắn 1-2 từ tiếng Anh, VD Formal/Casual/Concise/Direct/Diplomatic), `reason` (giải thích ngắn khi nào nên dùng). Mảng rỗng nếu không có phương án nào đáng đưa ra.
   - **`analysis`**: tối đa 3 điểm phân tích, mỗi điểm gồm `original` (trích dẫn CHÍNH XÁC từ input gốc), `improved` (phần tương ứng đã sửa trong `mainResult`), `reason` (giải thích ngắn tại sao sửa như vậy). Mảng rỗng nếu input đã hoàn hảo.
5. Endpoint dịch **không lưu note** — chỉ trả kết quả; lưu là hành động riêng của user (mục 4.5).

- Thiết kế prompt gửi cho AI cần system prompt rõ ràng, đưa role description + context description vào, yêu cầu AI **tự phát hiện ngôn ngữ input** và xử lý theo đúng 2 nhánh trên, đồng thời trả về **JSON có cấu trúc** đúng schema ở trên (để backend parse dễ dàng), không kèm markdown code fence hay giải thích ngoài JSON.
- Xử lý lỗi khi AI trả JSON không hợp lệ: **retry 1 lần** với prompt yêu cầu nghiêm ngặt hơn về format; nếu vẫn lỗi thì trả message lỗi rõ ràng, thân thiện cho user thay vì crash hoặc bịa kết quả.

### 4.5 Lưu Note
- Sau khi có kết quả dịch/chỉnh sửa, user bấm "Save as Note".
- Title mặc định lấy từ `suggestedTitle` trả về ở bước dịch, user có thể sửa lại trước khi lưu hoặc sửa lại sau trong màn hình chi tiết note.
- Note lưu: `title`, văn bản input gốc, ngôn ngữ đã nhận diện (vi/en/mixed), bản kết quả tiếng Anh chính, alternatives, analysis, Role tag, Context tag, **user sở hữu note (bắt buộc)**, thời gian tạo/cập nhật.
- User có thể sửa lại note thủ công sau khi lưu (VD: chọn 1 trong các alternative làm bản chính, đổi title).

### 4.6 Quản lý Notes (thư viện tra cứu, riêng theo từng user)
- Danh sách note **chỉ hiển thị note của chính user đang đăng nhập** — dạng list/card, hiển thị: title, input rút gọn, output rút gọn, tag role, tag context, ngày tạo.
- Filter theo Role, theo Context, search full-text theo title + nội dung (tiếng Việt hoặc tiếng Anh), có phân trang.
- Xem chi tiết 1 note (đầy đủ bản dịch + phân tích) — 404 nếu note không thuộc user hiện tại.
- Xoá / sửa note — chỉ trên note của chính mình.

## 5. Kiến trúc đề xuất (backend)

```
com.example.translator
 ├── auth/          (signup/login/me/change-password)
 ├── user/          (AppUser entity, AppRole enum, admin quản lý user)
 ├── security/      (JWT filter/service, Spring Security config, UserDetails, entry point/access-denied handler)
 ├── aimodel/       (AiModelConfig entity, AiProviderType enum, API-key encryption converter, CRUD + endpoint liệt kê rút gọn)
 ├── role/          (Role entity, repository, service, controller — vai trò giao tiếp)
 ├── context/       (Context entity, repository, service, controller)
 ├── note/          (Note entity, repository, service, controller — scoped theo user)
 ├── translation/   (TranslationController, TranslationService interface, TranslationServiceImpl,
 │                   AiProviderClient interface + implementations theo provider, DTO cho request/response AI)
 ├── config/        (cấu hình CORS, v.v.)
 └── common/        (exception handler, DTO chung, validation)
```

- Áp dụng layered architecture: Controller → Service → Repository.
- DTO riêng cho API layer, không expose Entity trực tiếp.
- Global exception handler trả lỗi dạng JSON chuẩn (status, message, timestamp, path, details).
- Dùng `@PreAuthorize`/method security để enforce phân quyền admin ở tầng controller.

## 6. Database schema (gợi ý)

- `app_users`: id, email (unique), password_hash, display_name, app_role (`ADMIN`/`USER`), enabled, created_at, updated_at.
- `roles`: id, name (unique), description, created_at.
- `contexts`: id, name (unique), description, created_at.
- `ai_model_configs`: id, label, provider, base_url, api_key (mã hoá at rest), model_identifier, api_version, max_tokens, timeout_seconds, enabled, is_default (ràng buộc tối đa 1 default), created_at, updated_at.
- `notes`: id, title, original_text, detected_language (vi/en/mixed), english_result, alternatives (JSON), analysis (JSON), role_id (FK, nullable), context_id (FK, nullable), **user_id (FK → app_users, NOT NULL)**, created_at, updated_at.

Dùng Flyway migration scripts (`V1__init.sql`, ...) để tạo schema + seed data mặc định cho roles/contexts. Không seed sẵn tài khoản hay AI model — user tự signup và tự cấu hình AI model qua UI sau khi có tài khoản admin.

## 7. API endpoints (gợi ý)

```
POST   /api/auth/signup             # public
POST   /api/auth/login              # public
GET    /api/auth/me                 # authenticated
POST   /api/auth/change-password    # authenticated

GET    /api/users                   # ADMIN only
POST   /api/users                   # ADMIN only
GET    /api/users/{id}              # ADMIN only
PATCH  /api/users/{id}/role         # ADMIN only
PATCH  /api/users/{id}/status       # ADMIN only
DELETE /api/users/{id}              # ADMIN only

GET    /api/roles                   # authenticated (mọi role)
POST   /api/roles
PUT    /api/roles/{id}
DELETE /api/roles/{id}

GET    /api/contexts                # authenticated
POST   /api/contexts
PUT    /api/contexts/{id}
DELETE /api/contexts/{id}

GET    /api/ai-models               # ADMIN only — đầy đủ (không kèm apiKey)
POST   /api/ai-models               # ADMIN only
PUT    /api/ai-models/{id}          # ADMIN only
DELETE /api/ai-models/{id}          # ADMIN only
GET    /api/ai-models/enabled       # authenticated — rút gọn {id, label} để chọn model

POST   /api/translate               # authenticated; body { text, roleId?, contextId?, modelConfigId }
                                     # -> AI tự nhận diện ngôn ngữ input (vi/en/mixed),
                                     # trả detectedLanguage + suggestedTitle + mainResult + alternatives + analysis (không lưu)

GET    /api/notes                   # authenticated, scoped theo user; query: roleId, contextId, keyword, page, size
POST   /api/notes                   # lưu note (thường gọi sau khi có kết quả từ /translate)
GET    /api/notes/{id}              # 404 nếu không thuộc user hiện tại
PUT    /api/notes/{id}
DELETE /api/notes/{id}
```

## 8. Tích hợp AI

- KHÔNG đọc API key từ biến môi trường — API key của từng AI model nằm trong DB (`ai_model_configs`), nhập qua admin UI, mã hoá at rest bằng key mã hoá riêng đọc từ biến môi trường (VD `AI_MODEL_ENCRYPTION_KEY`, không có default ở môi trường production — fail fast nếu thiếu).
- Dùng `RestClient`/`WebClient` (Spring 6) để gọi API của provider tương ứng theo config được chọn.
- System prompt cần nêu rõ: "Bạn là trợ lý ngôn ngữ Việt-Anh chuyên cho dân IT. Input có thể là tiếng Việt, tiếng Anh, hoặc pha trộn — hãy tự nhận diện ngôn ngữ. Nếu input là tiếng Việt (hoặc pha trộn), hãy dịch sang tiếng Anh tự nhiên, chuyên nghiệp, đúng ngữ pháp, phù hợp vai trò và ngữ cảnh. Nếu input đã là tiếng Anh, đừng dịch, mà hãy sửa ngữ pháp và nâng cấp văn phong cho tự nhiên/chuyên nghiệp hơn, phù hợp vai trò và ngữ cảnh. Luôn trả về JSON đúng schema, không kèm giải thích ngoài JSON."
- Có timeout hợp lý theo từng model config (VD mặc định 15-20s) và xử lý lỗi network/timeout rõ ràng, trả message thân thiện cho FE.
- Retry 1 lần khi AI trả JSON không hợp lệ trước khi báo lỗi cho user.

## 9. Yêu cầu UI/UX (frontend)

**Thẩm mỹ**: đơn giản không có nghĩa là sơ sài. UI cần **đẹp mắt, hiện đại, thân thiện**, cảm giác như một sản phẩm SaaS chỉn chu chứ không phải form CRUD khô khan. Cụ thể:
- Dùng Tailwind CSS + một bộ màu chủ đạo nhất quán (VD: 1 màu primary cho action chính, neutral gray cho nền/text phụ), **có dark mode toggle** ở header.
- Typography rõ ràng, khoảng cách (spacing) thoáng, bo góc mềm mại (rounded corners), có shadow/border nhẹ để phân tách khối nội dung thay vì kẻ line cứng.
- Có micro-interaction cơ bản: loading state (spinner/skeleton) khi gọi AI dịch (vì có thể mất vài giây), animation chuyển mượt khi hiện kết quả, toast/notification nhỏ khi lưu note thành công hoặc có lỗi.
- Icon sử dụng nhất quán (gợi ý dùng bộ icon như `lucide-react`) thay vì text thuần cho các action (copy, save, delete, edit, logout...).
- Nút "Copy" nhanh cho bản dịch chính (vì user sẽ hay copy-paste kết quả vào Jira/chat/email) — đây là chi tiết nhỏ nhưng ảnh hưởng lớn đến trải nghiệm thực tế.
- Responsive tối thiểu cho màn hình laptop phổ biến (không cần tối ưu mobile sâu, nhưng không được vỡ layout) — nav có thể ẩn label chỉ giữ icon ở màn hình nhỏ.

**Bố cục chức năng**:
- Trang **Login/Signup** riêng biệt, không nằm trong layout chính, redirect về login nếu chưa đăng nhập.
- Trang chính (Translate): 3 dropdown (Role, Context, **AI Model**) + textarea nhập văn bản (có đếm ký tự, giới hạn 2000, placeholder gợi ý ví dụ câu) + nút "Dịch" (disable nếu chưa chọn model hoặc chưa nhập text). Cảnh báo rõ nếu chưa có AI model nào được cấu hình.
- Kết quả hiển thị: bản kết quả chính nổi bật (font lớn hơn, dễ đọc), badge nhỏ hiển thị `detectedLanguage` (màu khác nhau theo vi/en/mixed) để user biết agent hiểu input là tiếng gì; các alternative dạng tab/toggle gọn kèm style label; phần phân tích hiển thị dạng list rõ ràng (original → improved + lý do) để dễ quét mắt.
- Nút "Lưu note" ngay dưới kết quả kèm ô sửa title, có phản hồi rõ ràng khi lưu thành công.
- Trang quản lý Role/Context: bảng đơn giản + modal thêm/sửa (tránh chuyển trang liên tục, giữ trải nghiệm mượt), mở cho mọi user.
- Trang Notes: dạng card list, mỗi card hiển thị **title (nổi bật, làm tiêu đề card)** + input rút gọn + kết quả rút gọn + tag role/context (dạng pill/badge có màu) + ngày tạo; có bộ lọc theo role/context và ô tìm kiếm nổi bật ở đầu trang (search theo title + nội dung).
- Trang Account: xem thông tin user hiện tại + đổi mật khẩu.
- Trang Admin Users và Admin AI Models: chỉ hiển thị trong nav và chỉ truy cập được khi đăng nhập bằng tài khoản `ADMIN` (route guard riêng, redirect nếu không đủ quyền).
- Ưu tiên tốc độ thao tác (vì đây là tool dùng nhanh trong lúc làm việc) — hạn chế số click cần thiết để hoàn thành 1 tác vụ dịch.

## 10. Yêu cầu phi chức năng

- Viết unit test cho service layer (đặc biệt logic build prompt, parse response AI, mã hoá/giải mã API key, và các rule phân quyền như "không được xoá admin cuối cùng").
- Validate input: không cho dịch text rỗng, giới hạn độ dài text hợp lý (VD 2000 ký tự), password tối thiểu 8 ký tự, email hợp lệ.
- README hướng dẫn: cách set các biến môi trường bắt buộc (JWT secret, key mã hoá AI model), cách chạy backend, chạy frontend, cách migrate DB, cách chạy bằng Docker Compose, cách deploy.
- Cấu hình theo profile (`local`: có default an toàn cho dev, chạy được ngay không cần set biến môi trường; `prod`: bắt buộc set mọi secret, fail fast nếu thiếu).
- CORS cấu hình được qua biến môi trường (danh sách origin cho phép), mặc định mở cho dev server ở `local`, mặc định đóng (rỗng) ở `prod`.

## 11. Việc cần làm (đề xuất thứ tự triển khai)

1. Khởi tạo project Spring Boot + cấu hình DB + Flyway + entity Role/Context/Note.
2. Thiết lập auth: entity AppUser, Spring Security + JWT, signup (first-user-is-admin)/login/me/change-password.
3. CRUD API cho Role và Context (kèm seed data mặc định), mở cho mọi user đã đăng nhập.
4. Entity + CRUD admin cho AI Model config (kèm mã hoá API key), endpoint liệt gọn cho user chọn model.
5. Tích hợp AI: interface provider-agnostic, implementation Anthropic + OpenAI-compatible, `/api/translate` build prompt theo role/context + dispatch theo model được chọn, parse JSON response kèm retry.
6. API cho Note (CRUD + filter/search, scoped theo user, gắn `user_id`).
7. API quản lý User cho admin (list/create/đổi role/bật-tắt/xoá, guard admin cuối cùng).
8. Frontend: Login/Signup + route guard (auth, admin).
9. Frontend: trang dịch thuật chính (Role/Context/Model + kết quả + lưu note).
10. Frontend: trang quản lý Role/Context, trang Notes (list, filter, detail), trang Account.
11. Frontend: trang Admin Users, trang Admin AI Models.
12. Viết test, hoàn thiện README, Dockerfile + docker-compose, polish UI (dark mode, toast, animation).

---

**Ghi chú cho agent triển khai**: Nếu có phần nào chưa rõ (VD: cách deploy cụ thể, chi tiết UI), hãy đưa ra giả định hợp lý, ghi chú lại trong README, và tiếp tục triển khai thay vì dừng lại hỏi quá nhiều — có thể hỏi lại 1 lần nếu thực sự cần thiết trước khi bắt đầu code.
