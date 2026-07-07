# Build and Verify

The repository provides lightweight verification gates for Java and frontend code. Run the
root verifier before opening a pull request.

## Prerequisites

- JDK 25
- Maven 3.9 or newer (the Maven wrapper is included)
- Node.js 22 or newer and npm

## Whole repository

From any working directory, use the script appropriate for your shell:

```powershell
D:\projects\ai-chess-rivals\scripts\verify.ps1
```

```sh
./scripts/verify.sh
```

The scripts stop at the first failing backend or frontend check.

## Backend

Run Maven verification from the repository root:

```powershell
server\mvnw.cmd -f server\pom.xml verify
```

```sh
./server/mvnw -f server/pom.xml verify
```

The verify lifecycle enforces Java 25 and Maven 3.9+, checks Java formatting, compiles with
Error Prone, runs tests (including Spring Modulith structure verification), and runs SpotBugs.
Apply Java formatting with `server\mvnw.cmd -f server\pom.xml spotless:apply` on Windows or
`./server/mvnw -f server/pom.xml spotless:apply` on POSIX systems.

## Frontend

Run these commands from `client/`:

```text
npm run format
npm run format:check
npm run typecheck
npm run verify
```

`npm run verify` sequentially runs formatting, type checking, linting, and the production
build. It stops at the first failure and does not rely on shell-specific command chaining.
