package dev.krishnamurti.ai_chess_rivals.game.application;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.game.config.GameProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import dev.krishnamurti.ai_chess_rivals.game.domain.MoveNotation;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public final class StockfishPlayer implements ChessPlayer {

  private final StockfishClient stockfishClient;
  private final Duration thinkTime;

  public StockfishPlayer(StockfishClient stockfishClient, GameProperties gameProperties) {
    this.stockfishClient =
        Objects.requireNonNull(stockfishClient, "stockfishClient must not be null");
    Objects.requireNonNull(gameProperties, "gameProperties must not be null");
    this.thinkTime = Duration.ofMillis(gameProperties.moveThinkTimeMillis());
  }

  @Override
  public void startNewGame() {
    stockfishClient.newGame();
  }

  @Override
  public MoveNotation chooseMove(Match match) {
    Match currentMatch = Objects.requireNonNull(match, "match must not be null");
    if (!currentMatch.isInProgress()) {
      throw new IllegalStateException("Cannot choose a move when match is not in progress");
    }

    stockfishClient.setPosition(currentMatch.currentPosition().fen());
    return new MoveNotation(stockfishClient.bestMove(thinkTime));
  }
}
