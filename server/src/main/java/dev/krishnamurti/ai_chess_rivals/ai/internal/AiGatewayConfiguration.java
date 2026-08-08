package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiGatewayConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
  AiChatGateway enabledAiChatGateway(
      @Qualifier("groqProviderChatClient") ProviderChatClient groq,
      @Qualifier("geminiProviderChatClient") ProviderChatClient gemini) {
    return new FailoverAiChatGateway(groq, gemini);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.ai",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  AiChatGateway disabledAiChatGateway() {
    return new DisabledAiChatGateway();
  }
}
