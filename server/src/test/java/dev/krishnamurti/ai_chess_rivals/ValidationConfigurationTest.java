package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessConfig;
import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ValidationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  ConfigurationPropertiesAutoConfiguration.class,
                  ValidationAutoConfiguration.class))
          .withUserConfiguration(ValidationConfiguration.class, ChessConfig.class)
          .withPropertyValues(
              "app.chess.stockfish.path=stockfish/stockfish",
              "app.chess.stockfish.threads=1",
              "app.chess.stockfish.hash-mb=16",
              "app.chess.stockfish.startup-timeout-seconds=10",
              "app.chess.stockfish.move-timeout-seconds=30",
              "app.chess.stockfish.evaluation.depth=8",
              "app.chess.stockfish.evaluation.move-time-millis=50",
              "app.chess.stockfish.evaluation.major-gain-threshold-centipawns=200",
              "app.chess.stockfish.evaluation.major-mistake-threshold-centipawns=200");

  @Test
  void exposesNativeSafeConfigurationPropertiesValidator() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          LocalValidatorFactoryBean validator =
              context.getBean(
                  EnableConfigurationProperties.VALIDATOR_BEAN_NAME,
                  LocalValidatorFactoryBean.class);

          assertThat(validator.getTraversableResolver())
              .isSameAs(AlwaysTraversableResolver.INSTANCE);
        });
  }

  @Test
  void bindsValidNestedChessConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          ChessProperties properties = context.getBean(ChessProperties.class);
          assertThat(properties.stockfish().evaluation().majorGainThresholdCentipawns())
              .isEqualTo(200);
        });
  }

  @Test
  void rejectsInvalidNestedChessThresholdThroughConfigurationBinding() {
    contextRunner
        .withPropertyValues("app.chess.stockfish.evaluation.major-gain-threshold-centipawns=0")
        .run(context -> assertThat(context).hasFailed());
  }
}
