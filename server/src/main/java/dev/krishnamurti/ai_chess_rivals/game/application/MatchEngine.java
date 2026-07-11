package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.Move;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Runs a single synchronous match from start to finish. */
@Service
public final class MatchEngine {

  private final ChessPlayer chessPlayer;
  private final ChessBoardService chessBoardService;
  private final MatchEventSink matchEventSink;
  private final int maxPlies;
  private final AtomicReference<Match> currentMatch = new AtomicReference<>();
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);

  public MatchEngine(
      @Qualifier("stockfishPlayer") ChessPlayer chessPlayer,
      ChessBoardService chessBoardService,
      GameProperties gameProperties,
      MatchEventSink matchEventSink) {
    this.chessPlayer = Objects.requireNonNull(chessPlayer, "chessPlayer must not be null");
    this.chessBoardService =
        Objects.requireNonNull(chessBoardService, "chessBoardService must not be null");
    Objects.requireNonNull(gameProperties, "gameProperties must not be null");
    this.matchEventSink = Objects.requireNonNull(matchEventSink, "matchEventSink must not be null");
    this.maxPlies = gameProperties.maxPlies();
  }

  public synchronized Match startNewMatch() {
    Match existingMatch = currentMatch.get();
    if (existingMatch != null && existingMatch.isInProgress()) {
      throw new IllegalStateException(
          "Cannot start a new match while another match is in progress");
    }

    stopRequested.set(false);
    Match match = Match.newGame();
    try {
      chessPlayer.startNewGame();
    } catch (RuntimeException e) {
      throw new MatchEngineException("Failed to initialize a new match", e);
    }
    currentMatch.set(match);
    try {
      matchEventSink.publish(new MatchStarted(match.sideToMove(), match.currentPosition()));
    } catch (RuntimeException e) {
      throw new MatchEngineException("Failed to publish match start event", e);
    }
    return match;
  }

  public Match playUntilFinished() {
    Match match = currentMatch.get();
    if (match == null) {
      match = startNewMatch();
    }
    stopRequested.set(false);
    Map<String, Integer> positionOccurrences = buildPositionOccurrences(match);

    while (match.isInProgress() && !stopRequested.get()) {
      if (match.moveCount() >= maxPlies) {
        match = finishMatch(match, GameResult.DRAW);
        break;
      }

      int ply = match.moveCount() + 1;
      try {
        MoveNotation moveNotation = chessPlayer.chooseMove(match);
        PlayerColor player = match.sideToMove();
        AppliedMove appliedMove =
            chessBoardService.applyMove(match.currentPosition(), moveNotation);
        match = match.recordMove(moveNotation, appliedMove.position());
        currentMatch.set(match);
        Move recordedMove = match.moves().getLast();
        matchEventSink.publish(
            new MovePlayed(
                recordedMove.sequenceNumber(),
                player,
                recordedMove.notation(),
                recordedMove.positionAfterMove(),
                appliedMove.capture(),
                appliedMove.check(),
                appliedMove.checkmate(),
                appliedMove.promotion()));
        int currentPositionOccurrences =
            recordPositionOccurrence(positionOccurrences, match.currentPosition());
        GameResult result =
            chessBoardService
                .determineResult(
                    match.currentPosition(), match.sideToMove(), currentPositionOccurrences)
                .orElse(null);
        if (result != null) {
          match = finishMatch(match, result);
        }
      } catch (RuntimeException e) {
        throw new MatchEngineException(
            "Match execution failed while processing ply " + ply, e);
      }
    }

    return currentMatch();
  }

  public Match currentMatch() {
    Match match = currentMatch.get();
    if (match == null) {
      throw new IllegalStateException("No match has been started");
    }
    return match;
  }

  public void stopCurrentMatch() {
    stopRequested.set(true);
  }

  private Match finishMatch(Match match, GameResult result) {
    Match finishedMatch = match.finish(result);
    currentMatch.set(finishedMatch);
    matchEventSink.publish(
        new MatchFinished(result, finishedMatch.currentPosition(), finishedMatch.moveCount()));
    return finishedMatch;
  }

  private Map<String, Integer> buildPositionOccurrences(Match match) {
    Map<String, Integer> positionOccurrences = new HashMap<>();
    recordPositionOccurrence(positionOccurrences, Match.newGame().currentPosition());
    for (dev.krishnamurti.ai_chess_rivals.game.domain.Move move : match.moves()) {
      recordPositionOccurrence(positionOccurrences, move.positionAfterMove());
    }
    return positionOccurrences;
  }

  private int recordPositionOccurrence(
      Map<String, Integer> positionOccurrences,
      dev.krishnamurti.ai_chess_rivals.game.domain.BoardPosition position) {
    return positionOccurrences.merge(
        chessBoardService.normalizedPositionKey(position), 1, Integer::sum);
  }
}
