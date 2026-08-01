# Phase 2 AI Personality Layer Design

Date: 2026-08-01
Status: Approved
Epic: #4

## Goal

Transform the completed Stockfish-vs-Stockfish viewer into an entertaining AI-vs-AI experience with selectable personalities, contextual banter, resilient multi-provider LLM integration, and replayable dialogue.

The chess layer remains authoritative. LLMs never select or validate moves.

## Scope

Phase 2 delivers:

- Spring AI integration
- Groq as primary provider and Gemini as fallback
- Four read-only system personalities stored in PostgreSQL
- Personality selection and random rivalry selection
- Game-start introductions
- Tiered move reactions
- Victory and defeat dialogue
- Lightweight Stockfish evaluation swings
- Structured and validated dialogue output
- Persisted dialogue restored on refresh/reconnect
- Unified chronological move and dialogue activity feed
- Metrics and structured logs for AI calls and fallback behavior

Periodic narration, user-created personalities, long-term memory, tool calling, and multi-step agents are deferred.

## Provider Strategy

Use Spring AI as the integration layer.

- Groq is the default primary provider through the OpenAI-compatible integration.
- Gemini is the only automatic fallback provider.
- Provider and model names are environment-configurable.
- Groq timeout: 8 seconds.
- Gemini timeout: 12 seconds.
- Groq failure, timeout, or malformed structured output immediately triggers Gemini.
- No same-provider retry.
- If both providers fail, persist a deterministic character-specific fallback line and continue.

The chess match must never fail because entertainment generation failed.

## Spring AI Showcase Boundary

Phase 2 deliberately demonstrates:

- `ChatClient`
- provider-specific `ChatModel` configuration
- prompt templates
- structured output mapping
- a lightweight Advisor for shared context or instrumentation
- Actuator/Micrometer observability

Tool calling, chat memory, autonomous agents, and multi-step workflows belong to Phase 3.

## Turn Flow

Dialogue generation is synchronous with match pacing:

1. Stockfish selects a move.
2. The backend commits and classifies the move.
3. The position receives a lightweight post-move evaluation.
4. The dialogue policy decides whether anyone speaks and selects one speaker.
5. Groq generates and validates the response.
6. Gemini is called once if Groq fails or returns invalid output.
7. A deterministic fallback line is used if both fail.
8. Dialogue is persisted and broadcast.
9. The existing random move delay runs.
10. The next turn begins.

Slow dialogue is acceptable, but provider waits are bounded.

## Dialogue Frequency

Use tiered frequency:

- Always speak for game start, game end, checkmate, promotion, and major evaluation swings.
- Captures and checks have high speaking probability.
- Ordinary moves use personality-specific probability and recent-silence rules.
- One character normally speaks per move event.
- Game start and game end may produce one line from each character.

The speaker is selected according to the event: the mover may celebrate a strong move, while the opponent may react to a threat, material loss, or mistake.

## Chess Awareness

Stockfish remains the only chess evaluator.

After every committed move, request a shallow, predictable-cost position evaluation. Persist or expose:

- evaluation before
- evaluation after
- evaluation swing
- broad classification: stable, major gain, or major mistake

Do not claim precise consumer-chess labels such as “brilliant move.”

## Personality Model

Personalities are PostgreSQL records seeded through Flyway.

Phase 2 supports exactly four system personalities:

- selectable as White or Black
- read-only
- distinct in identity, speaking style, humor, confidence, speaking frequency, and rivalry hooks

A dedicated issue defines and validates the actual characters. Phase 2 does not include authoring APIs or UI. Future user-created personalities may extend the same persistence model, while system personalities remain immutable.

## Prompt Context

Each generation receives minimal context:

- triggering event and move
- concise board and evaluation facts
- speaker personality
- opponent identity
- last four persisted dialogue lines

The prompt should permit direct replies without forcing every line to be a comeback. No full move history, summarization memory, or long-term memory is included.

## Structured Output

Use a small required schema:

- `text`
- `emotion`
- `reactionType`

Keep enums small. Validate blank text, length, enum mapping, and PG-13 safety before persistence. Invalid Groq output triggers Gemini directly.

## Safety

Allow PG-13 sarcasm, mockery, arrogance, and dramatic rivalry language.

Disallow slurs, sexual content, threats, and personally targeted abuse. Use prompt boundaries plus lightweight backend validation. Do not add a moderation-model call in Phase 2.

## Persistence and Recovery

Persist each dialogue line with at least:

- match identifier
- triggering ply/event
- personality identifier
- text
- emotion
- reaction type
- provider/fallback metadata
- timestamp

Refresh, reconnect, and completed-match hydration must preserve the chronological banter history.

## Frontend Experience

Before starting a match, the viewer can:

- select distinct White and Black personalities
- randomize a rivalry between two distinct personalities

Moves and dialogue share the existing chronological activity feed. Dialogue has clear character identity and distinct styling. Separate banter panels and board speech bubbles are deferred.

## Observability

Record metrics and structured logs for:

- selected provider
- latency
- success/failure
- timeout
- validation failure
- fallback activation
- deterministic fallback usage
- approximate token usage when available

Do not log prompts or raw responses in normal logs. Persist only the accepted final dialogue.

## Testing

Use stubbed/fake chat models for deterministic tests. Cover:

- Groq success
- Groq timeout/failure to Gemini success
- malformed Groq output to Gemini success
- both providers failing to deterministic fallback
- speaking-frequency policy
- speaker selection
- evaluation swing classification
- dialogue persistence and hydration
- personality selection and randomization
- no regression to match completion when AI fails

## Delivery Order

1. Documentation and architecture alignment
2. Spring AI provider foundation
3. Lightweight Stockfish evaluation
4. Personality persistence and roster API
5. Four system personality design and seed data
6. Dialogue generation workflow
7. Dialogue persistence and match lifecycle integration
8. Personality selection and random rivalry UI
9. Unified activity feed rendering
10. Observability, resilience tests, and Phase 2 acceptance

## Success Criteria

Phase 2 is complete when:

- viewers can choose or randomize two distinct personalities
- characters visibly differ in style and frequency
- start, important move, and end dialogue appears in order
- recent banter enables contextual replies
- Groq-to-Gemini failover is bounded and observable
- provider failure never prevents match completion
- dialogue survives refresh and reconnect
- the project clearly demonstrates practical Spring AI integration without introducing Phase 3 agent complexity
