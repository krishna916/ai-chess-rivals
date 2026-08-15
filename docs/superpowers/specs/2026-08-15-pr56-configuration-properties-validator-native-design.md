# PR #56 Native-Safe Configuration Properties Validation Design

## Problem

PR #56 now builds the production GraalVM native image successfully and constructs the AI-enabled gateway topology, but native startup still fails during `@ConfigurationProperties` validation. The latest failure occurs while validating `ChessProperties.Stockfish.Evaluation.majorGainThresholdCentipawns` and the stack still passes through Hibernate Validator's `JPATraversableResolver`, Jakarta Persistence `PersistenceUtil`, Hibernate ORM `PersistenceUtilHelper`, and reflective `Field.get(...)`.

The previous correction added a Spring Boot `ValidationConfigurationCustomizer` that installs `AlwaysTraversableResolver` on the application's normal `ValidatorFactory`. JVM tests proved that validator uses the custom resolver, but the native failure was unchanged.

Spring Boot 4.1 source explains why: `ConfigurationPropertiesJsr303Validator` creates its own internal `LocalValidatorFactoryBean` for each `@Validated` configuration-properties type and initializes it directly. It does not use the application's normal `ValidatorFactory` and does not apply `ValidationConfigurationCustomizer` beans.

Spring Boot's `ConfigurationPropertiesBinder` also shows that a bean named `configurationPropertiesValidator` is added to configuration-properties validation independently of the built-in JSR-303 validator. As long as a properties type still has `@Validated`, Boot also creates the separate `ConfigurationPropertiesJsr303Validator`. Therefore adding the named validator without removing `@Validated` would run both validators and retain the failing JPA-aware path.

## Decision

Use Spring Boot's supported `configurationPropertiesValidator` hook as the single validation path for application configuration properties.

`ValidationConfiguration` will expose a **static** bean named `EnableConfigurationProperties.VALIDATOR_BEAN_NAME` whose type is `LocalValidatorFactoryBean`. That bean will use the existing application-owned `AlwaysTraversableResolver`, so Hibernate Validator does not consult JPA persistence reachability for ordinary configuration records.

Remove `@Validated` and its import from the six application `@ConfigurationProperties` types that currently use it:

- `AiProperties`
- `ChessProperties`
- `GameProperties`
- `MatchGuardProperties`
- `OwnerControlProperties`
- `WebSocketProperties`

Keep all Jakarta Bean Validation constraints exactly as they are, including `@Valid`, `@NotNull`, `@NotBlank`, `@NotEmpty`, `@Min`, `@Max`, and `@AssertTrue`.

This causes `ConfigurationPropertiesBinder` to run the named `configurationPropertiesValidator` while no longer creating Boot's separate per-type `ConfigurationPropertiesJsr303Validator`.

## Validation Semantics

The change must preserve startup validation behavior. Valid configuration must bind successfully and invalid configuration must still fail application-context startup.

The regression test must exercise the real configuration-properties binding path rather than fetching the application's general `ValidatorFactory`. It must bind `ChessProperties` through `@EnableConfigurationProperties`, prove the named `configurationPropertiesValidator` exists and uses `AlwaysTraversableResolver`, and prove the same nested chess threshold that failed natively is rejected when configured as `0`.

Existing `ApplicationContextRunner` tests that relied on `@Validated` must explicitly include `ValidationConfiguration` in their user configuration so they continue testing the new named validator path.

## Native Reflection Boundary

Do not add field reflection hints for `ChessProperties`, `GameProperties`, or other configuration records as part of this correction. The current failure is caused by the JPA traversability check, and this design removes that check from the configuration-properties validation path.

Do not remove or shrink the existing `AiRuntimeHints` yet. They already cover AI `@AssertTrue` method invocation and changing them in the same correction would introduce another variable.

`GameProperties` and `MatchGuardProperties` also contain `@AssertTrue` methods. If a fresh hosted native run later fails specifically on reflective invocation of one of those methods, treat that as a separate, bounded method-invocation metadata issue. Do not reintroduce field-access hints or revert this validator design without evidence.

## Scope

### Modify

- `server/src/main/java/dev/krishnamurti/ai_chess_rivals/ValidationConfiguration.java`
- six existing configuration-properties classes listed above
- `server/src/test/java/dev/krishnamurti/ai_chess_rivals/ValidationConfigurationTest.java`
- existing binding/context tests that construct isolated application contexts and rely on configuration validation

### Preserve

- `AlwaysTraversableResolver`
- all Jakarta validation constraints and messages
- `AiRuntimeHints` / `AiRuntimeHintsTest`
- Dockerfile and Compose behavior
- GitHub Actions native verification workflow
- AI AOT topology and provider environment contract
- Stockfish behavior
- JPA mappings
- Flyway
- frontend

## Success Criteria

1. The named `configurationPropertiesValidator` is a `LocalValidatorFactoryBean` using `AlwaysTraversableResolver`.
2. None of the six application configuration-properties types has `@Validated`.
3. Existing valid property sets continue to bind.
4. Existing invalid AI/game/match/owner property sets still fail startup.
5. A binding-level chess regression rejects `major-gain-threshold-centipawns=0` through the named validator path.
6. Backend and repository verification pass.
7. Hosted native CI starts the actual native application without entering `JPATraversableResolver` for configuration-properties validation.
8. The existing AI-enabled topology marker and image environment leak assertions remain unchanged.