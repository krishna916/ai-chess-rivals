package dev.krishnamurti.ai_chess_rivals.game.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.krishnamurti.ai_chess_rivals.game.config.OwnerControlProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;

/** Authenticates owner controls after Spring MVC has matched the request path. */
public final class OwnerTokenInterceptor implements HandlerInterceptor {

  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTH_CODE = "OWNER_AUTH_REQUIRED";
  private static final String AUTH_MESSAGE = "A valid owner token is required.";

  private final byte[] expectedToken;
  private final ObjectWriter problemWriter;

  public OwnerTokenInterceptor(OwnerControlProperties properties, ObjectMapper objectMapper) {
    this.expectedToken = properties.controlToken().getBytes(UTF_8);
    this.problemWriter = objectMapper.writerFor(ProblemDetail.class);
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    if (!"POST".equals(request.getMethod())) {
      return true;
    }
    String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (hasValidToken(authorization)) {
      return true;
    }

    ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, AUTH_MESSAGE);
    detail.setTitle("Owner Authorization Required");
    detail.setProperty("code", AUTH_CODE);
    detail.setProperty("message", AUTH_MESSAGE);
    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    problemWriter.writeValue(response.getOutputStream(), detail);
    return false;
  }

  private boolean hasValidToken(String authorization) {
    if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
      return false;
    }
    byte[] suppliedToken = authorization.substring(BEARER_PREFIX.length()).getBytes(UTF_8);
    return suppliedToken.length > 0 && MessageDigest.isEqual(expectedToken, suppliedToken);
  }
}
