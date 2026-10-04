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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
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

    long duplicateLookupStartedAt = System.nanoTime();
    log.info(
        "event=db.started operation=dialogue_duplicate_lookup matchId={} triggerType={} triggerPly={}",
        matchId,
        triggerType,
        triggerPly);
    boolean duplicate;
    try {
      duplicate =
          repository
              .findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
                  matchId, triggerType, triggerPly, dialogue.personalityKey())
              .isPresent();
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=dialogue_duplicate_lookup matchId={} triggerType={} triggerPly={} exceptionType={} durationMs={}",
          matchId,
          triggerType,
          triggerPly,
          exception.getClass().getSimpleName(),
          elapsedMillis(duplicateLookupStartedAt));
      throw exception;
    }
    log.info(
        "event=db.completed operation=dialogue_duplicate_lookup matchId={} triggerType={} triggerPly={} outcome={} durationMs={}",
        matchId,
        triggerType,
        triggerPly,
        duplicate ? "duplicate" : "absent",
        elapsedMillis(duplicateLookupStartedAt));
    if (duplicate) {
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
    long saveStartedAt = System.nanoTime();
    log.info(
        "event=db.started operation=dialogue_save matchId={} triggerType={} triggerPly={}",
        matchId,
        triggerType,
        triggerPly);
    try {
      PersistedDialogue persisted = toApi(repository.save(entity));
      log.info(
          "event=db.completed operation=dialogue_save outcome=save_returned id={} matchId={} triggerType={} triggerPly={} durationMs={}",
          persisted.id(),
          matchId,
          triggerType,
          triggerPly,
          elapsedMillis(saveStartedAt));
      return Optional.of(persisted);
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=dialogue_save matchId={} triggerType={} triggerPly={} exceptionType={} durationMs={}",
          matchId,
          triggerType,
          triggerPly,
          exception.getClass().getSimpleName(),
          elapsedMillis(saveStartedAt));
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<PersistedDialogue> findAll(UUID matchId) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    long startedAtNanos = System.nanoTime();
    log.info("event=db.started operation=dialogue_history matchId={}", matchId);
    try {
      List<PersistedDialogue> lines =
          repository.findAllByMatchIdOrderByIdAsc(matchId).stream()
              .map(DialoguePersistenceService::toApi)
              .toList();
      log.info(
          "event=db.completed operation=dialogue_history matchId={} outcome=success resultCount={} durationMs={}",
          matchId,
          lines.size(),
          elapsedMillis(startedAtNanos));
      return lines;
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=dialogue_history matchId={} exceptionType={} durationMs={}",
          matchId,
          exception.getClass().getSimpleName(),
          elapsedMillis(startedAtNanos));
      throw exception;
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<DialogueHistoryLine> lastFour(UUID matchId) {
    Objects.requireNonNull(matchId, "matchId must not be null");
    long startedAtNanos = System.nanoTime();
    log.info("event=db.started operation=dialogue_recent_history matchId={}", matchId);
    List<DialogueLineEntity> newestFirst;
    try {
      newestFirst = repository.findTop4ByMatchIdOrderByIdDesc(matchId);
    } catch (RuntimeException exception) {
      log.info(
          "event=db.failed operation=dialogue_recent_history matchId={} exceptionType={} durationMs={}",
          matchId,
          exception.getClass().getSimpleName(),
          elapsedMillis(startedAtNanos));
      throw exception;
    }
    List<DialogueHistoryLine> chronological = new ArrayList<>(newestFirst.size());
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
      DialogueLineEntity row = newestFirst.get(i);
      chronological.add(
          new DialogueHistoryLine(
              row.triggerPly(), row.personalityKey(), row.personalityDisplayName(), row.text()));
    }
    List<DialogueHistoryLine> result = List.copyOf(chronological);
    log.info(
        "event=db.completed operation=dialogue_recent_history matchId={} outcome=success resultCount={} durationMs={}",
        matchId,
        result.size(),
        elapsedMillis(startedAtNanos));
    return result;
  }

  private static long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000L;
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
