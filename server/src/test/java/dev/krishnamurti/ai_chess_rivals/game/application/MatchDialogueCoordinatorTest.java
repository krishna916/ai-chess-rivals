package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.krishnamurti.ai_chess_rivals.ai.api.AiResponseSource;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEmotion;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueGenerator;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryLine;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueHistoryStore;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueReactionType;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueTriggerType;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import dev.krishnamurti.ai_chess_rivals.ai.api.PersistedDialogue;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition;
import dev.krishnamurti.ai_chess_rivals.game.domain.CastlingSide;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.MatchRivalry;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveDetails;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.DialoguePlayed;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

class MatchDialogueCoordinatorTest {

  private static final UUID MATCH_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final MatchRivalry RIVALRY =
      new MatchRivalry("sage", "Sage", "maverick", "Maverick");

  private final DialogueGenerator dialogueGenerator =
      org.mockito.Mockito.mock(DialogueGenerator.class);
  private final DialogueHistoryStore historyStore =
      org.mockito.Mockito.mock(DialogueHistoryStore.class);
  private final MatchEventSink matchEventSink = org.mockito.Mockito.mock(MatchEventSink.class);
  private final MatchDialogueCoordinator coordinator =
      new MatchDialogueCoordinator(dialogueGenerator, historyStore, matchEventSink);

  @BeforeEach
  void defaultHistory() {
    when(historyStore.lastFour(MATCH_ID)).thenReturn(List.of());
  }

  @Test
  void onMovePassesExactlyTheLastFourHistoryAndMapsWhiteSides() {
    List<DialogueHistoryLine> history =
        List.of(new DialogueHistoryLine(3, "blaze", "Blaze", "Heat."));
    when(historyStore.lastFour(MATCH_ID)).thenReturn(history);
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.empty());

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    ArgumentCaptor<dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest> request =
        ArgumentCaptor.forClass(dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest.class);
    verify(dialogueGenerator).generateMove(request.capture());
    assertThat(request.getValue().moverPersonalityKey()).isEqualTo("sage");
    assertThat(request.getValue().opponentPersonalityKey()).isEqualTo("maverick");
    assertThat(request.getValue().recentDialogue()).isSameAs(history);
  }

  @Test
  void onMoveMapsBlackSidesInReverse() {
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.empty());

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.BLACK), () -> true);

    ArgumentCaptor<dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest> request =
        ArgumentCaptor.forClass(dev.krishnamurti.ai_chess_rivals.ai.api.DialogueMoveRequest.class);
    verify(dialogueGenerator).generateMove(request.capture());
    assertThat(request.getValue().moverPersonalityKey()).isEqualTo("maverick");
    assertThat(request.getValue().opponentPersonalityKey()).isEqualTo("sage");
  }

  @Test
  void scopesMatchAndEventCorrelationThroughDialogueAndClearsItAfterward() {
    MDC.put("matchId", "outer-match");
    MDC.put("triggerType", "outer-trigger");
    MDC.put("triggerPly", "outer-ply");
    when(dialogueGenerator.generateMove(any()))
        .thenAnswer(
            invocation -> {
              assertThat(MDC.get("matchId")).isEqualTo(MATCH_ID.toString());
              assertThat(MDC.get("triggerType")).isEqualTo("MOVE");
              assertThat(MDC.get("triggerPly")).isEqualTo("1");
              return Optional.empty();
            });

    try {
      coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

      assertThat(MDC.get("matchId")).isEqualTo("outer-match");
      assertThat(MDC.get("triggerType")).isEqualTo("outer-trigger");
      assertThat(MDC.get("triggerPly")).isEqualTo("outer-ply");
    } finally {
      MDC.clear();
    }
  }

  @Test
  void persistsBeforePublishingNewDialogue() {
    GeneratedDialogue generated = generated(AiResponseSource.GROQ);
    PersistedDialogue saved = persisted(1);
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.of(generated));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, generated))
        .thenReturn(Optional.of(saved));

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    InOrder order = inOrder(historyStore, matchEventSink);
    order.verify(historyStore).persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, generated);
    order.verify(matchEventSink).publish(new DialoguePlayed(saved));
  }

  @Test
  void doesNotPublishWhenPersistenceReportsDuplicate() {
    GeneratedDialogue generated = generated(AiResponseSource.GROQ);
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.of(generated));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, generated))
        .thenReturn(Optional.empty());

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    verify(matchEventSink, never()).publish(any());
  }

  @Test
  void persistsFallbackThroughTheSamePath() {
    GeneratedDialogue fallback = generated(AiResponseSource.DETERMINISTIC_FALLBACK);
    PersistedDialogue saved = persisted(2);
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.of(fallback));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, fallback))
        .thenReturn(Optional.of(saved));

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    verify(historyStore).persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, fallback);
    verify(matchEventSink).publish(new DialoguePlayed(saved));
  }

  @Test
  void generatorFailureDoesNotEscapeOrPublish() {
    when(dialogueGenerator.generateMove(any())).thenThrow(new IllegalStateException("provider"));

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    verify(historyStore, never()).persistIfAbsent(any(), any(), any(Integer.class), any());
    verify(matchEventSink, never()).publish(any());
  }

  @Test
  void generatorFailureLogsOnlySafeExceptionMetadata() {
    Logger logger = (Logger) LoggerFactory.getLogger(MatchDialogueCoordinator.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      when(dialogueGenerator.generateMove(any()))
          .thenThrow(new IllegalStateException("provider response secret"));

      coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    ILoggingEvent event = assertThat(appender.list).singleElement().actual();
    assertThat(event.getFormattedMessage())
        .contains("exceptionType=IllegalStateException")
        .doesNotContain("provider response secret");
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void persistenceFailureDoesNotEscapeOrPublish() {
    GeneratedDialogue generated = generated(AiResponseSource.GROQ);
    when(dialogueGenerator.generateMove(any())).thenReturn(Optional.of(generated));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.MOVE, 1, generated))
        .thenThrow(new IllegalStateException("database"));

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), () -> true);

    verify(matchEventSink, never()).publish(any());
  }

  @Test
  void staleAuthorityDiscardsGeneratedLineBeforePersistence() {
    GeneratedDialogue generated = generated(AiResponseSource.GROQ);
    AtomicBoolean authoritative = new AtomicBoolean(true);
    when(dialogueGenerator.generateMove(any()))
        .thenAnswer(
            invocation -> {
              authoritative.set(false);
              return Optional.of(generated);
            });

    coordinator.onMove(MATCH_ID, RIVALRY, move(PlayerColor.WHITE), authoritative::get);

    verify(historyStore, never()).persistIfAbsent(any(), any(), any(Integer.class), any());
    verify(matchEventSink, never()).publish(any());
  }

  @Test
  void gameStartPublishesOnlyNewlyPersistedSpeaker() {
    GeneratedDialogue white = generated(AiResponseSource.GROQ, "blaze");
    GeneratedDialogue black = generated(AiResponseSource.GEMINI, "vesper");
    PersistedDialogue savedBlack = persisted(4);
    when(dialogueGenerator.generateStart(any())).thenReturn(List.of(white, black));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.GAME_START, 0, white))
        .thenReturn(Optional.empty());
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.GAME_START, 0, black))
        .thenReturn(Optional.of(savedBlack));

    coordinator.onGameStart(MATCH_ID, RIVALRY, () -> true);

    verify(matchEventSink).publish(new DialoguePlayed(savedBlack));
    verify(matchEventSink, org.mockito.Mockito.times(1)).publish(any());
  }

  @Test
  void gameEndUsesResultOutcomesAndPublishesOnlyNewlyPersistedSpeaker() {
    GeneratedDialogue winner = generated(AiResponseSource.GROQ, "blaze");
    GeneratedDialogue loser = generated(AiResponseSource.GROQ, "vesper");
    PersistedDialogue savedLoser = persisted(5);
    when(dialogueGenerator.generateEnd(any())).thenReturn(List.of(winner, loser));
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.GAME_END, 12, winner))
        .thenReturn(Optional.empty());
    when(historyStore.persistIfAbsent(MATCH_ID, DialogueTriggerType.GAME_END, 12, loser))
        .thenReturn(Optional.of(savedLoser));

    coordinator.onGameEnd(
        MATCH_ID,
        RIVALRY,
        dev.krishnamurti.ai_chess_rivals.game.domain.GameResult.WHITE_WINS,
        12,
        () -> true);

    ArgumentCaptor<dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest> request =
        ArgumentCaptor.forClass(dev.krishnamurti.ai_chess_rivals.ai.api.DialogueEndRequest.class);
    verify(dialogueGenerator).generateEnd(request.capture());
    assertThat(request.getValue().whiteOutcome())
        .isEqualTo(dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome.VICTORY);
    assertThat(request.getValue().whitePersonalityKey()).isEqualTo("sage");
    assertThat(request.getValue().blackPersonalityKey()).isEqualTo("maverick");
    assertThat(request.getValue().blackOutcome())
        .isEqualTo(dev.krishnamurti.ai_chess_rivals.ai.api.DialogueOutcome.DEFEAT);
    verify(matchEventSink).publish(new DialoguePlayed(savedLoser));
  }

  @Test
  void historyFailsSoftWhenStorageIsUnavailable() {
    when(historyStore.findAll(MATCH_ID)).thenThrow(new IllegalStateException("database"));

    assertThat(coordinator.history(MATCH_ID)).isEmpty();
  }

  private static MovePlayed move(PlayerColor player) {
    return new MovePlayed(
        1,
        player,
        new MoveNotation("e2e4"),
        BoardPosition.STARTING_POSITION,
        new MoveDetails(
            ChessPieceType.PAWN,
            player,
            "e2",
            "e4",
            null,
            null,
            null,
            (CastlingSide) null,
            false,
            false),
        Optional.<EvaluationSwing>empty());
  }

  private static GeneratedDialogue generated(AiResponseSource source) {
    return generated(source, "blaze");
  }

  private static GeneratedDialogue generated(AiResponseSource source, String personalityKey) {
    return new GeneratedDialogue(
        personalityKey,
        "Line from " + personalityKey,
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        source);
  }

  private static PersistedDialogue persisted(long id) {
    return new PersistedDialogue(
        id,
        MATCH_ID,
        DialogueTriggerType.MOVE,
        1,
        "blaze",
        "Blaze",
        "Persisted line",
        DialogueEmotion.CONFIDENT,
        DialogueReactionType.MOVE_REACTION,
        AiResponseSource.GROQ,
        Instant.parse("2026-08-16T00:00:00Z"));
  }
}
