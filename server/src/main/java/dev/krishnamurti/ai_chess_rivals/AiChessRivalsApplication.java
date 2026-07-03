package dev.krishnamurti.ai_chess_rivals;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;
import dev.krishnamurti.ai_chess_rivals.config.NativeRuntimeHints;

@SpringBootApplication
@ImportRuntimeHints(NativeRuntimeHints.class)
public class AiChessRivalsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AiChessRivalsApplication.class, args);
	}

}
