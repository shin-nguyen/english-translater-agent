---
name: add-ai-provider
description: Use when asked to add support for a new AI/LLM provider (a new AiProviderClient) to the translator backend, following the existing Anthropic / OpenAI-compatible pattern.
---

# Add a new AI provider client

This backend's AI integration is provider-agnostic by design:
`com.example.translator.translation.AiProviderClient` is a small interface
(`supports()` returns which `AiProviderType` it handles, `callModel(...)`
makes the HTTP call); `AiProviderClientRegistry` collects every Spring bean
that implements it into a `Map<AiProviderType, AiProviderClient>`; the two
existing implementations are `AnthropicProviderClient` and
`OpenAiCompatibleProviderClient`, all in
`backend/src/main/java/com/example/translator/translation/`.

Follow these steps, in order:

1. Read `AiProviderClient.java`, `AiProviderClientRegistry.java`, and BOTH
   existing implementations first. Do not start writing before you've seen
   how the two existing clients build a request and map a response — the
   new one should look like a sibling of those, not a novel design.
2. Add the new provider to the `AiProviderType` enum in
   `com.example.translator.aimodel.AiProviderType`.
3. Create `<Provider>ProviderClient` in the same `translation` package,
   implementing `AiProviderClient`. It only needs to be `@Component`-annotated
   for the registry to pick it up automatically — there is no separate
   registration step to remember.
4. Build a fresh `RestClient` per call from the given `AiModelConfig`
   (baseUrl/model/apiKey) exactly like the existing clients do — do not
   cache a client across calls, so an admin editing the config takes effect
   immediately.
5. Never introduce a second place that stores or logs the raw API key.
   `ApiKeyAttributeConverter` (in `com.example.translator.aimodel`) already
   AES-256-GCM encrypts `AiModelConfig.apiKey` at rest using
   `AI_MODEL_ENCRYPTION_KEY` — that happens transparently via JPA, so as
   long as the new client reads `config.getApiKey()` like the others do, it
   gets this for free. Do not add your own encryption, and do not log the
   key at any log level.
6. Add a test alongside the existing provider-client tests
   (`backend/src/test/java/.../translation/`) that mocks the HTTP call and
   asserts request shape + response mapping, mirroring the existing
   Anthropic/OpenAI-compatible test structure.
7. Only touch the frontend if the new provider needs an admin-form field
   Anthropic/OpenAI-compatible don't already have; if so, follow the
   existing "AI Models" admin page pattern instead of adding a new one.

When done, report: the new enum value, the new class's fully-qualified
name, and which test file you added/extended. Do not report or repeat any
API key value used in a manual test.
