package dev.krishnamurti.ai_chess_rivals.game.config;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchExecutionGuard;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Activates configuration properties for the game module. */
@Configuration
@EnableConfigurationProperties({
  GameProperties.class,
  OwnerControlProperties.class,
  MatchGuardProperties.class
})
public class GameConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  MatchExecutionGuard matchExecutionGuard(Clock clock, MatchGuardProperties properties) {
    return new MatchExecutionGuard(clock, properties.cooldown(), properties.dailyStartLimit());
  }
}
