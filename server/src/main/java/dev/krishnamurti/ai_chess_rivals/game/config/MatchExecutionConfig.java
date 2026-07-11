package dev.krishnamurti.ai_chess_rivals.game.config;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MatchExecutionConfig {

  private static final Duration MATCH_EXECUTOR_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  @Bean(destroyMethod = "")
  public ExecutorService matchExecutor() {
    return Executors.newSingleThreadExecutor(
        runnable -> {
          Thread thread = new Thread(runnable, "match-worker");
          thread.setDaemon(true);
          return thread;
        });
  }

  @Bean
  public DisposableBean matchExecutorShutdown(
      @Qualifier("matchExecutor") ExecutorService matchExecutor) {
    return () -> {
      matchExecutor.shutdown();
      if (!matchExecutor.awaitTermination(
          MATCH_EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
        matchExecutor.shutdownNow();
        matchExecutor.awaitTermination(
            MATCH_EXECUTOR_SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
      }
    };
  }
}
