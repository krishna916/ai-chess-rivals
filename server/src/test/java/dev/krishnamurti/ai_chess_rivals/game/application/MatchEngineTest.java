package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationService;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwingClassification;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.ChessPieceType;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameStatus;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEvent;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class MatchEngineTest {

  private static final MatchPacing NO_OP_PACING = () -> {};

  private static GameProperties gameProperties(int moveThinkTimeMillis, int maxPlies) {
    return new GameProperties(
        moveThinkTimeMillis, maxPlies, new GameProperties.MoveDelay(Duration.ZERO, Duration.ZERO));
  }

  private static MatchEngine matchEngine(
      FakeChessPlayer chessPlayer, int moveThinkTimeMillis, int maxPlies) {
    return matchEngine(chessPlayer, moveThinkTimeMillis, maxPlies, NO_OP_PACING, event -> {});
  }

  private static MatchEngine matchEngine(
      FakeChessPlayer chessPlayer,
      int moveThinkTimeMillis,
      int maxPlies,
      MatchEventSink eventSink) {
    return matchEngine(chessPlayer, moveThinkTimeMillis, maxPlies, NO_OP_PACING, eventSink);
  }

  private static MatchEngine matchEngine(
      FakeChessPlayer chessPlayer,
      int moveThinkTimeMillis,
      int maxPlies,
      MatchPacing matchPacing,
      MatchEventSink eventSink) {
    return matchEngine(
        chessPlayer,
        moveThinkTimeMillis,
        maxPlies,
        matchPacing,
        eventSink,
        new FakeChessEvaluationService(),
        mock(MatchDialogueCoordinator.class));
  }

  private static MatchEngine matchEngine(
      FakeChessPlayer chessPlayer,
      int moveThinkTimeMillis,
      int maxPlies,
      MatchPacing matchPacing,
      MatchEventSink eventSink,
      FakeChessEvaluationService evaluationService) {
    return matchEngine(
        chessPlayer,
        moveThinkTimeMillis,
        maxPlies,
        matchPacing,
        eventSink,
        evaluationService,
        mock(MatchDialogueCoordinator.class));
  }

  private static MatchEngine matchEngine(
      FakeChessPlayer chessPlayer,
      int moveThinkTimeMillis,
      int maxPlies,
      MatchPacing matchPacing,
      MatchEventSink eventSink,
      FakeChessEvaluationService evaluationService,
      MatchDialogueCoordinator matchDialogueCoordinator) {
    return new MatchEngine(
        chessPlayer,
        new ChessBoardService(),
        gameProperties(moveThinkTimeMillis, maxPlies),
        matchPacing,
        eventSink,
        evaluationService,
        matchDialogueCoordinator);
  }

  @Test
  void runsMoveDialogueAfterMoveBroadcastAndBeforePacing() throws InterruptedException {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchEventSink eventSink = mock(MatchEventSink.class);
    MatchPacing pacing = mock(MatchPacing.class);
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    MatchEngine[] engine = new MatchEngine[1];
    List<MatchEvent> events = new ArrayList<>();
    doAnswer(
            invocation -> {
              events.add(invocation.getArgument(0));
              return null;
            })
        .when(eventSink)
        .publish(any());
    doAnswer(
            invocation -> {
              engine[0].stopCurrentMatch();
              return null;
            })
        .when(pacing)
        .waitBeforeNextMove();
    engine[0] =
        matchEngine(
            chessPlayer, 250, 2, pacing, eventSink, new FakeChessEvaluationService(), coordinator);

    Match finalMatch = engine[0].playUntilFinished();

    MovePlayed movePlayed =
        events.stream()
            .filter(MovePlayed.class::isInstance)
            .map(MovePlayed.class::cast)
            .findFirst()
            .orElseThrow();
    InOrder order = inOrder(eventSink, coordinator, pacing);
    order.verify(eventSink).publish(movePlayed);
    order.verify(coordinator).onMove(eq(finalMatch.id()), eq(movePlayed), any());
    order.verify(pacing).waitBeforeNextMove();
  }

  @Test
  void stopInvalidatesAuthorityCapturedByRunningDialogue() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    MatchEngine[] engine = new MatchEngine[1];
    AtomicReference<BooleanSupplier> authority = new AtomicReference<>();
    doAnswer(
            invocation -> {
              BooleanSupplier captured = invocation.getArgument(2);
              authority.set(captured);
              assertTrue(captured.getAsBoolean());
              engine[0].stopCurrentMatch();
              assertFalse(captured.getAsBoolean());
              return null;
            })
        .when(coordinator)
        .onMove(any(), any(), any());
    engine[0] =
        matchEngine(
            chessPlayer,
            250,
            2,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);

    engine[0].playUntilFinished();

    assertFalse(authority.get().getAsBoolean());
  }

  @Test
  void resumeGetsANewExecutionGeneration() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    MatchEngine[] engine = new MatchEngine[1];
    AtomicReference<BooleanSupplier> oldAuthority = new AtomicReference<>();
    AtomicReference<BooleanSupplier> newAuthority = new AtomicReference<>();
    doAnswer(
            invocation -> {
              BooleanSupplier captured = invocation.getArgument(2);
              if (oldAuthority.get() == null) {
                oldAuthority.set(captured);
                engine[0].stopCurrentMatch();
              } else {
                newAuthority.set(captured);
              }
              return null;
            })
        .when(coordinator)
        .onMove(any(), any(), any());
    engine[0] =
        matchEngine(
            chessPlayer,
            250,
            2,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);

    engine[0].playUntilFinished();
    engine[0].playUntilFinished();

    assertFalse(oldAuthority.get().getAsBoolean());
    assertTrue(newAuthority.get().getAsBoolean());
  }

  @Test
  void resumeWaitsForStoppedExecutionToLeaveDialogueBeforeStartingAnotherLoop() throws Exception {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5", "c7c5");
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    CountDownLatch firstDialogueEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstDialogue = new CountDownLatch(1);
    AtomicInteger moveDialogueCalls = new AtomicInteger();

    doAnswer(
            invocation -> {
              if (moveDialogueCalls.incrementAndGet() == 1) {
                firstDialogueEntered.countDown();
                assertTrue(releaseFirstDialogue.await(5, TimeUnit.SECONDS));
              }
              return null;
            })
        .when(coordinator)
        .onMove(any(), any(), any());

    MatchEngine engine =
        matchEngine(
            chessPlayer,
            250,
            2,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<Match> stoppedExecution = executor.submit(engine::playUntilFinished);
      assertTrue(firstDialogueEntered.await(5, TimeUnit.SECONDS));

      engine.stopCurrentMatch();

      CountDownLatch resumeTaskStarted = new CountDownLatch(1);
      Future<Match> resumedExecution =
          executor.submit(
              () -> {
                resumeTaskStarted.countDown();
                return engine.playUntilFinished();
              });
      assertTrue(resumeTaskStarted.await(5, TimeUnit.SECONDS));

      assertThrows(TimeoutException.class, () -> resumedExecution.get(500, TimeUnit.MILLISECONDS));

      releaseFirstDialogue.countDown();

      stoppedExecution.get(5, TimeUnit.SECONDS);
      Match finished = resumedExecution.get(5, TimeUnit.SECONDS);

      assertTrue(finished.isFinished());
      assertEquals(2, finished.moveCount());
      assertEquals("e2e4", finished.moves().get(0).notation().value());
      assertEquals("e7e5", finished.moves().get(1).notation().value());
      assertEquals(2, chessPlayer.chooseMoveMatches.size());
      assertEquals(finished, engine.currentMatch());
    } finally {
      releaseFirstDialogue.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void gameStartDialogueRunsBeforeTheFirstMove() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    List<String> order = new ArrayList<>();
    chessPlayer.operationOrder = order;
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    doAnswer(
            invocation -> {
              order.add("start");
              return null;
            })
        .when(coordinator)
        .onGameStart(any(), any());
    MatchEngine engine =
        matchEngine(
            chessPlayer,
            250,
            1,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);

    engine.playUntilFinished();

    assertEquals(List.of("start", "choose"), order);
  }

  @Test
  void terminalResultRunsEndDialogueBeforeMatchFinished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("f2f3", "e7e5", "g2g4", "d8h4");
    MatchEventSink eventSink = mock(MatchEventSink.class);
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    MatchEngine engine =
        matchEngine(
            chessPlayer,
            250,
            10,
            NO_OP_PACING,
            eventSink,
            new FakeChessEvaluationService(),
            coordinator);

    engine.playUntilFinished();

    InOrder order = inOrder(coordinator, eventSink);
    order.verify(coordinator).onGameEnd(any(), eq(GameResult.BLACK_WINS), eq(4), any());
    order.verify(eventSink).publish(any(MatchFinished.class));
  }

  @Test
  void maxPliesDrawRunsEndDialogue() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    MatchEngine engine =
        matchEngine(
            chessPlayer,
            250,
            1,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);

    engine.playUntilFinished();

    verify(coordinator).onGameEnd(any(), eq(GameResult.DRAW), eq(1), any());
  }

  @Test
  void dialogueFailureDoesNotPreventMatchCompletion() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    MatchDialogueCoordinator coordinator = mock(MatchDialogueCoordinator.class);
    doThrow(new IllegalStateException("dialogue failed"))
        .when(coordinator)
        .onMove(any(), any(), any());
    MatchEngine engine =
        matchEngine(
            chessPlayer,
            250,
            1,
            NO_OP_PACING,
            event -> {},
            new FakeChessEvaluationService(),
            coordinator);

    Match finalMatch = engine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
  }

  @Test
  void startNewMatchInitializesFreshState() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300);

    Match match = matchEngine.startNewMatch();

    assertEquals(1, chessPlayer.startNewGameCalls);
    assertTrue(match.isInProgress());
    assertEquals(0, match.moveCount());
    assertEquals(match, matchEngine.currentMatch());
  }

  @Test
  void playUntilFinishedRecordsMovesAndStopsAtMaxPliesFallback() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 2);

    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(GameStatus.FINISHED, finalMatch.status());
    assertEquals(GameResult.DRAW, finalMatch.result().orElseThrow());
    assertEquals(2, finalMatch.moveCount());
    assertEquals("e2e4", finalMatch.moves().get(0).notation().value());
    assertEquals("e7e5", finalMatch.moves().get(1).notation().value());
    assertEquals(2, chessPlayer.chooseMoveMatches.size());
    assertEquals(
        List.of(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
        chessPlayer.chooseMoveMatches.stream()
            .map(match -> match.currentPosition().fen())
            .toList());
    assertEquals(
        List.of(PlayerColor.WHITE, PlayerColor.BLACK),
        chessPlayer.chooseMoveMatches.stream().map(Match::sideToMove).toList());
  }

  @Test
  void playUntilFinishedStopsAfterCurrentIterationWhenStopIsRequested() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300);
    chessPlayer.onChooseMove = () -> matchEngine.stopCurrentMatch();

    Match match = matchEngine.playUntilFinished();

    assertTrue(match.isInProgress());
    assertFalse(match.isFinished());
    assertEquals(1, match.moveCount());
    assertEquals("e2e4", match.moves().getFirst().notation().value());
  }

  @Test
  void playUntilFinishedResumesStoppedMatch() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 2);
    chessPlayer.onChooseMove = () -> matchEngine.stopCurrentMatch();

    Match stoppedMatch = matchEngine.playUntilFinished();
    Match resumedMatch = matchEngine.playUntilFinished();

    assertTrue(stoppedMatch.isInProgress());
    assertEquals(1, stoppedMatch.moveCount());
    assertTrue(resumedMatch.isFinished());
    assertEquals(GameResult.DRAW, resumedMatch.result().orElseThrow());
    assertEquals(2, resumedMatch.moveCount());
    assertEquals("e7e5", resumedMatch.moves().get(1).notation().value());
  }

  @Test
  void playUntilFinishedResumesWithoutDuplicatingEvaluationBaseline() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService().withEvaluations(cp(0), cp(-20), cp(-10));
    MatchEngine matchEngine =
        matchEngine(chessPlayer, 250, 2, NO_OP_PACING, eventSink, evaluationService);
    chessPlayer.onChooseMove = matchEngine::stopCurrentMatch;

    matchEngine.playUntilFinished();
    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(
        1, eventSink.events.stream().filter(event -> event instanceof MatchStarted).count());
    List<MovePlayed> moveEvents =
        eventSink.events.stream()
            .filter(MovePlayed.class::isInstance)
            .map(MovePlayed.class::cast)
            .toList();
    assertEquals(List.of(1, 2), moveEvents.stream().map(MovePlayed::ply).toList());
    assertEquals(3, evaluationService.fens.size());
    assertEquals(
        List.of(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
            "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2"),
        evaluationService.fens);
    EvaluationSwing secondSwing = moveEvents.get(1).evaluation().orElseThrow();
    assertEquals(-20, secondSwing.beforeCentipawns());
    assertEquals(10, secondSwing.afterCentipawns());
    assertEquals(30, secondSwing.swingCentipawns());
  }

  @Test
  void stopDuringPostMoveEvaluationKeepsCommittedPlyAndResumeBaselineConsistent() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService().withEvaluations(cp(0), cp(-20), cp(-10));
    MatchEngine[] engine = new MatchEngine[1];
    int[] evaluationCalls = new int[1];
    evaluationService.onEvaluate(
        () -> {
          evaluationCalls[0]++;
          if (evaluationCalls[0] == 2) {
            engine[0].stopCurrentMatch();
          }
        });
    engine[0] = matchEngine(chessPlayer, 250, 2, NO_OP_PACING, eventSink, evaluationService);

    Match stoppedMatch = engine[0].playUntilFinished();
    Match resumedMatch = engine[0].playUntilFinished();

    assertTrue(stoppedMatch.isInProgress());
    assertEquals(1, stoppedMatch.moveCount());
    assertTrue(resumedMatch.isFinished());
    assertEquals(2, resumedMatch.moveCount());
    assertEquals(
        List.of(1, 2),
        eventSink.events.stream()
            .filter(MovePlayed.class::isInstance)
            .map(MovePlayed.class::cast)
            .map(MovePlayed::ply)
            .toList());
    assertEquals(3, evaluationService.fens.size());
    EvaluationSwing secondSwing =
        eventSink.events.stream()
            .filter(MovePlayed.class::isInstance)
            .map(MovePlayed.class::cast)
            .toList()
            .get(1)
            .evaluation()
            .orElseThrow();
    assertEquals(-20, secondSwing.beforeCentipawns());
    assertEquals(10, secondSwing.afterCentipawns());
    assertEquals(30, secondSwing.swingCentipawns());
  }

  @Test
  void playUntilFinishedReturnsDrawOnThreefoldRepetition() {
    FakeChessPlayer chessPlayer =
        new FakeChessPlayer("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 20);

    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(GameResult.DRAW, finalMatch.result().orElseThrow());
    assertEquals(8, finalMatch.moveCount());
  }

  @Test
  void startNewMatchRejectsReplacingActiveMatch() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300);
    matchEngine.startNewMatch();

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::startNewMatch);

    assertEquals("Cannot start a new match while another match is in progress", error.getMessage());
  }

  @Test
  void currentMatchRejectsWhenNoMatchExists() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300);

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::currentMatch);

    assertEquals("No match has been started", error.getMessage());
  }

  @Test
  void startNewMatchEmitsMatchStartedAfterSuccessfulInitialization() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300, eventSink);

    Match match = matchEngine.startNewMatch();

    assertEquals(1, eventSink.events.size());
    MatchStarted event = (MatchStarted) eventSink.events.getFirst();
    assertEquals(match.sideToMove(), event.sideToMove());
    assertEquals(match.currentPosition(), event.position());
  }

  @Test
  void startNewMatchEmitsNoEventWhenPlayerInitializationFails() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    chessPlayer.startNewGameFailure = new IllegalStateException("boom");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300, eventSink);

    assertThrows(MatchEngineException.class, matchEngine::startNewMatch);

    assertTrue(eventSink.events.isEmpty());
  }

  @Test
  void playUntilFinishedEmitsMovePlayedWithMovingSideAndPostMovePosition() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 1, eventSink);

    Match finalMatch = matchEngine.playUntilFinished();

    MovePlayed movePlayed = (MovePlayed) eventSink.events.get(1);
    assertEquals(1, movePlayed.ply());
    assertEquals(PlayerColor.WHITE, movePlayed.player());
    assertEquals("e2e4", movePlayed.notation().value());
    assertEquals(finalMatch.moves().getFirst().positionAfterMove(), movePlayed.position());
    assertEquals(ChessPieceType.PAWN, movePlayed.movingPiece());
    assertEquals(PlayerColor.WHITE, movePlayed.movingPieceColor());
    assertEquals("e2", movePlayed.sourceSquare());
    assertEquals("e4", movePlayed.destinationSquare());
    assertEquals(finalMatch.moves().getFirst().details(), movePlayed.details());
    assertFalse(movePlayed.capture());
    assertFalse(movePlayed.check());
    assertFalse(movePlayed.checkmate());
    assertFalse(movePlayed.promotion());
    assertTrue(movePlayed.evaluation().isPresent());
  }

  @Test
  void movePlayedCarriesMoverPerspectiveEvaluationSwing() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService().withEvaluations(cp(10), cp(-250));
    MatchEngine matchEngine =
        matchEngine(chessPlayer, 250, 1, NO_OP_PACING, eventSink, evaluationService);

    matchEngine.playUntilFinished();

    MovePlayed event = (MovePlayed) eventSink.events.get(1);
    EvaluationSwing swing = event.evaluation().orElseThrow();
    assertEquals(10L, swing.beforeCentipawns());
    assertEquals(250L, swing.afterCentipawns());
    assertEquals(240L, swing.swingCentipawns());
    assertEquals(EvaluationSwingClassification.MAJOR_GAIN, swing.classification());
    assertEquals(2, evaluationService.fens.size());
    assertEquals(
        List.of(
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
            "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"),
        evaluationService.fens);
  }

  @Test
  void evaluationFailureDoesNotInvalidateCommittedMove() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService()
            .failsForFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
            .withEvaluations(cp(-250));
    MatchEngine matchEngine =
        matchEngine(chessPlayer, 250, 1, NO_OP_PACING, eventSink, evaluationService);

    Match finalMatch = matchEngine.playUntilFinished();

    assertEquals(1, finalMatch.moveCount());
    MovePlayed event = (MovePlayed) eventSink.events.get(1);
    assertTrue(event.evaluation().isEmpty());
  }

  @Test
  void postMoveEvaluationFailureDoesNotInvalidateCommittedMove() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService().withEvaluations(cp(10)).failsOnCall(2);
    MatchEngine matchEngine =
        matchEngine(chessPlayer, 250, 1, NO_OP_PACING, eventSink, evaluationService);

    Match finalMatch = matchEngine.playUntilFinished();

    assertEquals(1, finalMatch.moveCount());
    MovePlayed event = (MovePlayed) eventSink.events.get(1);
    assertTrue(event.evaluation().isEmpty());
  }

  @Test
  void reusesCommittedEvaluationBaselineForNextPly() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    FakeChessEvaluationService evaluationService =
        new FakeChessEvaluationService().withEvaluations(cp(0), cp(-20), cp(-10));
    MatchEngine matchEngine =
        matchEngine(chessPlayer, 250, 2, NO_OP_PACING, event -> {}, evaluationService);

    matchEngine.playUntilFinished();

    assertEquals(3, evaluationService.fens.size());
  }

  @Test
  void currentMatchContainsAppliedMoveWhenMovePlayedIsPublished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    MatchEngine[] engine = new MatchEngine[1];
    List<Integer> observedMoveCounts = new ArrayList<>();
    MatchEventSink eventSink =
        event -> {
          if (event instanceof MovePlayed) {
            observedMoveCounts.add(engine[0].currentMatch().moveCount());
          }
        };
    engine[0] = matchEngine(chessPlayer, 250, 1, eventSink);

    engine[0].playUntilFinished();

    assertEquals(List.of(1), observedMoveCounts);
  }

  @Test
  void playUntilFinishedEmitsOrderedLifecycleEvents() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 2, eventSink);

    Match finalMatch = matchEngine.playUntilFinished();

    assertEquals(MatchStarted.class, eventSink.events.get(0).getClass());
    assertEquals(MovePlayed.class, eventSink.events.get(1).getClass());
    assertEquals(MovePlayed.class, eventSink.events.get(2).getClass());
    MatchFinished finished = (MatchFinished) eventSink.events.get(3);
    assertEquals(GameResult.DRAW, finished.result());
    assertEquals(finalMatch.currentPosition(), finished.finalPosition());
    assertEquals(2, finished.totalPlies());
  }

  @Test
  void completedMatchEmitsExactlyOneMatchFinished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 2, eventSink);

    matchEngine.playUntilFinished();

    long finishEvents = eventSink.events.stream().filter(MatchFinished.class::isInstance).count();
    assertEquals(1, finishEvents);
  }

  @Test
  void stopCurrentMatchEmitsStoppedButNoMatchFinished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300, eventSink);
    chessPlayer.onChooseMove = () -> matchEngine.stopCurrentMatch();

    matchEngine.playUntilFinished();

    assertTrue(eventSink.events.stream().noneMatch(MatchFinished.class::isInstance));
    assertEquals(
        1,
        eventSink.events.stream()
            .filter(dev.krishnamurti.ai_chess_rivals.game.event.MatchStopped.class::isInstance)
            .count());
  }

  @Test
  void resumingStoppedMatchDoesNotEmitAnotherMatchStarted() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 2, eventSink);
    chessPlayer.onChooseMove = () -> matchEngine.stopCurrentMatch();

    matchEngine.playUntilFinished();
    matchEngine.playUntilFinished();

    long startEvents = eventSink.events.stream().filter(MatchStarted.class::isInstance).count();
    assertEquals(1, startEvents);
  }

  @Test
  void finalMovePlayedCarriesCheckmateMetadataBeforeMatchFinished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("f2f3", "e7e5", "g2g4", "d8h4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 10, eventSink);

    matchEngine.playUntilFinished();

    MovePlayed movePlayed = (MovePlayed) eventSink.events.get(4);
    assertTrue(movePlayed.check());
    assertTrue(movePlayed.checkmate());
    assertEquals(MatchFinished.class, eventSink.events.get(5).getClass());
  }

  @Test
  void startNewMatchWrapsMatchStartedSinkFailure() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    eventSink.failure = new IllegalStateException("sink failed");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 300, eventSink);

    MatchEngineException error =
        assertThrows(MatchEngineException.class, matchEngine::startNewMatch);

    assertEquals("Failed to publish match start event", error.getMessage());
    assertThrows(IllegalStateException.class, matchEngine::currentMatch);
  }

  @Test
  void playUntilFinishedWrapsMoveSinkFailureWithPlyContext() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 10, eventSink);
    matchEngine.startNewMatch();
    eventSink.failure = new IllegalStateException("sink failed");

    MatchEngineException error =
        assertThrows(MatchEngineException.class, matchEngine::playUntilFinished);

    assertEquals("Match execution failed while processing ply 1", error.getMessage());
    assertEquals(1, matchEngine.currentMatch().moveCount());
    assertTrue(matchEngine.currentMatch().isInProgress());
  }

  @Test
  void playUntilFinishedLeavesMatchUnfinishedWhenMatchFinishedSinkFails() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 1, eventSink);
    matchEngine.startNewMatch();
    eventSink.failOnPublishNumber = 3;

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::playUntilFinished);

    assertEquals("sink failed", error.getMessage());
    assertEquals(1, matchEngine.currentMatch().moveCount());
    assertFalse(matchEngine.currentMatch().isInProgress());
    assertTrue(matchEngine.currentMatch().isFinished());
  }

  @Test
  void waitsAfterPublishingANonTerminalMoveBeforeRequestingTheNextMove() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    List<String> order = new ArrayList<>();
    chessPlayer.operationOrder = order;
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    eventSink.operationOrder = order;
    RecordingMatchPacing pacing = new RecordingMatchPacing();
    pacing.operationOrder = order;
    MatchEngine[] matchEngine = new MatchEngine[1];
    pacing.onWait = () -> matchEngine[0].stopCurrentMatch();
    matchEngine[0] = matchEngine(chessPlayer, 250, 2, pacing, eventSink);

    Match stoppedMatch = matchEngine[0].playUntilFinished();

    assertTrue(stoppedMatch.isInProgress());
    assertEquals(List.of("choose", "move", "pacing"), order);
    assertEquals(1, chessPlayer.chooseMoveMatches.size());
  }

  @Test
  void doesNotPaceAfterATerminalMove() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("f2f3", "e7e5", "g2g4", "d8h4");
    List<String> order = new ArrayList<>();
    chessPlayer.operationOrder = order;
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    eventSink.operationOrder = order;
    RecordingMatchPacing pacing = new RecordingMatchPacing();
    pacing.operationOrder = order;
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 10, pacing, eventSink);

    matchEngine.playUntilFinished();

    assertEquals(
        List.of("choose", "move", "finished"), order.subList(order.size() - 3, order.size()));
    assertEquals(3, pacing.calls);
  }

  @Test
  void playUntilFinishedWrapsUnexpectedPacingInterruptionAndPreservesTheInterruptFlag() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchPacing pacing = new RecordingMatchPacing();
    pacing.failure = new InterruptedException("boom");
    MatchEngine matchEngine = matchEngine(chessPlayer, 250, 10, pacing, event -> {});

    try {
      MatchEngineException error =
          assertThrows(MatchEngineException.class, matchEngine::playUntilFinished);

      assertEquals(
          "Match execution was interrupted unexpectedly while processing ply 1",
          error.getMessage());
      assertTrue(error.getCause() instanceof InterruptedException);
      assertTrue(Thread.currentThread().isInterrupted());
      assertEquals(1, matchEngine.currentMatch().moveCount());
      assertTrue(matchEngine.currentMatch().isInProgress());
    } finally {
      Thread.interrupted();
    }
  }

  private static final class FakeChessPlayer implements ChessPlayer {

    private final Deque<MoveNotation> moves = new ArrayDeque<>();
    private final List<Match> chooseMoveMatches = new ArrayList<>();
    private int startNewGameCalls;
    private RuntimeException startNewGameFailure;
    private Runnable onChooseMove;
    private List<String> operationOrder;

    private FakeChessPlayer(String... moves) {
      for (String move : moves) {
        this.moves.addLast(new MoveNotation(move));
      }
    }

    @Override
    public void startNewGame() {
      if (startNewGameFailure != null) {
        throw startNewGameFailure;
      }
      startNewGameCalls++;
    }

    @Override
    public MoveNotation chooseMove(Match match) {
      chooseMoveMatches.add(match);
      if (operationOrder != null) {
        operationOrder.add("choose");
      }
      if (onChooseMove != null) {
        Runnable callback = onChooseMove;
        onChooseMove = null;
        callback.run();
      }
      MoveNotation move = moves.pollFirst();
      if (move == null) {
        throw new IllegalStateException("No fake move configured");
      }
      return move;
    }
  }

  private static final class FakeChessEvaluationService implements ChessEvaluationService {

    private final Deque<PositionEvaluation> evaluations = new ArrayDeque<>();
    private final List<String> fens = new ArrayList<>();
    private int failureOnCall = -1;
    private String failureFen;
    private Runnable onEvaluate = () -> {};

    private FakeChessEvaluationService withEvaluations(PositionEvaluation... values) {
      evaluations.addAll(List.of(values));
      return this;
    }

    private FakeChessEvaluationService failsOnCall(int call) {
      failureOnCall = call;
      return this;
    }

    private FakeChessEvaluationService failsForFen(String fen) {
      failureFen = fen;
      return this;
    }

    private FakeChessEvaluationService onEvaluate(Runnable callback) {
      onEvaluate = callback;
      return this;
    }

    @Override
    public PositionEvaluation evaluate(String fen) {
      fens.add(fen);
      onEvaluate.run();
      if (fens.size() == failureOnCall || fen.equals(failureFen)) {
        throw new IllegalStateException("evaluation failed");
      }
      return evaluations.isEmpty() ? cp(0) : evaluations.removeFirst();
    }

    @Override
    public EvaluationSwing compare(PositionEvaluation before, PositionEvaluation after) {
      long beforeCentipawns = before.comparableCentipawns();
      long afterCentipawns = -after.comparableCentipawns();
      long swing = afterCentipawns - beforeCentipawns;
      EvaluationSwingClassification classification =
          swing >= 200
              ? EvaluationSwingClassification.MAJOR_GAIN
              : swing <= -200
                  ? EvaluationSwingClassification.MAJOR_MISTAKE
                  : EvaluationSwingClassification.STABLE;
      return new EvaluationSwing(beforeCentipawns, afterCentipawns, swing, classification);
    }
  }

  private static PositionEvaluation cp(int value) {
    return new PositionEvaluation(PositionEvaluation.ScoreType.CENTIPAWNS, value);
  }

  private static final class RecordingMatchEventSink implements MatchEventSink {

    private final List<MatchEvent> events = new ArrayList<>();
    private RuntimeException failure;
    private Integer failOnPublishNumber;
    private List<String> operationOrder;

    @Override
    public void publish(MatchEvent event) {
      if (failOnPublishNumber != null && events.size() + 1 == failOnPublishNumber) {
        throw new IllegalStateException("sink failed");
      }
      if (failure != null) {
        throw failure;
      }
      if (operationOrder != null) {
        if (event instanceof MovePlayed) {
          operationOrder.add("move");
        } else if (event instanceof MatchFinished) {
          operationOrder.add("finished");
        }
      }
      events.add(event);
    }
  }

  private static final class RecordingMatchPacing implements MatchPacing {

    private int calls;
    private InterruptedException failure;
    private Runnable onWait;
    private List<String> operationOrder;

    @Override
    public void waitBeforeNextMove() throws InterruptedException {
      calls++;
      if (operationOrder != null) {
        operationOrder.add("pacing");
      }
      if (onWait != null) {
        onWait.run();
      }
      if (failure != null) {
        throw failure;
      }
    }
  }
}
