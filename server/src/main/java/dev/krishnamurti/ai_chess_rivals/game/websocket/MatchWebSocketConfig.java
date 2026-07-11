package dev.krishnamurti.ai_chess_rivals.game.websocket;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
public class MatchWebSocketConfig implements WebSocketConfigurer {

  private final MatchWebSocketHandler matchWebSocketHandler;
  private final WebSocketProperties webSocketProperties;

  public MatchWebSocketConfig(
      MatchWebSocketHandler matchWebSocketHandler, WebSocketProperties webSocketProperties) {
    this.matchWebSocketHandler = matchWebSocketHandler;
    this.webSocketProperties = webSocketProperties;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(matchWebSocketHandler, "/ws/match")
        .setAllowedOrigins(webSocketProperties.allowedOrigins().toArray(String[]::new));
  }
}
