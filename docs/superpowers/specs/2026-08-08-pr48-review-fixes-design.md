# PR #48 Review Fixes Design

**Status:** Approved corrective design
**Scope:** Follow-up corrections for PR #48 / issue #38 only

## Goal

Close the review gaps in PR #48 without redesigning the already-correct Spring AI provider foundation.

## Problems to Correct

1. The application intentionally creates two `ChatModel` beans (`groqChatModel` and `geminiChatModel`), while Spring AI's generic `ChatClient.Builder` auto-configuration expects a unique `ChatModel`. The application must disable that generic builder auto-configuration because it creates the two required `ChatClient` instances explicitly.
2. The new Spring Modulith `ai` module exposes its application-facing types from the nested `ai.api` package, but that package is not currently declared as a named interface. It must be exported in the same way as `chess.api` so later Phase 2 modules can legally depend on `ai :: api`.
3. Existing focused `ApplicationContextRunner` tests do not load the full Spring Boot + Spring AI starter auto-configuration graph. Add one enabled-mode full-context test using dummy provider credentials/models and a mocked Stockfish client. The test must prove that both named provider models/clients and exactly one `AiChatGateway` coexist without a generic Spring AI `ChatClient.Builder` bean.
4. Issue #38 requires native-image-relevant verification to pass. PR #48 currently records that the native Docker build could not run. The production Docker native-image build must succeed before the issue is considered complete.

## Architecture

Keep the existing `ai` module and failover gateway unchanged. Add one fixed Spring configuration property, `spring.ai.chat.client.enabled=false`, because this application owns construction of both provider `ChatClient` instances. Export `ai.api` with `@NamedInterface("api")`. Add a full application-context smoke test for AI-enabled mode while keeping all provider calls offline.

## Files

- Modify `server/src/main/resources/application.yaml` to disable Spring AI's generic `ChatClient.Builder` auto-configuration.
- Create `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/api/package-info.java` to export the application-facing AI API.
- Create `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/AiEnabledApplicationContextTest.java` to exercise the real application auto-configuration graph in AI-enabled mode without network calls.
- Modify `docs/AI Chess Rivals - Tech Stack.md` because repository rules require configuration changes to remain reflected in the technology/configuration inventory.

## Test Design

`AiEnabledApplicationContextTest` will use `@SpringBootTest` with:

- `app.ai.enabled=true`
- dummy Groq/Gemini API keys and model names
- existing database/Flyway/Modulith persistence auto-configuration exclusions used by `AiChessRivalsApplicationTests`
- `@MockitoBean StockfishClient` so no Stockfish process starts

The test will assert:

- bean `groqChatModel` exists
- bean `geminiChatModel` exists
- bean `groqChatClient` exists
- bean `geminiChatClient` exists
- exactly one `AiChatGateway` exists
- no generic `ChatClient.Builder` bean exists

No provider completion method is invoked, so the test must not contact Groq or Gemini.

The existing `ApplicationModulesTest` remains the structural gate after `ai.api` is exported.

## Native Verification

The canonical production-native check is the repository Dockerfile:

```bash
docker build -t ai-chess-rivals:issue-38 ./server
```

This must exit `0`. Do not mark the native acceptance criterion complete if Docker/GraalVM native compilation cannot actually run.

## Explicit Non-Goals

- Do not change Groq/Gemini timeout conversion. The current Google GenAI SDK version used by Spring AI 2.0.0 applies `HttpOptions.timeout` as milliseconds internally, so the existing `Duration.toMillis()` implementation is correct.
- Do not change failover order, retry behavior, response validation, deterministic fallback semantics, provider properties, API request/result types, or AI enablement defaults.
- Do not add another abstraction, provider registry, retry library, resilience framework, controller, persistence, prompt code, personality code, tool calling, or agent behavior.
- Do not add AI configuration environment variables for `spring.ai.chat.client.enabled`; it is an architectural constant for this application, not an operator choice.

## Completion Criteria

- Full AI-enabled Spring Boot context starts with both explicit providers and no ambiguous generic `ChatClient.Builder`.
- `ai.api` is a Spring Modulith named interface.
- Focused AI tests and `ApplicationModulesTest` pass.
- Backend `verify` passes.
- Root verification passes.
- Production Docker native-image build passes.
- PR #48 validation text is updated only after fresh successful verification.
