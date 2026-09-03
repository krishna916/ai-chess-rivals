package dev.krishnamurti.ai_chess_rivals.ai.internal;

import dev.krishnamurti.ai_chess_rivals.ai.config.AiProperties;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
class AiProviderConfiguration {

  @Bean("openRouterPrimaryChatModel")
  ChatModel openRouterPrimaryChatModel(
      AiProperties properties, ObservationRegistry observationRegistry) {
    AiProperties.OpenRouter openrouter = properties.openrouter();
    return OpenAiChatModel.builder()
        .options(
            openRouterOptions(
                openrouter.apiKey(),
                openrouter.baseUrl(),
                openrouter.primaryModel(),
                openrouter.primaryTimeout()))
        .observationRegistry(observationRegistry)
        .build();
  }

  @Bean("openRouterPrimaryChatClient")
  ChatClient openRouterPrimaryChatClient(
      @Qualifier("openRouterPrimaryChatModel") ChatModel chatModel,
      DialogueBoundaryAdvisor dialogueBoundaryAdvisor) {
    return ChatClient.builder(chatModel).defaultAdvisors(dialogueBoundaryAdvisor).build();
  }

  @Bean("openRouterPrimaryProviderChatClient")
  ProviderChatClient openRouterPrimaryProviderChatClient(
      @Qualifier("openRouterPrimaryChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  @Bean("openRouterFallbackChatModel")
  ChatModel openRouterFallbackChatModel(
      AiProperties properties, ObservationRegistry observationRegistry) {
    AiProperties.OpenRouter openrouter = properties.openrouter();
    return OpenAiChatModel.builder()
        .options(
            openRouterOptions(
                openrouter.apiKey(),
                openrouter.baseUrl(),
                openrouter.fallbackModel(),
                openrouter.fallbackTimeout()))
        .observationRegistry(observationRegistry)
        .build();
  }

  @Bean("openRouterFallbackChatClient")
  ChatClient openRouterFallbackChatClient(
      @Qualifier("openRouterFallbackChatModel") ChatModel chatModel,
      DialogueBoundaryAdvisor dialogueBoundaryAdvisor) {
    return ChatClient.builder(chatModel).defaultAdvisors(dialogueBoundaryAdvisor).build();
  }

  @Bean("openRouterFallbackProviderChatClient")
  ProviderChatClient openRouterFallbackProviderChatClient(
      @Qualifier("openRouterFallbackChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  @Bean
  DialogueBoundaryAdvisor dialogueBoundaryAdvisor() {
    return new DialogueBoundaryAdvisor();
  }

  static OpenAiChatOptions openRouterOptions(
      String apiKey, String baseUrl, String model, Duration timeout) {
    return OpenAiChatOptions.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .model(model)
        .timeout(timeout)
        .maxRetries(0)
        .build();
  }
}
