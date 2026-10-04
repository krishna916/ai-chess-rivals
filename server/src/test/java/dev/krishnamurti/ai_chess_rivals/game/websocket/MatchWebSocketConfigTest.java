package dev.krishnamurti.ai_chess_rivals.game.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.socket.config.annotation.ServletWebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;
import org.springframework.web.socket.server.support.WebSocketHttpRequestHandler;

class MatchWebSocketConfigTest {

  private final MatchWebSocketConfig.RequestTraceHandshakeInterceptor interceptor =
      new MatchWebSocketConfig.RequestTraceHandshakeInterceptor();

  @ParameterizedTest
  @CsvSource({
    "'http://localhost', true",
    "'http://localhost:3000', true",
    "'https://localhost', true",
    "'https://localhost:9443', true",
    "'http://preview.krishnamurti.dev', true",
    "'https://ai-chess.krishnamurti.dev', true",
    "'http://nested.preview.krishnamurti.dev:3000', true",
    "'https://preview.krishnamurti.dev:9443', true",
    "'https://example.com', false",
    "'http://localhost.evil.com:3000', false",
    "'https://evilkrishnamurti.dev', false",
    "'https://preview.krishnamurti.dev.evil.com', false",
    "'https://krishnamurti.dev', false",
    "'http://127.0.0.1:5173', false",
    "'null', false"
  })
  void handshakeEnforcesTrustedOriginPolicy(String origin, boolean allowed) throws Exception {
    MatchWebSocketHandler handler = mock(MatchWebSocketHandler.class);
    ServletWebSocketHandlerRegistry registry = new ServletWebSocketHandlerRegistry();
    new MatchWebSocketConfig(handler, new WebSocketProperties(List.of("http://localhost:5173")))
        .registerWebSocketHandlers(registry);
    SimpleUrlHandlerMapping mapping = (SimpleUrlHandlerMapping) registry.getHandlerMapping();
    WebSocketHttpRequestHandler requestHandler =
        (WebSocketHttpRequestHandler) mapping.getUrlMap().get("/ws/match");
    OriginHandshakeInterceptor originInterceptor =
        requestHandler.getHandshakeInterceptors().stream()
            .filter(OriginHandshakeInterceptor.class::isInstance)
            .map(OriginHandshakeInterceptor.class::cast)
            .findFirst()
            .orElseThrow();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/match");
    request.setServerName("backend.internal");
    request.addHeader("Origin", origin);
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThat(
            originInterceptor.beforeHandshake(
                new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(response),
                handler,
                new HashMap<>()))
        .isEqualTo(allowed);
    if (!allowed) {
      assertThat(response.getStatus()).isEqualTo(403);
    }
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void handshakeCarriesValidatedRequestIdIntoSessionAttributes() {
    MDC.put("requestId", "websocket-handshake-001");
    Map<String, Object> attributes = new HashMap<>();

    interceptor.beforeHandshake(null, null, null, attributes);

    assertThat(attributes)
        .containsEntry(
            MatchWebSocketConfig.RequestTraceHandshakeInterceptor.TRACE_REQUEST_ID_ATTRIBUTE,
            "websocket-handshake-001");
  }

  @Test
  void handshakeGeneratesRequestIdWhenFilterContextIsMissing() {
    Map<String, Object> attributes = new HashMap<>();

    interceptor.beforeHandshake(null, null, null, attributes);

    assertThat(attributes)
        .containsKey(
            MatchWebSocketConfig.RequestTraceHandshakeInterceptor.TRACE_REQUEST_ID_ATTRIBUTE);
    assertThat(
            attributes.get(
                MatchWebSocketConfig.RequestTraceHandshakeInterceptor.TRACE_REQUEST_ID_ATTRIBUTE))
        .isInstanceOf(String.class)
        .matches(value -> value.toString().length() == 36);
  }
}
