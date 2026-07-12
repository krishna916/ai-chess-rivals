package dev.krishnamurti.ai_chess_rivals.game.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
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
import org.junit.jupiter.api.Test;

class MatchEngineTest {

  private static GameProperties gameProperties(int moveThinkTimeMillis, int maxPlies) {
    return new GameProperties(
        moveThinkTimeMillis,
        maxPlies,
        new GameProperties.MoveDelay(Duration.ZERO, Duration.ZERO));
  }

  @Test
  void startNewMatchInitializesFreshState() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), event -> {});

    Match match = matchEngine.startNewMatch();

    assertEquals(1, chessPlayer.startNewGameCalls);
    assertTrue(match.isInProgress());
    assertEquals(0, match.moveCount());
    assertEquals(match, matchEngine.currentMatch());
  }

  @Test
  void playUntilFinishedRecordsMovesAndStopsAtMaxPliesFallback() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 2), event -> {});

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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), event -> {});
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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 2), event -> {});
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
  void playUntilFinishedReturnsDrawOnThreefoldRepetition() {
    FakeChessPlayer chessPlayer =
        new FakeChessPlayer("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8");
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 20), event -> {});

    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(GameResult.DRAW, finalMatch.result().orElseThrow());
    assertEquals(8, finalMatch.moveCount());
  }

  @Test
  void startNewMatchRejectsReplacingActiveMatch() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), event -> {});
    matchEngine.startNewMatch();

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::startNewMatch);

    assertEquals("Cannot start a new match while another match is in progress", error.getMessage());
  }

  @Test
  void currentMatchRejectsWhenNoMatchExists() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), event -> {});

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::currentMatch);

    assertEquals("No match has been started", error.getMessage());
  }

  @Test
  void startNewMatchEmitsMatchStartedAfterSuccessfulInitialization() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), eventSink);

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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), eventSink);

    assertThrows(MatchEngineException.class, matchEngine::startNewMatch);

    assertTrue(eventSink.events.isEmpty());
  }

  @Test
  void playUntilFinishedEmitsMovePlayedWithMovingSideAndPostMovePosition() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 1), eventSink);

    Match finalMatch = matchEngine.playUntilFinished();

    MovePlayed movePlayed = (MovePlayed) eventSink.events.get(1);
    assertEquals(1, movePlayed.ply());
    assertEquals(PlayerColor.WHITE, movePlayed.player());
    assertEquals("e2e4", movePlayed.notation().value());
    assertEquals(finalMatch.moves().getFirst().positionAfterMove(), movePlayed.position());
    assertFalse(movePlayed.capture());
    assertFalse(movePlayed.check());
    assertFalse(movePlayed.checkmate());
    assertFalse(movePlayed.promotion());
  }

  @Test
  void playUntilFinishedEmitsOrderedLifecycleEvents() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 2), eventSink);

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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 2), eventSink);

    matchEngine.playUntilFinished();

    long finishEvents = eventSink.events.stream().filter(MatchFinished.class::isInstance).count();
    assertEquals(1, finishEvents);
  }

  @Test
  void stopCurrentMatchEmitsNoMatchFinished() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), eventSink);
    chessPlayer.onChooseMove = () -> matchEngine.stopCurrentMatch();

    matchEngine.playUntilFinished();

    assertTrue(eventSink.events.stream().noneMatch(MatchFinished.class::isInstance));
  }

  @Test
  void resumingStoppedMatchDoesNotEmitAnotherMatchStarted() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4", "e7e5");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 2), eventSink);
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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 10), eventSink);

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
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 300), eventSink);

    MatchEngineException error =
        assertThrows(MatchEngineException.class, matchEngine::startNewMatch);

    assertEquals("Failed to publish match start event", error.getMessage());
    assertThrows(IllegalStateException.class, matchEngine::currentMatch);
  }

  @Test
  void playUntilFinishedWrapsMoveSinkFailureWithPlyContext() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 10), eventSink);
    matchEngine.startNewMatch();
    eventSink.failure = new IllegalStateException("sink failed");

    MatchEngineException error =
        assertThrows(MatchEngineException.class, matchEngine::playUntilFinished);

    assertEquals("Match execution failed while processing ply 1", error.getMessage());
    assertEquals(0, matchEngine.currentMatch().moveCount());
    assertTrue(matchEngine.currentMatch().isInProgress());
  }

  @Test
  void playUntilFinishedLeavesMatchUnfinishedWhenMatchFinishedSinkFails() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer("e2e4");
    RecordingMatchEventSink eventSink = new RecordingMatchEventSink();
    MatchEngine matchEngine =
        new MatchEngine(
            chessPlayer, new ChessBoardService(), gameProperties(250, 1), eventSink);
    matchEngine.startNewMatch();
    eventSink.failOnPublishNumber = 3;

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::playUntilFinished);

    assertEquals("sink failed", error.getMessage());
    assertEquals(1, matchEngine.currentMatch().moveCount());
    assertTrue(matchEngine.currentMatch().isInProgress());
  }

  private static final class FakeChessPlayer implements ChessPlayer {

    private final Deque<MoveNotation> moves = new ArrayDeque<>();
    private final List<Match> chooseMoveMatches = new ArrayList<>();
    private int startNewGameCalls;
    private RuntimeException startNewGameFailure;
    private Runnable onChooseMove;

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

  private static final class RecordingMatchEventSink implements MatchEventSink {

    private final List<MatchEvent> events = new ArrayList<>();
    private RuntimeException failure;
    private Integer failOnPublishNumber;

    @Override
    public void publish(MatchEvent event) {
      if (failOnPublishNumber != null && events.size() + 1 == failOnPublishNumber) {
        throw new IllegalStateException("sink failed");
      }
      if (failure != null) {
        throw failure;
      }
      events.add(event);
    }
  }
}
