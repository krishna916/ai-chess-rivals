# PR #56 AI Properties Declared-Field Reflection Design

## Problem

The previous native-reflection fix registered invocation hints for the three `@AssertTrue` validation methods in `AiProperties`. That worked: the original `MissingReflectionRegistrationError` for `AiProperties.isEnabledConfigurationComplete()` disappeared.

The next hosted native run now fails later while Hibernate Validator traverses the nested configuration object and reflectively reads the private record backing field `AiProperties.Groq.timeout`.

This proves the remaining gap is not AI topology selection, Docker build configuration, or gateway wiring. The native image lacks reflective field-access metadata for the small `AiProperties` record hierarchy used by Jakarta Bean Validation.

## Chosen Approach

Extend the existing `AiRuntimeHints` registrar with Spring Framework's `MemberCategory.ACCESS_DECLARED_FIELDS` for exactly these three application-owned configuration records:

- `AiProperties`
- `AiProperties.Groq`
- `AiProperties.Gemini`

Retain the existing exact invocation hints for:

- `AiProperties#isEnabledConfigurationComplete()`
- `AiProperties.Groq#isTimeoutPositive()`
- `AiProperties.Gemini#isTimeoutWithinHttpOptionsRange()`

Use the existing `AiRuntimeHintsTest` to prove both contracts:

1. each validation method remains invokable reflectively;
2. declared fields on each of the three configuration records are reflectively accessible.

The implementation should use `TypeHint.Builder.withMembers(MemberCategory.ACCESS_DECLARED_FIELDS)` on the existing three type registrations rather than introducing separate metadata files or a new hints component.

## Why This Scope

The new stack trace shows Hibernate Validator's JPA traversability resolver calling reflective `Field.get(...)` on a record backing field. Registering only `Groq.timeout` would likely create a member-by-member native-debugging loop. Registering declared-field access for these three tiny records is still tightly bounded while covering the complete configuration object graph that validation traverses.

This must not expand into broad application-wide reflection registration.

## Out of Scope

Do not change:

- `AiProperties` validation rules or record structure
- `AiConfig`
- `AiProviderConfiguration`
- `AiGatewayConfiguration`
- provider/gateway behavior
- Dockerfile or Docker Compose
- GitHub Actions topology verifier
- build-time/runtime AI environment contract
- Flyway warnings or unrelated native-image issues

Do not add `reachability-metadata.json`.

## Testing Strategy

Update `AiRuntimeHintsTest` first so it fails against the current method-only registrar. The test must keep all three existing method-invocation assertions and additionally verify field-access hints for the complete record hierarchy.

At minimum, assert `onFieldAccess(...)` for every declared record component:

### `AiProperties`

- `enabled`
- `groq`
- `gemini`

### `AiProperties.Groq`

- `apiKey`
- `baseUrl`
- `model`
- `timeout`

### `AiProperties.Gemini`

- `apiKey`
- `model`
- `timeout`

After the focused hints test is green, rerun existing AI configuration/binding tests and backend verification. The existing hosted native-image workflow remains the authoritative artifact-level validation.

## Stop Condition

This is the last planned widening of the runtime-hints approach.

If a fresh native run still fails with a different Hibernate Validator reflection requirement outside these three `AiProperties` records, stop and report the exact new stack trace before adding more metadata. At that point, reconsider using Bean Validation for this configuration-properties path rather than continuing to grow native reflection hints.