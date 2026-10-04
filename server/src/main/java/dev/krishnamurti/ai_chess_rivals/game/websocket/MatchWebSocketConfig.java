package dev.krishnamurti.ai_chess_rivals.game.websocket;

import dev.krishnamurti.ai_chess_rivals.AllowedOriginPatterns;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration
@EnableWebSocket
@EnableConfigurationProperties(WebSocketProperties.class)
public class MatchWebSocketConfig implements WebSocketConfigurer {

  private static final List<String> ALLOWED_ORIGIN_PATTERNS =
      List.of(
          AllowedOriginPatterns.LOCALHOST_HTTP,
          AllowedOriginPatterns.LOCALHOST_HTTPS,
          AllowedOriginPatterns.SUBDOMAIN_HTTP,
          AllowedOriginPatterns.SUBDOMAIN_HTTPS);

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
        .addInterceptors(new RequestTraceHandshakeInterceptor())
        .setAllowedOrigins(webSocketProperties.allowedOrigins().toArray(String[]::new))
        .setAllowedOriginPatterns(ALLOWED_ORIGIN_PATTERNS.toArray(String[]::new));
  }

  static final class RequestTraceHandshakeInterceptor implements HandshakeInterceptor {

    static final String TRACE_REQUEST_ID_ATTRIBUTE = "traceRequestId";

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Map<String, Object> attributes) {
      String requestId = org.slf4j.MDC.get("requestId");
      attributes.put(
          TRACE_REQUEST_ID_ATTRIBUTE, requestId == null ? UUID.randomUUID().toString() : requestId);
      return true;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler handler,
        Exception exception) {}
  }
}
