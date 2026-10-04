package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityPromptProfile;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class DialoguePersistenceServiceTest {

  private static final UUID MATCH_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  private final DialogueLineRepository repository =
      org.mockito.Mockito.mock(DialogueLineRepository.class);
  private final PersonalityService personalityService =
      org.mockito.Mockito.mock(PersonalityService.class);
  private final DialoguePersistenceService service =
      new DialoguePersistenceService(repository, personalityService);

  @BeforeEach
  void setUpPersonalityProfile() {
    when(personalityService.requirePromptProfile("blaze")).thenReturn(profile("blaze", "Blaze"));
  }

  @Test
  void returnsEmptyAndDoesNotSaveWhenTriggerSpeakerAlreadyExists() {
    when(repository.findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            MATCH_ID, DialogueTriggerType.MOVE, 7, "blaze"))
        .thenReturn(Optional.of(entity(1, 7)));

    Optional<PersistedDialogue> saved =
        service.persistIfAbsent(
            MATCH_ID, DialogueTriggerType.MOVE, 7, generated("blaze", "Still standing."));

    assertThat(saved).isEmpty();
    verify(repository, never()).save(any());
  }

  @Test
  void lastFourReturnsOldestToNewestAfterReadingNewestRows() {
    when(repository.findTop4ByMatchIdOrderByIdDesc(MATCH_ID))
        .thenReturn(List.of(entity(9, 9), entity(8, 8), entity(7, 7), entity(6, 6)));

    assertThat(service.lastFour(MATCH_ID))
        .extracting(DialogueHistoryLine::triggeringPly)
        .containsExactly(6, 7, 8, 9);
  }

  @Test
  void persistsFallbackSourceWithoutSpecialCase() {
    GeneratedDialogue fallback =
        new GeneratedDialogue(
            "blaze",
            "Fine. We continue.",
            DialogueEmotion.NEUTRAL,
            DialogueReactionType.MOVE_REACTION,
            AiResponseSource.DETERMINISTIC_FALLBACK);
    when(repository.findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            MATCH_ID, DialogueTriggerType.MOVE, 4, "blaze"))
        .thenReturn(Optional.empty());
    when(repository.save(any(DialogueLineEntity.class)))
        .thenAnswer(
            invocation -> {
              DialogueLineEntity saved = invocation.getArgument(0);
              assignId(saved, 10);
              return saved;
            });

    PersistedDialogue saved =
        service.persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 4, fallback).orElseThrow();

    assertThat(saved.source()).isEqualTo(AiResponseSource.DETERMINISTIC_FALLBACK);
    assertThat(saved.text()).isEqualTo("Fine. We continue.");
  }

  @Test
  void logsSaveReturnMetadataWithoutDialogueText() {
    String dialogueText = "Sensitive generated line that must not appear in logs";
    when(repository.findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            MATCH_ID, DialogueTriggerType.MOVE, 4, "blaze"))
        .thenReturn(Optional.empty());
    when(repository.save(any(DialogueLineEntity.class)))
        .thenAnswer(
            invocation -> {
              DialogueLineEntity saved = invocation.getArgument(0);
              assignId(saved, 42);
              return saved;
            });
    Logger logger = (Logger) LoggerFactory.getLogger(DialoguePersistenceService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    MDC.put("requestId", "dialogue-trace-001");
    try {
      service.persistIfAbsent(
          MATCH_ID, DialogueTriggerType.MOVE, 4, generated("blaze", dialogueText));

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anySatisfy(
              message ->
                  assertThat(message)
                      .contains("operation=dialogue_duplicate_lookup", "outcome=absent"))
          .anySatisfy(
              message ->
                  assertThat(message)
                      .contains("operation=dialogue_save", "outcome=save_returned", "id=42")
                      .doesNotContain(dialogueText));
      assertThat(appender.list)
          .allSatisfy(
              event ->
                  assertThat(event.getMDCPropertyMap())
                      .containsEntry("requestId", "dialogue-trace-001"));
    } finally {
      MDC.clear();
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void repositoryFailureDoesNotLogSuccessfulHistoryCompletion() {
    when(repository.findAllByMatchIdOrderByIdAsc(MATCH_ID))
        .thenThrow(new IllegalStateException("private database detail"));
    Logger logger = (Logger) LoggerFactory.getLogger(DialoguePersistenceService.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    MDC.put("requestId", "dialogue-failure-001");
    try {
      assertThatThrownBy(() -> service.findAll(MATCH_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("private database detail");

      assertThat(MDC.get("requestId")).isEqualTo("dialogue-failure-001");
      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anySatisfy(
              message ->
                  assertThat(message)
                      .contains(
                          "event=db.failed",
                          "operation=dialogue_history",
                          "exceptionType=IllegalStateException")
                      .doesNotContain("private database detail"))
          .noneSatisfy(message -> assertThat(message).contains("event=db.completed"));
    } finally {
      MDC.clear();
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void findAllPreservesRepositoryIdOrder() {
    when(repository.findAllByMatchIdOrderByIdAsc(MATCH_ID))
        .thenReturn(List.of(entity(2, 2), entity(4, 4), entity(8, 8)));

    assertThat(service.findAll(MATCH_ID))
        .extracting(PersistedDialogue::id)
        .containsExactly(2L, 4L, 8L);
  }

  @Test
  void persistsDisplayNameFromPersonalityProfile() {
    when(repository.findByMatchIdAndTriggerTypeAndTriggerPlyAndPersonalityKey(
            MATCH_ID, DialogueTriggerType.MOVE, 4, "blaze"))
        .thenReturn(Optional.empty());
    DialogueLineEntity savedEntity = entity(11, 4);
    when(repository.save(any(DialogueLineEntity.class))).thenReturn(savedEntity);

    service.persistIfAbsent(
        MATCH_ID, DialogueTriggerType.MOVE, 4, generated("blaze", "Keep watching."));

    var captured = org.mockito.ArgumentCaptor.forClass(DialogueLineEntity.class);
    verify(repository).save(captured.capture());
    assertThat(captured.getValue().personalityDisplayName()).isEqualTo("Blaze");
  }

  private static GeneratedDialogue generated(String key, String text) {
    return new GeneratedDialogue(
        key,
        text,
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        AiResponseSource.REMOTE_PRIMARY);
  }

  private static PersonalityPromptProfile profile(String key, String displayName) {
    return new PersonalityPromptProfile(
        key,
        displayName,
        "Competitive traits",
        new BigDecimal("0.5"),
        "Dry style",
        "PG-13 boundary");
  }

  private static DialogueLineEntity entity(long id, int ply) {
    DialogueLineEntity entity =
        new DialogueLineEntity(
            MATCH_ID,
            DialogueTriggerType.MOVE,
            ply,
            "blaze",
            "Blaze",
            "Line " + id,
            DialogueEmotion.CONFIDENT,
            DialogueReactionType.MOVE_REACTION,
            AiResponseSource.REMOTE_PRIMARY,
            Instant.parse("2026-08-16T00:00:00Z").plusSeconds(id));
    assignId(entity, id);
    return entity;
  }

  private static void assignId(DialogueLineEntity entity, long id) {
    try {
      Field idField = DialogueLineEntity.class.getDeclaredField("id");
      idField.setAccessible(true);
      idField.set(entity, id);
    } catch (ReflectiveOperationException error) {
      throw new AssertionError(error);
    }
  }
}
