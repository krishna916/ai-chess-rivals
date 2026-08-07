# AI Chess Rivals — Implementation Strategy

Version: 1.1
Status: Accepted

## Purpose

Implement the project in independently verifiable phases so chess correctness remains separate
from AI entertainment behavior. Phase 1 provides the stable Stockfish match foundation. Phase 2
adds Spring AI-based personality and dialogue without changing chess authority. Phase 3 may later
introduce bounded agentic workflows.

This strategy follows the [Constitution](AI%20Chess%20Rivals%20-%20Constitution.md) and the approved
[Phase 2 personality-layer design](superpowers/specs/2026-08-01-phase-2-ai-personality-layer-design.md).

## Architectural Boundary

The chess layer owns legal moves, position state, move commitment, evaluation, result detection,
and match progression.

The AI layer observes committed game events and produces entertainment. It never selects,
validates, or replaces a chess move.

## Phase 1 — Chess Foundation

Phase 1 is the completed chess foundation. It is responsible for:

- Stockfish/UCI process communication and lifecycle
- game lifecycle and match control
- authoritative board state and move history
- legal move application and result detection
- WebSocket match streaming
- frontend board/activity-feed hydration
- resilience across interruption, refresh, and reconnect

Phase 1 contains no LLM dependency, provider integration, prompt engineering, personality dialogue,
or agent orchestration.

## Phase 2 — AI Personality Layer

Phase 2 adds entertainment on top of the completed chess foundation:

- Use Spring AI as the required provider integration layer.
- Use Groq as primary through the OpenAI-compatible integration.
- Use Gemini as the only automatic fallback.
- Keep provider and model names environment-configurable.
- Generate dialogue only after a move is committed and classified.
- Persist accepted dialogue and restore it on refresh/reconnect.
- Keep provider waits bounded and continue the match with deterministic fallback dialogue when both providers fail.
- Use structured output with `text`, `emotion`, and `reactionType`.
- Demonstrate `ChatClient`, provider-specific `ChatModel` configuration, prompt templates, structured output mapping, a lightweight Advisor, and Actuator/Micrometer observability.

### Phase 2 Dialogue Flow

The match remains authoritative and deterministic at every chess boundary:

1. Stockfish selects a move.
2. The backend commits and classifies the move.
3. The position receives a lightweight post-move evaluation.
4. A dialogue policy decides whether anyone speaks and selects one speaker.
5. Groq generates the response; the backend validates its structured output.
6. Gemini is called once if Groq fails or the backend rejects the structured output.
7. A deterministic character-specific fallback line is used if both providers fail.
8. Accepted dialogue is persisted and broadcast with the match activity.
9. The existing move pacing continues and the next turn begins.

The dialogue policy gives priority to game start, game end, checkmate, promotion, and major
evaluation swings. Captures and checks have high speaking probability; ordinary moves use
personality-specific probability and recent-silence rules. Normally one character speaks per move
event.

### Phase 2 Persistence and Safety

Persist each accepted dialogue line with its match, triggering ply/event, personality, text,
emotion, reaction type, provider/fallback metadata, and timestamp. Refresh, reconnect, and
completed-match hydration must preserve chronological banter.

Structured output is validated for non-blank text, length, enum mapping, and PG-13 safety before
persistence. Slurs, sexual content, threats, and personally targeted abuse are not allowed.
Provider failure must never prevent match completion.

## Phase 3 Boundary

The following remain deferred to Phase 3 and are not Phase 2 implementation options:

- tool calling
- chat memory
- autonomous agents
- multi-step workflows
- generic orchestration frameworks
- long-term memory

Phase 2 does not introduce a generic agent framework, a memory system, or a workflow engine to
support these deferred capabilities.

## Delivery Order

Implement the approved design in this order:

1. Documentation and architecture alignment
2. Spring AI provider foundation
3. Lightweight Stockfish evaluation
4. Personality persistence and roster API
5. Four system personality designs and seed data
6. Dialogue generation workflow
7. Dialogue persistence and match lifecycle integration
8. Personality selection and random rivalry UI
9. Unified activity-feed rendering
10. Observability, resilience tests, and Phase 2 acceptance

## Phase 2 Success Criteria

Phase 2 is successful when:

- viewers can choose or randomize two distinct personalities
- characters visibly differ in style and speaking frequency
- start, important-move, and end dialogue appears in order
- recent banter enables contextual replies only through a bounded, explicitly supplied
  recent-dialogue window. Spring AI `ChatMemory` and any long-term memory remain deferred to
  Phase 3.
- Groq-to-Gemini failover is bounded and observable
- provider failure never prevents match completion
- dialogue survives refresh and reconnect
- the project demonstrates practical Spring AI integration without introducing Phase 3 agent complexity
