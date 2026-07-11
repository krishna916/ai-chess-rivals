package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/** Runs a single synchronous Stockfish-vs-Stockfish match from start to finish. */
@Service
public final class MatchEngine {

  private final StockfishClient stockfishClient;
  private final ChessBoardService chessBoardService;
  private final Duration thinkTime;
  private final int maxPlies;
  private final AtomicReference<Match> currentMatch = new AtomicReference<>();
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);

  public MatchEngine(
      StockfishClient stockfishClient,
      ChessBoardService chessBoardService,
      GameProperties gameProperties) {
    this.stockfishClient =
        Objects.requireNonNull(stockfishClient, "stockfishClient must not be null");
    this.chessBoardService =
        Objects.requireNonNull(chessBoardService, "chessBoardService must not be null");
    Objects.requireNonNull(gameProperties, "gameProperties must not be null");
    this.thinkTime = Duration.ofMillis(gameProperties.moveThinkTimeMillis());
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
      stockfishClient.newGame();
    } catch (RuntimeException e) {
      throw new MatchEngineException("Failed to initialize a new Stockfish match", e);
    }
    currentMatch.set(match);
    return match;
  }

  public Match playUntilFinished() {
    Match match = currentMatch.get();
    if (match == null) {
      match = startNewMatch();
    }
    Map<String, Integer> positionOccurrences = buildPositionOccurrences(match);

    while (match.isInProgress() && !stopRequested.get()) {
      if (match.moveCount() >= maxPlies) {
        match = finishMatch(match, GameResult.DRAW);
        break;
      }

      try {
        stockfishClient.setPosition(match.currentPosition().fen());
        MoveNotation moveNotation = new MoveNotation(stockfishClient.bestMove(thinkTime));
        match =
            match.recordMove(
                moveNotation, chessBoardService.applyMove(match.currentPosition(), moveNotation));
        currentMatch.set(match);
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
            "Match execution failed while processing ply " + (match.moveCount() + 1), e);
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
    return positionOccurrences.merge(chessBoardService.normalizedPositionKey(position), 1, Integer::sum);
  }
}
