package dev.krishnamurti.ai_chess_rivals.ai.api;

import java.util.Optional;

public interface SelectablePersonalityCatalog {
  Optional<SelectablePersonality> findSelectable(String personalityKey);
}
