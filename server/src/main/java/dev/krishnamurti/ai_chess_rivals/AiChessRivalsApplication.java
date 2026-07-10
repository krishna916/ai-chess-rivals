package dev.krishnamurti.ai_chess_rivals;

import dev.krishnamurti.ai_chess_rivals.chess.NativeRuntimeHints;
import dev.krishnamurti.ai_chess_rivals.game.config.GameNativeRuntimeHints;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints({NativeRuntimeHints.class, GameNativeRuntimeHints.class})
public class AiChessRivalsApplication {

  public static void main(String[] args) {
    SpringApplication.run(AiChessRivalsApplication.class, args);
  }
}
