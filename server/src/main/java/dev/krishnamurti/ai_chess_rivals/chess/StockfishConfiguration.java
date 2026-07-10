package dev.krishnamurti.ai_chess_rivals.chess;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the {@link StockfishEngine} as the {@link StockfishClient} Spring bean.
 *
 * <p>Package-private so that {@link StockfishEngine}'s package-private visibility is respected.
 * {@link dev.krishnamurti.ai_chess_rivals.chess.config.ChessConfig} in {@code chess.config} handles
 * property binding; this class handles bean creation.
 */
@Configuration
class StockfishConfiguration {

  @Bean
  StockfishClient stockfishClient(ChessProperties chessProperties) {
    try {
      return new StockfishEngine(chessProperties);
    } catch (Exception e) {
      throw new StockfishException("Failed to initialize StockfishEngine bean", e);
    }
  }
}
