package dev.krishnamurti.ai_chess_rivals.ai.internal;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import dev.krishnamurti.ai_chess_rivals.ai.config.AiProperties;
import java.time.Duration;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
class AiProviderConfiguration {

  @Bean("groqChatModel")
  ChatModel groqChatModel(AiProperties properties) {
    AiProperties.Groq groq = properties.groq();
    return OpenAiChatModel.builder()
        .options(groqOptions(groq.apiKey(), groq.baseUrl(), groq.model(), groq.timeout()))
        .build();
  }

  @Bean("groqChatClient")
  ChatClient groqChatClient(@Qualifier("groqChatModel") ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }

  @Bean("groqProviderChatClient")
  ProviderChatClient groqProviderChatClient(@Qualifier("groqChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  @Bean("geminiChatModel")
  ChatModel geminiChatModel(AiProperties properties) {
    AiProperties.Gemini gemini = properties.gemini();
    Client client =
        Client.builder()
            .apiKey(gemini.apiKey())
            .httpOptions(geminiHttpOptions(gemini.timeout()))
            .build();

    return GoogleGenAiChatModel.builder()
        .genAiClient(client)
        .options(GoogleGenAiChatOptions.builder().model(gemini.model()).build())
        .retryTemplate(noRetryTemplate())
        .build();
  }

  @Bean("geminiChatClient")
  ChatClient geminiChatClient(@Qualifier("geminiChatModel") ChatModel chatModel) {
    return ChatClient.builder(chatModel).build();
  }

  @Bean("geminiProviderChatClient")
  ProviderChatClient geminiProviderChatClient(
      @Qualifier("geminiChatClient") ChatClient chatClient) {
    return prompt -> chatClient.prompt().user(prompt).call().content();
  }

  static OpenAiChatOptions groqOptions(
      String apiKey, String baseUrl, String model, Duration timeout) {
    return OpenAiChatOptions.builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .model(model)
        .timeout(timeout)
        .maxRetries(0)
        .build();
  }

  static HttpOptions geminiHttpOptions(Duration timeout) {
    return HttpOptions.builder()
        .timeout(Math.toIntExact(timeout.toMillis()))
        .retryOptions(HttpRetryOptions.builder().attempts(1).build())
        .build();
  }

  static RetryTemplate noRetryTemplate() {
    return new RetryTemplate(RetryPolicy.builder().maxRetries(0).build());
  }
}
