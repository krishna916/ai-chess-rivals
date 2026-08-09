package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class DeterministicFallbackCatalogTest {

  private final DeterministicFallbackCatalog catalog = new DeterministicFallbackCatalog();

  @Test
  void returnsExactBlazeFallbacks() {
    assertThat(catalog.fallbackFor("blaze", DialogueFallbackKind.START))
        .isEqualTo("Bell's rung. Keep your king cool—I brought the heat.");
    assertThat(catalog.fallbackFor("blaze", DialogueFallbackKind.ORDINARY_REACTION))
        .isEqualTo("Pressure's climbing. Hope you packed an exit.");
    assertThat(catalog.fallbackFor("blaze", DialogueFallbackKind.FAILURE_RECOVERY))
        .isEqualTo("No speech needed. The board's loud enough.");
    assertThat(catalog.fallbackFor("blaze", DialogueFallbackKind.VICTORY))
        .isEqualTo("That's the final whistle. I own the highlight reel.");
    assertThat(catalog.fallbackFor("blaze", DialogueFallbackKind.DEFEAT))
        .isEqualTo("You got this one. Enjoy it before the rematch catches fire.");
  }

  @Test
  void includesAllPersonalityFallbacks() {
    List<String> keys = List.of("blaze", "vesper", "gremlin", "regent");
    for (String key : keys) {
      for (DialogueFallbackKind kind : DialogueFallbackKind.values()) {
        assertThat(catalog.fallbackFor(key, kind)).isNotBlank();
      }
    }
    assertThat(catalog.fallbackFor("vesper", DialogueFallbackKind.ORDINARY_REACTION))
        .isEqualTo("That loosened more than you think.");
    assertThat(catalog.fallbackFor("gremlin", DialogueFallbackKind.VICTORY))
        .isEqualTo("Somehow, the nonsense was structurally sound.");
    assertThat(catalog.fallbackFor("regent", DialogueFallbackKind.DEFEAT))
        .isEqualTo("A temporary abdication. Do try not to redecorate.");
  }

  @Test
  void rejectsUnknownPersonality() {
    assertThatThrownBy(() -> catalog.fallbackFor("unknown", DialogueFallbackKind.START))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("No deterministic fallback for personality: unknown");
  }
}
