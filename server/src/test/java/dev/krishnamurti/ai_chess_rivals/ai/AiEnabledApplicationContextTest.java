package dev.krishnamurti.ai_chess_rivals.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.dialogue.DialogueRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "app.ai.enabled=true",
      "app.ai.openrouter.api-key=test-openrouter-key",
      "app.ai.openrouter.primary-model=inclusionai/ling-3.0-flash:free",
      "app.ai.openrouter.fallback-model=~deepseek/deepseek-v4-flash-latest",
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
@Import({PersonalityRepositoryTestConfiguration.class, DialogueRepositoryTestConfiguration.class})
class AiEnabledApplicationContextTest {

  @Autowired ApplicationContext context;

  @Autowired Environment environment;

  @MockitoBean StockfishClient stockfishClient;

  @Test
  void enabledAiModeUsesOnlyExplicitProviderClients() {
    assertThat(context.getBeansOfType(ChatModel.class))
        .containsOnlyKeys("openRouterPrimaryChatModel", "openRouterFallbackChatModel");
    assertThat(context.getBeansOfType(ChatClient.class))
        .containsOnlyKeys("openRouterPrimaryChatClient", "openRouterFallbackChatClient");
    assertThat(context.getBeansOfType(ChatClient.Builder.class)).isEmpty();
    assertThat(context.getBeansOfType(AiChatGateway.class)).hasSize(1);
    assertThat(environment.getProperty("spring.ai.chat.observations.log-prompt", Boolean.class))
        .isFalse();
    assertThat(environment.getProperty("spring.ai.chat.observations.log-completion", Boolean.class))
        .isFalse();
    assertThat(
            environment.getProperty(
                "spring.ai.chat.observations.include-error-logging", Boolean.class))
        .isFalse();
  }
}
