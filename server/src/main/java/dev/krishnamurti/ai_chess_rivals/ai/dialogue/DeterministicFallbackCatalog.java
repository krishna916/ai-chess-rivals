package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
final class DeterministicFallbackCatalog {

  private static final Map<String, Map<DialogueFallbackKind, String>> FALLBACKS = fallbacks();

  String fallbackFor(String personalityKey, DialogueFallbackKind kind) {
    Map<DialogueFallbackKind, String> fallbacks = FALLBACKS.get(personalityKey);
    if (fallbacks == null) {
      throw new IllegalArgumentException(
          "No deterministic fallback for personality: " + personalityKey);
    }
    return fallbacks.get(kind);
  }

  private static Map<String, Map<DialogueFallbackKind, String>> fallbacks() {
    return Map.of(
        "blaze",
        fallbackMap(
            "Bell's rung. Keep your king cool—I brought the heat.",
            "Pressure's climbing. Hope you packed an exit.",
            "No speech needed. The board's loud enough.",
            "That's the final whistle. I own the highlight reel.",
            "You got this one. Enjoy it before the rematch catches fire."),
        "vesper",
        fallbackMap(
            "Proceed. The position will explain itself.",
            "That loosened more than you think.",
            "Silence is acceptable. Continue.",
            "As expected. The position reached its conclusion.",
            "A clean result. I will adjust."),
        "gremlin",
        fallbackMap(
            "Excellent. I have brought absolutely responsible chess decisions.",
            "Tiny move. Suspicious amount of chaos.",
            "The goblin department declines to comment.",
            "Somehow, the nonsense was structurally sound.",
            "Rude. I was saving my best disaster for later."),
        "regent",
        fallbackMap(
            "Take your seat. The board is now holding court.",
            "A curious petition. I am inclined to deny it.",
            "The court will let the position speak.",
            "Check the record: the crown remains exactly where it belongs.",
            "A temporary abdication. Do try not to redecorate."));
  }

  private static Map<DialogueFallbackKind, String> fallbackMap(
      String start, String ordinary, String recovery, String victory, String defeat) {
    EnumMap<DialogueFallbackKind, String> values = new EnumMap<>(DialogueFallbackKind.class);
    values.put(DialogueFallbackKind.START, start);
    values.put(DialogueFallbackKind.ORDINARY_REACTION, ordinary);
    values.put(DialogueFallbackKind.FAILURE_RECOVERY, recovery);
    values.put(DialogueFallbackKind.VICTORY, victory);
    values.put(DialogueFallbackKind.DEFEAT, defeat);
    return Map.copyOf(values);
  }
}
