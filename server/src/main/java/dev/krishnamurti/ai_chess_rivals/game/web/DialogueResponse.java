package dev.krishnamurti.ai_chess_rivals.game.web;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import java.time.Instant;
import java.util.UUID;

public record DialogueResponse(
    long id,
    UUID matchId,
    DialogueTriggerType triggerType,
    int triggerPly,
    String personalityKey,
    String personalityDisplayName,
    String text,
    DialogueEmotion emotion,
    DialogueReactionType reactionType,
    AiResponseSource source,
    Instant createdAt) {

  public static DialogueResponse from(PersistedDialogue dialogue) {
    return new DialogueResponse(
        dialogue.id(),
        dialogue.matchId(),
        dialogue.triggerType(),
        dialogue.triggerPly(),
        dialogue.personalityKey(),
        dialogue.personalityDisplayName(),
        dialogue.text(),
        dialogue.emotion(),
        dialogue.reactionType(),
        dialogue.source(),
        dialogue.createdAt());
  }
}
