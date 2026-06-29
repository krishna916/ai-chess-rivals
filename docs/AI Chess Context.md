# Project Lifecycle

AI Chess Rivals is a short-lived showcase project.

Unlike Horizon, this project is **not intended to evolve into a long-term production application**.

The goal is to build a polished demonstration of AI engineering capabilities, iterate a few times based on learnings, and consider the project complete.

Expect approximately 3–4 meaningful feature iterations after the MVP.

This assumption should influence every technical decision.

---

# Showcase-First Philosophy

This project exists to demonstrate practical AI engineering skills, including:

- LLM integration
- Prompt engineering
- AI-assisted workflows
- Agent design
- Product thinking
- End-to-end application development

The project is **not** intended to demonstrate enterprise software architecture.

Avoid introducing complexity solely to satisfy architectural ideals.

---

# Engineering Philosophy

Optimize for:

- Shipping quickly
- Learning quickly
- Demonstrating AI capabilities
- Readable code
- Simple implementation
- Low maintenance

Do not optimize for:

- Years of maintainability
- Large team collaboration
- Massive scalability
- Enterprise architecture
- Perfect abstractions
- Highly extensible frameworks

A working solution is preferred over an architecturally perfect one.

---

# Architecture Philosophy

Choose the simplest implementation that works.

It is acceptable to:

- Duplicate a small amount of code.
- Keep services relatively coarse-grained.
- Delay abstraction until clearly needed.
- Use straightforward synchronous flows.
- Accept small technical debt if it accelerates delivery.

Avoid introducing patterns whose primary benefit would only appear in a long-lived production system.

Examples include:

- Event-driven architectures
- Microservices
- Complex domain abstractions
- Plugin systems
- Generic orchestration frameworks
- Premature optimization

Complexity should only be added when it directly improves the viewer experience or significantly simplifies development.

---

# Decision Filter

When evaluating any technical suggestion, apply the following questions in order:

1. Does this make the project more entertaining?
2. Does this better demonstrate AI engineering skills?
3. Will this likely be useful within the next 3–4 iterations?
4. Is there a significantly simpler solution?

If the answer to (3) is "No", or the answer to (4) is "Yes", prefer the simpler implementation.