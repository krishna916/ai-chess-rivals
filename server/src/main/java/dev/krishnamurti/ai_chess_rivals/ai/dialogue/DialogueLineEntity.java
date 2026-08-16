package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "dialogue_line",
    uniqueConstraints =
        @UniqueConstraint(
            name = "dialogue_line_unique_trigger_speaker",
            columnNames = {"match_id", "trigger_type", "trigger_ply", "personality_key"}))
class DialogueLineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "match_id", nullable = false)
  private UUID matchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "trigger_type", nullable = false, length = 32)
  private DialogueTriggerType triggerType;

  @Column(name = "trigger_ply", nullable = false)
  private int triggerPly;

  @Column(name = "personality_key", nullable = false, length = 64)
  private String personalityKey;

  @Column(name = "personality_display_name", nullable = false, length = 80)
  private String personalityDisplayName;

  @Column(name = "dialogue_text", nullable = false, length = 280)
  private String text;

  @Enumerated(EnumType.STRING)
  @Column(name = "emotion", nullable = false, length = 32)
  private DialogueEmotion emotion;

  @Enumerated(EnumType.STRING)
  @Column(name = "reaction_type", nullable = false, length = 32)
  private DialogueReactionType reactionType;

  @Enumerated(EnumType.STRING)
  @Column(name = "response_source", nullable = false, length = 32)
  private AiResponseSource source;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected DialogueLineEntity() {
    this.id = null;
  }

  DialogueLineEntity(
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
    this.id = null;
    this.matchId = matchId;
    this.triggerType = triggerType;
    this.triggerPly = triggerPly;
    this.personalityKey = personalityKey;
    this.personalityDisplayName = personalityDisplayName;
    this.text = text;
    this.emotion = emotion;
    this.reactionType = reactionType;
    this.source = source;
    this.createdAt = createdAt;
  }

  Long id() {
    return id;
  }

  UUID matchId() {
    return matchId;
  }

  DialogueTriggerType triggerType() {
    return triggerType;
  }

  int triggerPly() {
    return triggerPly;
  }

  String personalityKey() {
    return personalityKey;
  }

  String personalityDisplayName() {
    return personalityDisplayName;
  }

  String text() {
    return text;
  }

  DialogueEmotion emotion() {
    return emotion;
  }

  DialogueReactionType reactionType() {
    return reactionType;
  }

  AiResponseSource source() {
    return source;
  }

  Instant createdAt() {
    return createdAt;
  }
}
