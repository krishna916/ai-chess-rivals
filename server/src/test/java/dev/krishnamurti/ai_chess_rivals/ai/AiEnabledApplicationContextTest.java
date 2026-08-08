package dev.krishnamurti.ai_chess_rivals.ai;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
    properties = {
      "app.ai.enabled=true",
      "app.ai.groq.api-key=test-groq-key",
      "app.ai.groq.model=test-groq-model",
      "app.ai.gemini.api-key=test-gemini-key",
      "app.ai.gemini.model=test-gemini-model",
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
@Import(PersonalityRepositoryTestConfiguration.class)
class AiEnabledApplicationContextTest {

  @Autowired ApplicationContext context;

  @MockitoBean StockfishClient stockfishClient;

  @Test
  void enabledAiModeUsesOnlyExplicitProviderClients() {
    assertThat(context.getBeansOfType(ChatModel.class))
        .containsOnlyKeys("groqChatModel", "geminiChatModel");
    assertThat(context.getBeansOfType(ChatClient.class))
        .containsOnlyKeys("groqChatClient", "geminiChatClient");
    assertThat(context.getBeansOfType(ChatClient.Builder.class)).isEmpty();
    assertThat(context.getBeansOfType(AiChatGateway.class)).hasSize(1);
  }
}
