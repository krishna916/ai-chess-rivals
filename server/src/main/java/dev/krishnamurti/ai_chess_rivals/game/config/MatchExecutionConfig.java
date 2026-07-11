package dev.krishnamurti.ai_chess_rivals.game.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchExecutionConfig {

  @Bean(destroyMethod = "shutdown")
  public ExecutorService matchExecutor() {
    return Executors.newSingleThreadExecutor(
        runnable -> {
          Thread thread = new Thread(runnable, "match-worker");
          thread.setDaemon(true);
          return thread;
        });
  }
}
