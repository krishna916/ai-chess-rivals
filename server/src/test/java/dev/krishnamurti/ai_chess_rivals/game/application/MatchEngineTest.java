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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class MatchEngineTest {

  @Test
  void startNewMatchInitializesFreshState() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 300));

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
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 2));

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
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 300));
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
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 2));
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
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 20));

    Match finalMatch = matchEngine.playUntilFinished();

    assertTrue(finalMatch.isFinished());
    assertEquals(GameResult.DRAW, finalMatch.result().orElseThrow());
    assertEquals(8, finalMatch.moveCount());
  }

  @Test
  void startNewMatchRejectsReplacingActiveMatch() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 300));
    matchEngine.startNewMatch();

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::startNewMatch);

    assertEquals("Cannot start a new match while another match is in progress", error.getMessage());
  }

  @Test
  void currentMatchRejectsWhenNoMatchExists() {
    FakeChessPlayer chessPlayer = new FakeChessPlayer();
    MatchEngine matchEngine =
        new MatchEngine(chessPlayer, new ChessBoardService(), new GameProperties(250, 300));

    IllegalStateException error =
        assertThrows(IllegalStateException.class, matchEngine::currentMatch);

    assertEquals("No match has been started", error.getMessage());
  }

  private static final class FakeChessPlayer implements ChessPlayer {

    private final Deque<MoveNotation> moves = new ArrayDeque<>();
    private final List<Match> chooseMoveMatches = new ArrayList<>();
    private int startNewGameCalls;
    private Runnable onChooseMove;

    private FakeChessPlayer(String... moves) {
      for (String move : moves) {
        this.moves.addLast(new MoveNotation(move));
      }
    }

    @Override
    public void startNewGame() {
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
}
