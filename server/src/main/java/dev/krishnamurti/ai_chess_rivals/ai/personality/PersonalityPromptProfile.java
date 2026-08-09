package dev.krishnamurti.ai_chess_rivals.ai.personality;

import java.math.BigDecimal;
import java.util.Objects;

public record PersonalityPromptProfile(
    String key,
    String displayName,
    String promptTraits,
    BigDecimal speakingProbability,
    String styleGuidance,
    String boundaryGuidance) {

  public PersonalityPromptProfile {
    requireText(key, "key");
    requireText(displayName, "displayName");
    requireText(promptTraits, "promptTraits");
    Objects.requireNonNull(speakingProbability, "speakingProbability must not be null");
    if (speakingProbability.compareTo(BigDecimal.ZERO) < 0
        || speakingProbability.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("speakingProbability must be between 0 and 1");
    }
    requireText(styleGuidance, "styleGuidance");
    requireText(boundaryGuidance, "boundaryGuidance");
  }

  static PersonalityPromptProfile from(PersonalityEntity entity) {
    return new PersonalityPromptProfile(
        entity.personalityKey(),
        entity.displayName(),
        entity.promptTraits(),
        entity.speakingProbability(),
        entity.styleGuidance(),
        entity.boundaryGuidance());
  }

  private static void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
