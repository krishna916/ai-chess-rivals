package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;

class ValidationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
          .withUserConfiguration(ValidationConfiguration.class);

  @Test
  void configuresAlwaysTraversableResolver() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          ValidatorFactory validatorFactory = context.getBean(ValidatorFactory.class);

          assertThat(validatorFactory.getTraversableResolver())
              .isSameAs(AlwaysTraversableResolver.INSTANCE);
        });
  }

  @Test
  void keepsNestedConfigurationValidationActive() {
    contextRunner.run(
        context -> {
          assertThat(context).hasNotFailed();
          Validator validator = context.getBean(Validator.class);
          ChessProperties.Stockfish.Evaluation invalidEvaluation =
              new ChessProperties.Stockfish.Evaluation(8, 50, 0, 200);
          ChessProperties properties =
              new ChessProperties(
                  new ChessProperties.Stockfish(
                      "stockfish/stockfish", 1, 16, 10, 30, invalidEvaluation));

          Set<ConstraintViolation<ChessProperties>> violations = validator.validate(properties);

          assertThat(violations)
              .extracting(violation -> violation.getPropertyPath().toString())
              .contains("stockfish.evaluation.majorGainThresholdCentipawns");
        });
  }
}
