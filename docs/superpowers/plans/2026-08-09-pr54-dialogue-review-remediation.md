# PR #54 Dialogue Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Address the correctness and test-coverage findings from the review of PR #54 by making move-event ownership explicit in dialogue prompts and adding direct capture-policy coverage, without expanding issue #42 into deployment or lifecycle work.

**Architecture:** Keep the existing `DialogueSpeakingPolicy`, `DialogueGenerationService`, provider failover, public dialogue API, and chess evaluation contracts intact. Fix the ambiguity at the prompt boundary by rendering the mover identity, the selected speaker's role, and an explicit mover-perspective interpretation rule into every move prompt. Add focused regression tests that prove opponent-speaking events cannot be interpreted as though the opponent made the move.

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring AI 2.0.0 (`PromptTemplate`), Spring Modulith 2.1.0, JUnit 5, AssertJ, Mockito, existing `AiChatGateway` and `EvaluationSwing` contracts.

## Source of Truth

- Pull request: `#54 feat: implement contextual dialogue generation workflow`
- Review finding on PR #54: move prompts lose the mover/speaker relationship for opponent-speaking events.
- Original issue: `#42 Phase 2: Implement contextual dialogue generation workflow`
- Original implementation plan: `docs/superpowers/plans/2026-08-09-contextual-dialogue-generation-workflow.md`
- Evaluation semantics: `chess/StockfishEvaluationService.java` and `chess/api/EvaluationSwing.java`
- Separate native-deployment follow-up: `#55 Phase 2: Make AI enablement safe for GraalVM native deployment`

## Global Constraints

- Do not modify `game/**`, `chess/**`, persistence, Flyway migrations, WebSocket integration, frontend code, provider routing, provider retry configuration, or Maven dependencies.
- Do not solve issue #55 in this PR. Native/AOT deployment work is explicitly tracked separately.
- Do not change the locked speaker-selection semantics from #42:
  - checkmate/promotion -> mover
  - `MAJOR_MISTAKE` -> opponent
  - `MAJOR_GAIN` -> mover
  - check -> opponent
  - capture/ordinary -> mover
- Do not change speaking probabilities: capture/check remain `0.85`; ordinary moves continue using the selected speaker's persisted `speaking_probability`.
- Do not pass FEN, full move history, or additional chess analysis into the prompt.
- Preserve the existing `AiChatGateway` Groq -> Gemini -> deterministic fallback path.
- Keep the fix local to prompt semantics and test coverage. Do not add a new role abstraction, service, enum, database field, or orchestration layer.
- The evaluation line remains the existing compact `before` / `after` / `swing` / `classification` data. The prompt must explicitly state that it describes the committed move from the mover's perspective.
- `check=true` must be described unambiguously as the mover giving check to the opponent, so an opponent-selected speaker cannot read it as having delivered the check.

## File Map

**Modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactory.java`
  - Render mover identity, selected-speaker role, and mover-perspective semantics for move prompts.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactoryTest.java`
  - Lock prompt ownership semantics for both mover-speaking and opponent-speaking cases.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java`
  - Prove the real orchestration path produces the correct speaker plus the correct prompt perspective for major mistakes, checks, and mover-speaking events.
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueSpeakingPolicyTest.java`
  - Add direct capture probability/speaker coverage.

**Do not modify:**

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueSpeakingPolicy.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationService.java`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/internal/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/chess/**`
- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/game/**`
- `server/src/main/resources/db/migration/**`
- `client/**`
- `.github/workflows/**`
- `server/Dockerfile`
- `server/pom.xml`

---

### Task 1: Lock the Missing Move-Ownership Semantics with Prompt-Factory Tests

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactoryTest.java`

**Interfaces:**
- Consumes: existing `DialoguePromptFactory.movePrompt(DialogueMoveRequest, PersonalityPromptProfile, PersonalityPromptProfile, DialogueReactionType, String)`.
- Produces: failing regression tests that define the exact mover/speaker perspective required from all move prompts.

- [ ] **Step 1: Extend the existing mover-speaking move prompt test**

In `rendersMovePromptWithMinimalContextAndAllHistory`, keep the current request where Blaze is the mover and Blaze is passed as `speaker`.

Add assertions for these exact semantic fragments:

```java
assertThat(prompt)
    .contains("Move owner: Blaze")
    .contains("Speaker role: MOVER (you played this move)")
    .contains("from Blaze's perspective")
    .contains("check=true means the mover gave check to the opponent");
```

Do not remove the existing assertions for move notation, evaluation, history, safety rules, or structured format.

- [ ] **Step 2: Add an opponent-speaking prompt regression test**

Add this focused test using a move made by Blaze while Vesper is the selected speaker:

```java
@Test
void rendersOpponentSpeakingMovePromptWithExplicitMoverPerspective() {
  DialogueMoveRequest request =
      new DialogueMoveRequest(
          5,
          "blaze",
          "vesper",
          "Qxd5+",
          true,
          true,
          false,
          false,
          Optional.of(
              new EvaluationSwing(
                  300, -100, -400, EvaluationSwingClassification.MAJOR_MISTAKE)),
          List.of());

  String prompt =
      factory.movePrompt(
          request,
          opponent,
          speaker,
          DialogueReactionType.MOVE_REACTION,
          "FORMAT");

  assertThat(prompt)
      .contains("Move owner: Blaze")
      .contains("Speaker role: OPPONENT (the other personality played this move)")
      .contains("Move: Qxd5+")
      .contains("classification=MAJOR_MISTAKE")
      .contains("from Blaze's perspective")
      .contains("check=true means the mover gave check to the opponent");
}
```

In this test class `speaker` is Blaze and `opponent` is Vesper, so passing `opponent` as the prompt speaker and `speaker` as the other personality intentionally models Vesper reacting to Blaze's move.

- [ ] **Step 3: Run the focused prompt test and verify the new assertions fail**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePromptFactoryTest test
```

Expected: FAIL because the current prompt does not contain `Move owner`, `Speaker role`, or the mover-perspective interpretation sentence.

- [ ] **Step 4: Do not implement production changes yet**

Stop Task 1 after confirming the regression is red. Task 2 owns the production change.

---

### Task 2: Render Mover Identity and Speaker Role in Every Move Prompt

**Files:**
- Modify: `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactory.java`
- Test: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactoryTest.java`

**Interfaces:**
- Consumes: the request's `moverPersonalityKey`, the already-selected `speaker`, and the other personality profile supplied by `DialogueGenerationService`.
- Produces: an unambiguous prompt where model output can distinguish who made the move from who is speaking.
- Preserves: the existing `movePrompt(...)` method signature so `DialogueGenerationService` does not change.

- [ ] **Step 1: Add explicit ownership fields to `MOVE_TEMPLATE`**

In `DialoguePromptFactory`, replace the opening portion of `MOVE_TEMPLATE` with this semantic structure while keeping the existing personality, event, evaluation, history, and `RULES` content:

```java
private static final String MOVE_TEMPLATE =
    """
    You are {speakerDisplayName}, reacting to a committed chess event against {opponentDisplayName}.
    Personality traits: {speakerTraits}
    Style guidance: {speakerStyle}
    Boundary guidance: {speakerBoundary}
    Move owner: {moverDisplayName}
    Speaker role: {speakerRole}
    Perspective: all move flags and evaluation values below describe {moverDisplayName}'s committed move from {moverDisplayName}'s perspective; check=true means the mover gave check to the opponent.
    Ply: {ply}
    Move: {moveNotation}
    Facts: capture={capture}, check={check}, checkmate={checkmate}, promotion={promotion}
    {evaluation}
    Recent dialogue: {recentDialogue}
    React to the event in character.
    """
        + RULES;
```

Do not add a second template for opponent reactions. One move template plus explicit variables is sufficient.

- [ ] **Step 2: Populate `moverDisplayName` and `speakerRole` inside `movePrompt`**

Immediately after `baseVariables(...)`, compute whether the selected speaker is the mover:

```java
boolean speakerIsMover = speaker.key().equals(request.moverPersonalityKey());
String moverDisplayName =
    speakerIsMover ? speaker.displayName() : opponent.displayName();

variables.put("moverDisplayName", moverDisplayName);
variables.put(
    "speakerRole",
    speakerIsMover
        ? "MOVER (you played this move)"
        : "OPPONENT (the other personality played this move)");
```

Then keep the existing `ply`, `moveNotation`, move flags, evaluation, and recent-dialogue variables unchanged.

Do not introduce a `DialogueSpeakerRole` enum. This distinction exists only to make the prompt unambiguous and does not need to become domain state.

- [ ] **Step 3: Run the prompt-factory tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePromptFactoryTest test
```

Expected: PASS, including both mover-speaking and opponent-speaking perspective assertions.

- [ ] **Step 4: Commit the prompt regression fix**

```bash
git add server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactory.java \
        server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactoryTest.java
git commit -m "fix: preserve move ownership in dialogue prompts"
```

---

### Task 3: Prove the Orchestration Path Preserves Speaker and Move Ownership Together

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java`

**Interfaces:**
- Consumes: existing `DialogueGenerationService`, `DialogueSpeakingPolicy`, and the test's captured `AiChatRequest` list.
- Produces: integration-style unit coverage showing that policy-selected speaker identity and prompt perspective agree before the request reaches `AiChatGateway`.

- [ ] **Step 1: Strengthen the existing major-mistake test**

In `majorMistakeMakesOpponentSpeak`, keep the existing assertion that the generated dialogue speaker is `vesper` and add:

```java
assertThat(requests).hasSize(1);
assertThat(requests.getFirst().prompt())
    .contains("Move owner: Blaze")
    .contains("Speaker role: OPPONENT (the other personality played this move)")
    .contains("classification=MAJOR_MISTAKE")
    .contains("from Blaze's perspective");
```

This locks the exact bug found in review: Vesper speaks, while the prompt still states that Blaze made the mistake.

- [ ] **Step 2: Add a check-specific opponent reaction test**

Add:

```java
@Test
void checkMakesOpponentSpeakWithoutClaimingTheOpponentDeliveredCheck() {
  gatewayReturnsValidProviderOutput();
  DialogueGenerationService service = service(new DialogueSpeakingPolicy(() -> 0.0));
  DialogueMoveRequest request =
      new DialogueMoveRequest(
          5,
          "blaze",
          "vesper",
          "Qh5+",
          false,
          true,
          false,
          false,
          Optional.empty(),
          List.of());

  GeneratedDialogue result = service.generateMove(request).orElseThrow();

  assertThat(result.personalityKey()).isEqualTo("vesper");
  assertThat(requests).hasSize(1);
  assertThat(requests.getFirst().prompt())
      .contains("Move owner: Blaze")
      .contains("Speaker role: OPPONENT (the other personality played this move)")
      .contains("check=true means the mover gave check to the opponent");
}
```

The injected random value `0.0` guarantees the `0.85` check branch speaks without changing production policy.

- [ ] **Step 3: Strengthen one mover-speaking service test**

In `mapsProviderOutputAndSource`, after the existing output/source assertions add:

```java
assertThat(requests).hasSize(1);
assertThat(requests.getFirst().prompt())
    .contains("Move owner: Blaze")
    .contains("Speaker role: MOVER (you played this move)");
```

This prevents a future fix from accidentally labeling every speaker as the opponent.

- [ ] **Step 4: Run the service tests**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialogueGenerationServiceTest test
```

Expected: PASS.

- [ ] **Step 5: Commit the orchestration regression coverage**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java
git commit -m "test: lock dialogue move perspective"
```

---

### Task 4: Add Direct Capture Probability and Speaker Coverage

**Files:**
- Modify: `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueSpeakingPolicyTest.java`

**Interfaces:**
- Consumes: the existing unchanged policy rule `request.capture() || request.check() ? 0.85 : speakingProbability`.
- Produces: direct regression coverage proving capture uses the fixed `0.85` branch and selects the mover.

- [ ] **Step 1: Add a focused capture test without refactoring existing helpers**

Add:

```java
@Test
void captureUsesImportantEventProbabilityAndMoverSpeaker() {
  DialogueMoveRequest capture =
      new DialogueMoveRequest(
          6,
          "mover",
          "opponent",
          "exd5",
          true,
          false,
          false,
          false,
          Optional.empty(),
          List.of(new DialogueHistoryLine(5, "opponent", "Opponent", "previous line")));

  DialogueSpeakingPolicy speak = new DialogueSpeakingPolicy(() -> 0.849);
  DialogueSpeakingPolicy silent = new DialogueSpeakingPolicy(() -> 0.850);

  assertThat(speak.selectMoveSpeaker(capture, mover, opponent)).contains("mover");
  assertThat(silent.selectMoveSpeaker(capture, mover, opponent)).isEmpty();
}
```

The history line at ply `5` deliberately prevents the four-ply silence rule from forcing speech and masking the probability branch.

Do not rewrite the existing request helper just to support this single test; a direct constructor is clearer and keeps this remediation small.

- [ ] **Step 2: Run the policy test**

Run:

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialogueSpeakingPolicyTest test
```

Expected: PASS without production changes.

- [ ] **Step 3: Commit the test gap fix**

```bash
git add server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueSpeakingPolicyTest.java
git commit -m "test: cover capture dialogue probability"
```

---

### Task 5: Verify the PR Remediation and Keep Native Deployment Out of Scope

**Files:**
- Verify only; no additional production files should be needed.

**Interfaces:**
- Produces: evidence that the review findings are fixed without expanding PR #54 into issue #55.

- [ ] **Step 1: Run all dialogue-focused tests together**

```powershell
server\mvnw.cmd -f server\pom.xml -Dtest=DialoguePromptFactoryTest,DialogueGenerationServiceTest,DialogueSpeakingPolicyTest,DialogueOutputCodecTest,DialogueBoundaryAdvisorTest,FailoverAiChatGatewayTest test
```

Expected: PASS.

- [ ] **Step 2: Run Spotless**

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
```

If Spotless changes any of the four files in this plan, inspect the diff and include those formatting changes. It must not cause unrelated source changes.

- [ ] **Step 3: Run backend verification**

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

Expected: Maven exits `0`, all backend tests pass, and static-analysis gates report no new findings.

- [ ] **Step 4: Run the repository verification script**

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify.ps1
```

Expected: all applicable repository verification gates pass.

- [ ] **Step 5: Review the final diff against the scope boundary**

Run:

```bash
git diff --name-only origin/master...HEAD
```

For the remediation commits, expected new changes beyond the original PR implementation are limited to:

```text
docs/superpowers/plans/2026-08-09-pr54-dialogue-review-remediation.md
server/src/main/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactory.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialoguePromptFactoryTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueGenerationServiceTest.java
server/src/test/java/dev/krishnamurti/ai_chess_rivals/ai/dialogue/DialogueSpeakingPolicyTest.java
```

The overall PR will still contain the original #42 files. Confirm there are no **new remediation edits** to `AiProviderConfiguration`, Docker/native-image configuration, workflows, game/chess code, persistence, or frontend code.

- [ ] **Step 6: Inspect the final semantic behavior manually**

Verify from tests/diff that all three relationships are now explicit:

```text
Blaze moves normally -> Blaze speaks as MOVER -> prompt says Blaze made the move.
Blaze gives check -> Vesper may speak as OPPONENT -> prompt says Blaze made the move and the mover gave check.
Blaze makes MAJOR_MISTAKE -> Vesper speaks as OPPONENT -> prompt says Blaze made the move and evaluation is from Blaze/mover perspective.
```

- [ ] **Step 7: Do not address native deployment in this branch**

Confirm issue `#55` remains the tracking location for the GraalVM AOT / `AI_ENABLED` problem. Do not modify `server/Dockerfile`, `.github/workflows/ci.yml`, `application.yaml`, `AiGatewayConfiguration`, or AI provider topology as part of this remediation.

- [ ] **Step 8: Push the remediation commits and update PR #54**

Push the existing feature branch:

```bash
git push origin feature/issue-42-contextual-dialogue-generation
```

Then add a PR comment summarizing:

```text
Addressed review findings:
- move prompts now identify the mover and selected speaker role explicitly;
- evaluation/check semantics are explicitly mover-perspective;
- regression coverage added for major-mistake opponent reactions, check opponent reactions, mover reactions, and capture probability.

Native/AOT deployment concern remains intentionally separate in #55.
```

Do not mark #55 resolved from this PR.
