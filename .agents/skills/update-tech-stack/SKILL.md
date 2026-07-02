---
name: update-tech-stack
description: Use when package.json, pom.xml, compose.yaml, or any configuration or dependency files are modified, added, or removed, to ensure the tech stack documentation remains accurate and up to date.
---

# Update Tech Stack

## Overview
The tech stack document [docs/AI Chess Rivals - Tech Stack.md](file:///D:/projects/ai-chess-rivals/docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md) is the single source of truth for the technologies, libraries, and versions used in this repository. It must always match the exact state of the codebase.

## Core Rule
Any modification, addition, or removal of libraries or configurations in the project (including but not limited to [client/package.json](file:///D:/projects/ai-chess-rivals/client/package.json), [server/pom.xml](file:///D:/projects/ai-chess-rivals/server/pom.xml), and [server/compose.yaml](file:///D:/projects/ai-chess-rivals/server/compose.yaml)) **MUST** immediately be accompanied by a corresponding update to [docs/AI Chess Rivals - Tech Stack.md](file:///D:/projects/ai-chess-rivals/docs/AI%20Chess%20Rivals%20-%20Tech%20Stack.md).

## Loophole Controls
*   **No Version Exemption**: Do not skip updating the document for "minor," "patch," or "security hotfix" dependency updates. All version changes must be tracked.

## Rationalization Table

| Excuse | Reality |
|---|---|
| "This is a critical security hotfix; we must deploy immediately and update the docs tomorrow." | A documentation update is a simple text change that takes less than 30 seconds. Deferring it guarantees documentation drift. Update it now. |
| "Updating the documentation introduces risk of missing our deploy window." | Updating a markdown file carries zero execution risk. Forgetting it creates immediate technical debt. |
| "It's just a minor/patch version bump, so it's not worth updating." | The tech stack document lists exact versions. Any bump is a mismatch if not documented. |


## Red Flags - STOP and Correct
*   Thinking: *"I'll update the tech stack doc later."*
*   Thinking: *"It's just a minor patch, so it doesn't matter."*
*   Thinking: *"We are in a rush, so documentation is secondary."*

