# PR #56 Native Validation Traversable Resolver Design

## Problem

PR #56 now proves that the AI-enabled AOT topology itself is working: the native application reaches `AI gateway topology: enabled (Groq -> Gemini)`. The current failure happens later while Spring Boot binds and validates unrelated `ChessProperties`.

The failing stack path is:

```text
ConfigurationPropertiesJsr303Validator
-> Hibernate Validator
-> JPATraversableResolver
-> jakarta.persistence.PersistenceUtil.isLoaded(...)
-> Hibernate ORM PersistenceUtilHelper field access
-> GraalVM MissingReflectionRegistrationError
```

The latest concrete failure is reflective access to the private record backing field `ChessProperties.Stockfish.Evaluation.majorGainThresholdCentipawns`.

Previous AI-specific runtime hints were effective: the earlier `AiProperties` reflection failures are gone. The problem has therefore widened beyond AI configuration and is now caused by Hibernate Validator's JPA-aware reachability check being applied to ordinary configuration-property records.

## Current Usage Boundary

Jakarta Bean Validation is currently used for configuration-property validation such as:

- `AiProperties`
- `ChessProperties`
- `GameProperties`
- other application configuration records

The current JPA personality entity does not use Jakarta Bean Validation annotations. Therefore the JPA-aware traversability behavior that protects lazy entity associations is not currently providing application value for validation, while it causes native reflection requirements for configuration record backing fields.

## Chosen Approach

Configure Hibernate Validator through Spring Boot's supported `ValidationConfigurationCustomizer` hook and replace the automatically selected JPA-aware `TraversableResolver` with an application-owned resolver that always returns `true` from both `isReachable(...)` and `isCascadable(...)`.

Spring Boot remains responsible for creating the default validator. Existing `@Validated`, `@Valid`, `@NotNull`, `@NotBlank`, `@Min`, `@Max`, and `@AssertTrue` constraints remain unchanged.

### Production structure

Add two small classes in the root application package so the configuration is component-scanned without creating another Spring Modulith application module:

- `ValidationConfiguration`
  - Spring `@Configuration(proxyBeanMethods = false)`.
  - exposes one `ValidationConfigurationCustomizer` bean.
  - configures `AlwaysTraversableResolver.INSTANCE`.
- `AlwaysTraversableResolver`
  - package-private singleton implementation of `jakarta.validation.TraversableResolver`.
  - `isReachable(...)` returns `true`.
  - `isCascadable(...)` returns `true`.

## Why This Instead of More Reflection Hints

Adding native hints for every configuration record would repeat the same failure one class at a time. Spring Boot already handles configuration-property binding metadata during AOT; the additional reflection is coming from Hibernate Validator's JPA reachability check.

A single traversability customization fixes the cause at the validation layer rather than expanding GraalVM metadata across unrelated configuration classes.

## Scope Boundaries

This correction must not:

- add native reflection hints for `ChessProperties`, `GameProperties`, or other configuration records;
- remove or narrow the existing `AiRuntimeHints` yet;
- rewrite Bean Validation constraints as manual Java validation;
- change `server/Dockerfile`, `server/docker-compose.yml`, `.github/workflows/ci.yml`, or the AI build/runtime environment contract;
- change AI provider/gateway topology;
- change Stockfish behavior;
- change persistence mappings;
- address unrelated Flyway or PostgreSQL dialect warnings.

Keeping `AiRuntimeHints` temporarily is intentional: the next native run should change only the traversability behavior. Reflection-hint cleanup can be evaluated separately after hosted native CI is green.

## Testing

Add one focused Spring Boot validation configuration test that:

1. starts `ValidationAutoConfiguration` with `ValidationConfiguration`;
2. verifies the resulting `ValidatorFactory` uses `AlwaysTraversableResolver.INSTANCE`;
3. validates an intentionally invalid nested `ChessProperties` instance and confirms the expected nested constraint violation still occurs.

Then rerun the existing AI/chess/game configuration validation suites and full backend verification.

Hosted `CI / Native image verification` remains the authoritative native-runtime test.

## Success Criteria

The change succeeds only when a fresh hosted native run:

1. builds the native image successfully;
2. starts the application without `JPATraversableResolver` / `PersistenceUtilHelper` reflection failures;
3. reaches health;
4. emits `AI gateway topology: enabled (Groq -> Gemini)`;
5. passes the existing image environment-leak assertion.

If native startup still fails with a `MissingReflectionRegistrationError`, capture the exact new stack path before changing anything else. Do not resume broad per-class reflection-hint additions automatically.

## Future Caveat

If Jakarta Bean Validation is later added to JPA entities with lazy associations, revisit this resolver. The JPA-aware resolver exists to avoid traversing unloaded persistent relationships during validation. That use case does not currently exist in this application.