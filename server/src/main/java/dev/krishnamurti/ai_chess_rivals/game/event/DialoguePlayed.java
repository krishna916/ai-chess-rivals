package dev.krishnamurti.ai_chess_rivals.game.event;

import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import java.util.Objects;

public record DialoguePlayed(PersistedDialogue dialogue) implements MatchEvent {

  public DialoguePlayed {
    Objects.requireNonNull(dialogue, "dialogue must not be null");
  }
}
