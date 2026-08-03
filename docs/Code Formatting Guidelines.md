# AI Chess Rivals — Code Formatting Guidelines

## Purpose

Formatting is automated and must remain consistent across human and AI-generated changes.

Do not introduce another formatter or manually enforce a competing style. Use the formatter already configured for each part of the repository.

## General Rules

- Format changed code before running the final verification command.
- Do not reformat unrelated files merely to create a cleaner-looking diff.
- Do not disable formatter rules for personal preference.
- Keep formatting-only changes separate from behavioral changes when they would otherwise create a large or noisy diff.
- Generated files, downloaded Stockfish binaries, build output, and dependency directories must not be formatted or committed unless the repository explicitly tracks them.
- A pull request is not complete while a formatter check is failing.

## Backend — Java

Java formatting is controlled by the Spotless Maven plugin using Google Java Format.

From the repository root:

### Windows

```powershell
server\mvnw.cmd -f server\pom.xml spotless:apply
server\mvnw.cmd -f server\pom.xml spotless:check
```

### POSIX

```sh
./server/mvnw -f server/pom.xml spotless:apply
./server/mvnw -f server/pom.xml spotless:check
```

Rules:

- Run `spotless:apply` after editing Java source.
- Do not manually align Java code with spaces or preserve formatting that Google Java Format rewrites.
- Keep imports formatter-compatible; Spotless removes unused imports.
- Do not add formatter suppression unless required for generated or externally mandated source, and document the reason.

The backend `verify` lifecycle includes the Spotless check, so formatting failures must be fixed rather than bypassed.

## Frontend — TypeScript, React, and Supported Text Files

Frontend formatting is controlled by Prettier through the scripts in `client/package.json`.

From `client/`:

```text
npm run format
npm run format:check
```

Rules:

- Run `npm run format` after changing frontend source or other files covered by the client Prettier configuration.
- Do not hand-tune whitespace that Prettier will replace.
- Do not mix ESLint rule fixes with formatting rules unless the existing configuration requires both.
- Use the repository's existing Prettier configuration and ignore files; do not add editor-specific formatting conventions that conflict with it.

`npm run verify` includes formatting validation along with type checking, linting, tests/build checks defined by the repository.

## Markdown, YAML, JSON, and Configuration Files

- Files under `client/` that are covered by Prettier should be formatted through `npm run format`.
- For repository-level or backend configuration files not covered by an automated formatter, preserve the surrounding style and keep diffs minimal.
- Do not run broad third-party formatters over Flyway SQL migrations or existing documentation unless explicitly requested.
- Never rewrite an already-applied Flyway migration solely for formatting; create a new migration for schema changes.

## Required Final Verification

After formatting, run the root verifier:

### Windows

```powershell
.\scripts\verify.ps1
```

### POSIX

```sh
./scripts/verify.sh
```

The root verifier is authoritative. Formatter-specific checks are useful for fast feedback, but they do not replace full repository verification.

## AI Coding Agent Expectations

Before declaring implementation complete, an AI coding agent must:

1. Format the files it changed using the configured formatter.
2. Review the diff for accidental unrelated formatting changes.
3. Run the relevant formatter check.
4. Run the root verification script.
5. Report any verification step it could not run instead of claiming success.
