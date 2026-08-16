package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryStore;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DialoguePersistenceService implements DialogueHistoryStore {

  private final DialogueLineRepository repository;
  private final PersonalityService personalityService;

  DialoguePersistenceService(
      DialogueLineRepository repository, PersonalityService personalityService) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.personalityService =
        Objects.requireNonNull(personalityService, "personalityService must not be null");
  }

  @Override
  @Transactional
  public Optional<PersistedDialogue> persistIfAbsent(
      UUID matchId, DialogueTriggerType triggerType, int triggerPly, GeneratedDialogue dialogue) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    Objects.requireNonNull(triggerType, "triggerType must not be null");
    Objects.requireNonNull(dialogue, "dialogue must not be null");
    if (triggerPly < 0) {
      throw new IllegalArgumentException("triggerPly must not be negative");
    }

    if (repository
        .findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            matchId, triggerType, triggerPly, dialogue.personalityKey())
        .isPresent()) {
      return Optional.empty();
    }

    String displayName =
        personalityService.requirePromptProfile(dialogue.personalityKey()).displayName();
    DialogueLineEntity entity =
        new DialogueLineEntity(
            matchId,
            triggerType,
            triggerPly,
            dialogue.personalityKey(),
            displayName,
            dialogue.text(),
            dialogue.emotion(),
            dialogue.reactionType(),
            dialogue.source(),
            Instant.now());
    return Optional.of(toApi(repository.save(entity)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PersistedDialogue> findAll(UUID matchId) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    return repository.findAllByMatchIdOrderByIdAsc(matchId).stream()
        .map(DialoguePersistenceService::toApi)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<DialogueHistoryLine> lastFour(UUID matchId) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    List<DialogueLineEntity> newestFirst = repository.findTop4ByMatchIdOrderByIdDesc(matchId);
    List<DialogueHistoryLine> chronological = new ArrayList<>(newestFirst.size());
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
      DialogueLineEntity row = newestFirst.get(i);
      chronological.add(
          new DialogueHistoryLine(
              row.triggerPly(), row.personalityKey(), row.personalityDisplayName(), row.text()));
    }
    return List.copyOf(chronological);
  }

  private static PersistedDialogue toApi(DialogueLineEntity entity) {
    Long id = entity.id();
    if (id == null) {
      throw new IllegalStateException("Persisted dialogue entity has no generated id");
    }
    return new PersistedDialogue(
        id,
        entity.matchId(),
        entity.triggerType(),
        entity.triggerPly(),
        entity.personalityKey(),
        entity.personalityDisplayName(),
        entity.text(),
        entity.emotion(),
        entity.reactionType(),
        entity.source(),
        entity.createdAt());
  }
}
