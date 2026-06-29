# AI Chess Rivals — Constitution
Version: 1.0
Status: Accepted

---

# Project Purpose

AI Chess Rivals is a hobby portfolio project built to learn and demonstrate practical AI engineering through an entertaining AI-vs-AI chess experience.

The project showcases:

- AI integration
- LLM usage
- Prompt engineering
- Agent design
- Product thinking
- End-to-end application development

It is **not** intended to become a long-lived production product.

---

# Project Lifecycle

This project is intentionally short-lived.

The expected lifecycle is:

- Build MVP
- Iterate 3–4 times
- Polish the experience
- Showcase the project
- Move on to the next learning opportunity

Long-term maintainability is not a primary goal.

---

# North Star

> Build AI characters, not AI researchers.

The chessboard is the stage.

The personalities are the product.

Every decision should increase the entertainment value of watching two AI personalities compete.

---

# Product Principles

## Entertainment First

Prioritize:

- Personality
- Rivalries
- Trash talk
- Emotional reactions
- Match narratives
- Replay value

over stronger chess.

---

## AI Showcase First

The project should clearly demonstrate practical AI engineering skills.

Favor features that showcase:

- LLM integration
- Prompt engineering
- Personality design
- AI-assisted user experiences

Avoid complexity that exists only to demonstrate software architecture.

---

## Simplicity First

Choose the simplest solution that achieves the desired experience.

Prefer:

- Straightforward code
- Explicit logic
- Small services
- Simple data models

Avoid unnecessary abstractions.

---

## Shipping Over Perfection

A working implementation is more valuable than a perfect implementation.

Small technical debt is acceptable if it speeds up delivery.

Do not optimize for problems unlikely to appear within the project's expected lifetime.

---

# Architecture Principles

## Chess Engine

Stockfish is responsible for:

- Legal moves
- Position evaluation
- Candidate move generation

Stockfish is never responsible for personality.

---

## Personality Layer

Personality selects from Stockfish's candidate moves using structured attributes such as:

- Aggression
- Risk tolerance
- Blunder chance

The goal is distinct play styles rather than stronger chess.

---

## **LLM Responsibilities**

LLMs are used exclusively for entertainment.

Examples include:

- Trash talk
- Reactions
- Celebrations
- Mocking
- Commentary

LLMs never calculate chess moves.

The application should support configurable AI providers and models. The choice of provider (e.g. Gemini, OpenAI, Anthropic, OpenRouter, or compatible APIs) is an implementation detail and may change for experimentation, cost, or quality reasons.

---

# Technology Stack

## Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- PostgreSQL
- Bean Validation
- Lombok
- Actuator
- Chesslib
- Stockfish

## Frontend

- React
- Vite
- TypeScript
- Tailwind CSS
- shadcn/ui
- react-router-dom
- react-chessboard
- chess.js
- Zustand
- Axios
- Lucide React
- Day.js

---

# Development Principles

Development should maximize productivity.

Run:

- Spring Boot directly from the IDE
- React using the Vite development server
- PostgreSQL in Docker Compose
- Stockfish as a native executable

Only containerize what meaningfully improves development.

---

# Deployment Principles

Keep deployment simple.

Deploy Spring Boot directly to Railway or Render.

Bundle the appropriate Stockfish executable with the application.

Introduce Docker only if deployment complexity justifies it.

---

# Explicitly Out of Scope

The MVP intentionally excludes:

- Complex multi-agent frameworks
- Long-term memory
- Reflection systems
- Autonomous planning
- Event-driven architectures
- Microservices
- Distributed systems
- Vector databases
- Redis
- Kafka
- User accounts
- Multiplayer
- Plugin architectures
- Generic orchestration frameworks

Complexity should only be added when it directly improves the viewer experience.

---

# Decision Framework

When evaluating any feature or architectural idea, ask:

1. Does it make the matches more entertaining?
2. Does it better showcase AI engineering?
3. Will it likely be useful within the next 3–4 iterations?
4. Is there a significantly simpler solution?

If a simpler solution achieves a similar outcome, prefer the simpler solution.

---

# Success Criteria

The project is successful when:

- AI personalities feel distinct.
- Trash talk is entertaining.
- Watching a match is enjoyable.
- The project clearly demonstrates practical AI engineering skills.
- The application is polished enough to showcase in a portfolio.

The project is **not** successful because it has:

- The strongest chess engine
- Enterprise architecture
- Advanced infrastructure
- Highly abstracted code
- Maximum scalability

---

# Guiding Principle

Whenever making implementation decisions, ask:

> Does this improve the entertainment value or better showcase AI engineering?

If the answer is no, choose the simpler solution.