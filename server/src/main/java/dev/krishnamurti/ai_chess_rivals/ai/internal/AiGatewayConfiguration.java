package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AiGatewayConfiguration {

  private static final Logger log = LoggerFactory.getLogger(AiGatewayConfiguration.class);

  @Bean
  @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
  AiChatGateway enabledAiChatGateway(
      @Qualifier("groqProviderChatClient") ProviderChatClient groq,
      @Qualifier("geminiProviderChatClient") ProviderChatClient gemini) {
    log.info("AI gateway topology: enabled (Groq -> Gemini)");
    return new FailoverAiChatGateway(groq, gemini);
  }

  @Bean
  @ConditionalOnProperty(
      prefix = "app.ai",
      name = "enabled",
      havingValue = "false",
      matchIfMissing = true)
  AiChatGateway disabledAiChatGateway() {
    log.info("AI gateway topology: disabled");
    return new DisabledAiChatGateway();
  }
}
