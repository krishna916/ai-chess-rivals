package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationRequested;
import dev.krishnamurti.ai_chess_rivals.chess.api.ChessEvaluationResult;
import dev.krishnamurti.ai_chess_rivals.chess.api.EvaluationSwing;
import dev.krishnamurti.ai_chess_rivals.chess.api.PositionEvaluation;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.GameResult;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.Move;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import dev.krishnamurti.ai_chess_rivals.game.domain.PlayerColor;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchEventSink;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchFinished;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStarted;
import dev.krishnamurti.ai_chess_rivals.game.event.MatchStopped;
import dev.krishnamurti.ai_chess_rivals.game.event.MovePlayed;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Runs a single synchronous match from start to finish. */
@Service
@Slf4j
public final class MatchEngine {

  private final ChessPlayer chessPlayer;
  private final ChessBoardService chessBoardService;
  private final MatchPacing matchPacing;
  private final MatchEventSink matchEventSink;
  private final ApplicationEventPublisher eventPublisher;
  private final int maxPlies;
  private final AtomicReference<Match> currentMatch = new AtomicReference<>();
  private final AtomicBoolean stopRequested = new AtomicBoolean(false);
  private final AtomicReference<EvaluationBaseline> evaluationBaseline = new AtomicReference<>();
  private final AtomicLong evaluationCorrelation = new AtomicLong();
  private final ConcurrentHashMap<Long, CompletableFuture<ChessEvaluationResult>>
      pendingEvaluations = new ConcurrentHashMap<>();

  public MatchEngine(
      @Qualifier("stockfishPlayer") ChessPlayer chessPlayer,
      ChessBoardService chessBoardService,
      GameProperties gameProperties,
      MatchPacing matchPacing,
      MatchEventSink matchEventSink,
      ApplicationEventPublisher eventPublisher) {
    this.chessPlayer = Objects.requireNonNull(chessPlayer, "chessPlayer must not be null");
    this.chessBoardService =
        Objects.requireNonNull(chessBoardService, "chessBoardService must not be null");
    Objects.requireNonNull(gameProperties, "gameProperties must not be null");
    this.matchPacing = Objects.requireNonNull(matchPacing, "matchPacing must not be null");
    this.matchEventSink = Objects.requireNonNull(matchEventSink, "matchEventSink must not be null");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
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
    evaluationBaseline.set(null);
    String startingFen = match.currentPosition().fen();
    requestEvaluation(0, startingFen, Optional.empty())
        .evaluation()
        .ifPresent(
            evaluation ->
                evaluationBaseline.set(new EvaluationBaseline(0, startingFen, evaluation)));
    try {
      matchEventSink.publish(new MatchStarted(match.sideToMove(), match.currentPosition()));
    } catch (RuntimeException e) {
      throw new MatchEngineException("Failed to publish match start event", e);
    }
    currentMatch.set(match);
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
      GameResult result;
      Optional<PositionEvaluation> beforeEvaluation = evaluationBefore(match);
      try {
        MoveNotation moveNotation = chessPlayer.chooseMove(match);
        PlayerColor player = match.sideToMove();
        AppliedMove appliedMove =
            chessBoardService.applyMove(match.currentPosition(), moveNotation);
        Match nextMatch =
            match.recordMove(moveNotation, appliedMove.position(), appliedMove.details());
        Move recordedMove = nextMatch.moves().getLast();
        match = nextMatch;
        currentMatch.set(match);
        String committedFen = match.currentPosition().fen();
        ChessEvaluationResult evaluationResult =
            requestEvaluation(recordedMove.sequenceNumber(), committedFen, beforeEvaluation);
        evaluationResult
            .evaluation()
            .ifPresent(
                evaluation ->
                    storeBaselineIfAuthoritative(
                        recordedMove.sequenceNumber(), committedFen, evaluation));
        Optional<EvaluationSwing> evaluationSwing = evaluationResult.swing();
        matchEventSink.publish(
            new MovePlayed(
                recordedMove.sequenceNumber(),
                player,
                recordedMove.notation(),
                recordedMove.positionAfterMove(),
                recordedMove.details(),
                evaluationSwing));
        int currentPositionOccurrences =
            recordPositionOccurrence(positionOccurrences, match.currentPosition());
        result =
            chessBoardService
                .determineResult(
                    match.currentPosition(), match.sideToMove(), currentPositionOccurrences)
                .orElse(null);
      } catch (RuntimeException e) {
        throw new MatchEngineException("Match execution failed while processing ply " + ply, e);
      }

      if (result != null) {
        match = finishMatch(match, result);
      } else if (!stopRequested.get() && !waitBeforeNextMove(ply)) {
        break;
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
    if (stopRequested.compareAndSet(false, true)) {
      Match match = currentMatch.get();
      if (match != null && match.isInProgress()) {
        matchEventSink.publish(
            new MatchStopped(match.sideToMove(), match.currentPosition(), match.moveCount()));
      }
    }
  }

  private boolean waitBeforeNextMove(int ply) {
    try {
      matchPacing.waitBeforeNextMove();
      return true;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      if (!stopRequested.get()) {
        throw new MatchEngineException(
            "Match execution was interrupted unexpectedly while processing ply " + ply, exception);
      }
      return false;
    }
  }

  private Match finishMatch(Match match, GameResult result) {
    Match finishedMatch = match.finish(result);
    matchEventSink.publish(
        new MatchFinished(result, finishedMatch.currentPosition(), finishedMatch.moveCount()));
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
    return positionOccurrences.merge(
        chessBoardService.normalizedPositionKey(position), 1, Integer::sum);
  }

  private Optional<PositionEvaluation> evaluationBefore(Match match) {
    EvaluationBaseline baseline = evaluationBaseline.get();
    String fen = match.currentPosition().fen();
    if (baseline != null && baseline.ply() == match.moveCount() && baseline.fen().equals(fen)) {
      return Optional.of(baseline.evaluation());
    }
    return requestEvaluation(match.moveCount(), fen, Optional.empty()).evaluation();
  }

  private void storeBaselineIfAuthoritative(int ply, String fen, PositionEvaluation evaluation) {
    Match authoritative = currentMatch.get();
    if (authoritative != null
        && authoritative.moveCount() == ply
        && authoritative.currentPosition().fen().equals(fen)) {
      evaluationBaseline.set(new EvaluationBaseline(ply, fen, evaluation));
    }
  }

  private ChessEvaluationResult requestEvaluation(
      int ply, String fen, Optional<PositionEvaluation> before) {
    ChessEvaluationRequested request =
        new ChessEvaluationRequested(evaluationCorrelation.incrementAndGet(), ply, fen, before);
    CompletableFuture<ChessEvaluationResult> result = new CompletableFuture<>();
    pendingEvaluations.put(request.correlationId(), result);
    try {
      eventPublisher.publishEvent(request);
      ChessEvaluationResult evaluationResult = result.getNow(null);
      if (evaluationResult == null || !matches(request, evaluationResult)) {
        log.warn(
            "Chess evaluation result unavailable or mismatched for ply {} and position {}",
            ply,
            fen);
        return ChessEvaluationResult.unavailable(request);
      }
      return evaluationResult;
    } catch (RuntimeException exception) {
      log.warn("Chess evaluation request failed for ply {} and position {}", ply, fen, exception);
      return ChessEvaluationResult.unavailable(request);
    } finally {
      pendingEvaluations.remove(request.correlationId());
    }
  }

  @EventListener
  void onEvaluationResult(ChessEvaluationResult result) {
    CompletableFuture<ChessEvaluationResult> pending =
        pendingEvaluations.get(result.correlationId());
    if (pending != null) {
      pending.complete(result);
    }
  }

  private static boolean matches(ChessEvaluationRequested request, ChessEvaluationResult result) {
    return request.correlationId() == result.correlationId()
        && request.ply() == result.ply()
        && request.fen().equals(result.fen());
  }

  private record EvaluationBaseline(int ply, String fen, PositionEvaluation evaluation) {}
}
