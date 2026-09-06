# Request Tracing Review Fixes Implementation Plan

> **For Luna:** REQUIRED SUB-SKILL: execute this plan **inline** with `superpowers:executing-plans`, task by task, using the checkboxes below. Keep execution in the current session; do not delegate tasks to worker/subagents.

**Goal:** Resolve the two blocking review findings on PR #72 by making the MDC `requestId` visible in rendered application logs and by ensuring requests that escape with an exception cannot be logged as successful `200` completions.

**Architecture:** Keep the existing single `RequestTracingFilter`; do not add tracing libraries, extra filters, or exception-handling infrastructure. Use Spring Boot's existing console logging pattern support to render the already-populated MDC value, and track whether the servlet chain completed normally so the completion log can use an effective `500` only when an exception escapes while the response still reports a non-error status.

**Tech Stack:** Java 25, Spring Boot 4.1, Spring MVC `OncePerRequestFilter`, SLF4J MDC, Logback, JUnit 5, AssertJ, MockMvc, Spring Boot `OutputCaptureExtension`, Maven.

**Spec:** GitHub issue #71 (`Add INFO-level request tracing and correlation IDs`) plus the two blocking review findings on PR #72.

## Global Constraints

- Work on the existing PR branch: `feat/issue-71-request-tracing`.
- Keep one request-tracing filter; do not add OpenTelemetry, Micrometer Tracing, a second filter, or new dependencies.
- Preserve current tracing scope: only `/api/**` and `/ws/**` handshake requests.
- Preserve request-ID validation: `[A-Za-z0-9._-]{1,64}`; missing/unsafe values become UUIDs.
- Keep exactly one INFO completion log per traced request.
- Do not log request/response bodies, query-string values, authorization credentials, cookies, owner token, API keys, arbitrary request headers, IP address, or user agent.
- Keep MDC restoration/cleanup in `finally`.
- Do not change controller, WebSocket-handler, AI, or match logging semantics beyond making the existing MDC request ID visible in the rendered log pattern.
- Do not touch unrelated frontend formatting failures; they are outside PR #72.
- Do not merge the PR as part of this plan. Push the fixes, verify CI, then request re-review.

---

## File Map

- Modify `server/src/main/resources/application.yaml`
  - Render the existing `requestId` MDC value in normal console log lines.
- Modify `server/src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java`
  - Avoid duplicating `requestId` inside the completion-message payload once it is supplied by the log pattern.
  - Log an effective failure status when the servlet chain exits exceptionally before setting an error status.
- Modify `server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java`
  - Prove MDC propagation to downstream log events.
  - Prove completion events carry the request ID via MDC rather than message duplication.
  - Prove exceptional execution is rethrown, MDC is restored, and completion logging reports failure rather than `200`.
- Modify `server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterIntegrationTest.java`
  - Prove the configured console pattern actually renders `[requestId=<id>]` for a real traced request.

---

### Task 1: Render MDC request IDs on downstream application logs

**Files:**
- Modify: `server/src/main/resources/application.yaml`
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterIntegrationTest.java`

**Interfaces:**
- Consumes: existing MDC key `RequestTracingFilter.REQUEST_ID_MDC_KEY == "requestId"`.
- Produces: rendered console marker `[requestId=<value>]` while a traced request is executing.
- Produces: completion message body `HTTP request completed method=... path=... status=... durationMs=...`; request correlation comes from MDC/log pattern rather than a duplicate message field.

- [ ] **Step 1: Add the failing rendered-log assertion to the Spring integration test**

Update `RequestTracingFilterIntegrationTest` to use Spring Boot's output-capture support:

```java
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    properties = {
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
@AutoConfigureMockMvc
@Import({PersonalityRepositoryTestConfiguration.class, DialogueRepositoryTestConfiguration.class})
class RequestTracingFilterIntegrationTest {
```

Change the existing test signature and add a rendered-output assertion:

```java
@Test
void registersRequestTracingFilterInApplicationContext(CapturedOutput output) throws Exception {
  mockMvc
      .perform(get("/api/v1/personalities").header("X-Request-ID", "integration-trace-001"))
      .andExpect(status().isOk())
      .andExpect(header().string("X-Request-ID", "integration-trace-001"));

  assertThat(output.getOut())
      .contains("[requestId=integration-trace-001]")
      .contains("HTTP request completed");
}
```

This assertion must target the bracketed pattern marker exactly. Do not merely search for `requestId=integration-trace-001`, because the current completion message already contains that text and would create a false-positive test.

- [ ] **Step 2: Add a failing unit test proving downstream log events receive the request MDC**

Add this test to `RequestTracingFilterTest`:

```java
@Test
void propagatesRequestIdToDownstreamLogEvents() throws Exception {
  Logger downstreamLogger =
      (Logger) LoggerFactory.getLogger("request-tracing-downstream-test");
  ListAppender<ILoggingEvent> downstreamAppender = new ListAppender<>();
  downstreamAppender.start();
  downstreamLogger.addAppender(downstreamAppender);

  try {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/personalities");
    request.addHeader("X-Request-ID", "downstream-trace-001");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          downstreamLogger.info("downstream work");
          response.setStatus(200);
        });

    assertThat(downstreamAppender.list).hasSize(1);
    assertThat(downstreamAppender.list.get(0).getMDCPropertyMap())
        .containsEntry("requestId", "downstream-trace-001");
  } finally {
    downstreamLogger.detachAppender(downstreamAppender);
    downstreamAppender.stop();
  }
}
```

This documents the intended propagation contract even though the current filter already puts the ID in MDC.

- [ ] **Step 3: Change the completion-log unit expectation so request ID is carried by MDC, not duplicated in the message**

In `generatesRequestIdAndLogsApiCompletion`, keep the existing response-header/UUID assertions, then replace the formatted-message assertion with:

```java
ILoggingEvent completionEvent = appender.list.get(0);
assertThat(completionEvent.getMDCPropertyMap()).containsEntry("requestId", requestId);
assertThat(completionEvent.getFormattedMessage())
    .contains(
        "method=GET",
        "path=/api/v1/personalities",
        "status=200",
        "durationMs=")
    .doesNotContain("requestId=");
```

In `reusesSafeIncomingRequestId`, replace the current message assertion with:

```java
ILoggingEvent completionEvent = appender.list.get(0);
assertThat(completionEvent.getMDCPropertyMap())
    .containsEntry("requestId", "manual-trace-001");
assertThat(completionEvent.getFormattedMessage())
    .contains("method=GET", "path=/api/v1/personalities")
    .doesNotContain("requestId=");
```

The `doesNotContain("requestId=")` checks must fail against the current implementation and prevent future duplicate correlation fields.

- [ ] **Step 4: Run the focused tests and observe RED before changing production code/config**

From `server/`:

```bash
./mvnw -Dtest=RequestTracingFilterTest,RequestTracingFilterIntegrationTest test
```

Expected before implementation:

- integration assertion for `[requestId=integration-trace-001]` fails because the console pattern does not currently render MDC `requestId`;
- completion-message assertions fail because the current message explicitly contains `requestId=...`.

The downstream-event MDC test may already pass; that is expected and confirms the filter already propagates MDC correctly.

- [ ] **Step 5: Configure Spring Boot to render the MDC request ID**

In `server/src/main/resources/application.yaml`, add a top-level logging section near the existing `server` / `spring` configuration:

```yaml
logging:
  pattern:
    level: "%5p [requestId=%X{requestId}]"
```

Do not replace the full console pattern and do not add a custom `logback-spring.xml`. Spring Boot 4.1 explicitly supports `logging.pattern.level` for adding MDC values to the default log format, so this is the smallest configuration change.

- [ ] **Step 6: Remove duplicate request-ID text from the filter's completion message**

In `RequestTracingFilter#doFilterInternal`, change only the completion message template and arguments from:

```java
log.info(
    "HTTP request completed requestId={} method={} path={} status={} durationMs={}",
    requestId,
    request.getMethod(),
    request.getRequestURI(),
    response.getStatus(),
    durationMs);
```

to:

```java
log.info(
    "HTTP request completed method={} path={} status={} durationMs={}",
    request.getMethod(),
    request.getRequestURI(),
    response.getStatus(),
    durationMs);
```

Do not move the log statement after `restoreMdc(...)`; the MDC value must still be active while the completion event is created.

- [ ] **Step 7: Run the focused tests and verify GREEN**

From `server/`:

```bash
./mvnw -Dtest=RequestTracingFilterTest,RequestTracingFilterIntegrationTest test
```

Expected: PASS.

- [ ] **Step 8: Commit Task 1**

```bash
git add src/main/resources/application.yaml \
        src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java \
        src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java \
        src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterIntegrationTest.java
git commit -m "fix: render request IDs in application logs"
```

---

### Task 2: Make exceptional request completion logs truthful

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java`
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java`

**Interfaces:**
- Consumes: current `HttpServletResponse#getStatus()`.
- Produces: the same response behavior and same thrown exception as before.
- Produces: logged status `500` when the filter chain exits exceptionally while the response still has a non-error status (`< 400`).
- Preserves: an already-set `4xx`/`5xx` response status when an exception escapes after that status was set.

- [ ] **Step 1: Add a failing regression test for an escaping exception**

Add this test to `RequestTracingFilterTest`:

```java
@Test
void logsFailureStatusAndRestoresMdcWhenRequestEscapesException() {
  MDC.put("requestId", "outer-request");

  MockHttpServletRequest request =
      new MockHttpServletRequest("GET", "/api/v1/personalities");
  request.addHeader("X-Request-ID", "exception-trace-001");
  MockHttpServletResponse response = new MockHttpServletResponse();
  ServletException failure = new ServletException("boom");

  assertThatThrownBy(
          () ->
              filter.doFilter(
                  request,
                  response,
                  (req, res) -> {
                    assertThat(MDC.get("requestId")).isEqualTo("exception-trace-001");
                    throw failure;
                  }))
      .isSameAs(failure);

  assertThat(response.getHeader("X-Request-ID")).isEqualTo("exception-trace-001");
  assertThat(appender.list).hasSize(1);

  ILoggingEvent completionEvent = appender.list.get(0);
  assertThat(completionEvent.getMDCPropertyMap())
      .containsEntry("requestId", "exception-trace-001");
  assertThat(completionEvent.getFormattedMessage())
      .contains(
          "method=GET",
          "path=/api/v1/personalities",
          "status=500",
          "durationMs=");

  assertThat(MDC.get("requestId")).isEqualTo("outer-request");
}
```

Add the missing static import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

Add the servlet exception import if not already present:

```java
import jakarta.servlet.ServletException;
```

- [ ] **Step 2: Run only the new regression and observe RED**

From `server/`:

```bash
./mvnw -Dtest=RequestTracingFilterTest#logsFailureStatusAndRestoresMdcWhenRequestEscapesException test
```

Expected with the current implementation: FAIL because the completion log reports the mock response's default `status=200` even though the chain threw.

- [ ] **Step 3: Track normal completion without catching or translating the exception**

In `RequestTracingFilter#doFilterInternal`, introduce a flag immediately before entering the chain:

```java
boolean completedNormally = false;
```

Set it only after `filterChain.doFilter(...)` returns normally:

```java
try {
  filterChain.doFilter(request, response);
  completedNormally = true;
} finally {
```

Do not add a `catch` block. Let `ServletException`, `IOException`, runtime exceptions, and errors propagate exactly as they do today.

- [ ] **Step 4: Compute an effective completion status inside `finally`**

At the start of `finally`, before logging, add:

```java
int completionStatus = response.getStatus();
if (!completedNormally && completionStatus < 400) {
  completionStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
}
```

Then use `completionStatus` in the completion log:

```java
log.info(
    "HTTP request completed method={} path={} status={} durationMs={}",
    request.getMethod(),
    request.getRequestURI(),
    completionStatus,
    durationMs);
```

This deliberately changes only what is reported in the completion log. Do not call `response.setStatus(500)` here; the servlet container / existing exception handling remains responsible for the real HTTP response.

- [ ] **Step 5: Add a regression proving an already-set error status is preserved**

Add this test:

```java
@Test
void preservesExistingErrorStatusWhenRequestEscapesException() {
  MockHttpServletRequest request =
      new MockHttpServletRequest("GET", "/api/v1/personalities");
  request.addHeader("X-Request-ID", "exception-status-trace-001");
  MockHttpServletResponse response = new MockHttpServletResponse();
  ServletException failure = new ServletException("boom");

  assertThatThrownBy(
          () ->
              filter.doFilter(
                  request,
                  response,
                  (req, res) -> {
                    response.setStatus(503);
                    throw failure;
                  }))
      .isSameAs(failure);

  assertThat(appender.list).hasSize(1);
  assertThat(appender.list.get(0).getFormattedMessage()).contains("status=503");
}
```

This prevents the fallback logic from blindly rewriting every exceptional request to `500`.

- [ ] **Step 6: Run the full request-tracing unit suite**

From `server/`:

```bash
./mvnw -Dtest=RequestTracingFilterTest test
```

Expected: PASS.

- [ ] **Step 7: Run the integration test again**

```bash
./mvnw -Dtest=RequestTracingFilterIntegrationTest test
```

Expected: PASS, including the bracketed console marker `[requestId=integration-trace-001]`.

- [ ] **Step 8: Commit Task 2**

```bash
git add src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java \
        src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java
git commit -m "fix: report exceptional request completions"
```

---

### Task 3: Verify the complete PR and prepare it for re-review

**Files:**
- No planned production-code changes.
- Update the PR description/comment only if verification evidence needs to be recorded.

**Interfaces:**
- Produces: fresh local verification evidence and fresh GitHub CI evidence for the new PR head.

- [ ] **Step 1: Run the focused observability tests from `server/`**

```bash
./mvnw -Dtest=RequestTracingFilterTest,RequestTracingFilterIntegrationTest test
```

Expected: PASS.

- [ ] **Step 2: Run all backend tests**

```bash
./mvnw test
```

Expected: all backend tests PASS.

- [ ] **Step 3: Run backend formatting/static-analysis verification**

```bash
./mvnw -DskipTests verify
```

Expected: PASS, including Spotless/SpotBugs checks configured by the repository.

- [ ] **Step 4: Run repository diff hygiene**

From repository root:

```bash
git diff --check
```

Expected: no whitespace errors.

- [ ] **Step 5: Run the repository verifier once, but do not expand scope into unrelated frontend cleanup**

From repository root:

```bash
./scripts/verify.sh
```

Expected for this branch:

- backend stage must PASS;
- if the root script reaches the already-known unrelated frontend Prettier findings, record them as pre-existing/out-of-scope and do not edit frontend files in this PR.

- [ ] **Step 6: Inspect the final diff before pushing**

```bash
git diff master...HEAD -- \
  server/src/main/resources/application.yaml \
  server/src/main/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilter.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterTest.java \
  server/src/test/java/dev/krishnamurti/ai_chess_rivals/observability/RequestTracingFilterIntegrationTest.java
```

Confirm mechanically:

- console pattern contains exactly one MDC request-ID marker;
- completion message does not duplicate `requestId=`;
- no sensitive request data was added to logging;
- exceptional default/non-error status is reported as `500`;
- an existing `4xx`/`5xx` status remains unchanged;
- exceptions are not caught/swallowed/replaced;
- MDC restoration remains in `finally` after the completion log.

- [ ] **Step 7: Push the existing PR branch**

```bash
git push origin feat/issue-71-request-tracing
```

- [ ] **Step 8: Wait for fresh PR CI and require both backend and native-image verification to be green**

Do not treat the old green run for commit `d41011aba495018c4463d03ee2ffeef5e2eac4bf` as evidence for these fixes. Verification must correspond to the new head SHA containing Tasks 1 and 2.

Required fresh checks:

- Backend verification: PASS.
- Native image verification: PASS, including AI-enabled/native WebSocket topology smoke.
- No new CodeRabbit blocking finding related to the changed lines.

- [ ] **Step 9: Post a concise PR update requesting re-review**

Use this exact structure:

```markdown
Implemented the two blocking review fixes from the follow-up plan:

- rendered MDC `requestId` in normal application console logs so downstream request logs are correlatable;
- made exceptional request completion logging report an effective failure status instead of a misleading default `200`, while preserving existing `4xx`/`5xx` statuses and rethrowing the original exception.

Verification:
- focused request-tracing tests: PASS
- backend test suite: PASS
- backend verify/static analysis: PASS
- native-image CI: PASS

Ready for re-review.
```

Do not claim any item PASS until the corresponding fresh command/check has actually completed successfully.

---

## Self-Review Checklist

- [x] Both PR #72 blocking findings map to explicit tasks.
- [x] No new observability platform, dependency, filter, or tracing subsystem is introduced.
- [x] The plan keeps request correlation in MDC and makes it visible using Spring Boot's existing logging pattern support.
- [x] The plan prevents request-ID duplication in the completion message.
- [x] The plan covers downstream MDC propagation, rendered console output, exceptional cleanup, misleading `200` logging, and preservation of already-set error statuses.
- [x] Exception semantics remain unchanged because implementation uses `try/finally` without a new catch/translation layer.
- [x] Full backend and native-image verification are required before requesting re-review.
- [x] Unrelated frontend formatting findings remain explicitly out of scope.
