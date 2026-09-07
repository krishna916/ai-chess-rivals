package dev.krishnamurti.ai_chess_rivals.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTracingFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-ID";
  static final String REQUEST_ID_MDC_KEY = "requestId";
  private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
  private static final Logger log = LoggerFactory.getLogger(RequestTracingFilter.class);

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return !(path.startsWith("/api/") || path.startsWith("/ws/"));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String requestId = resolveRequestId(request);
    String previousRequestId = MDC.get(REQUEST_ID_MDC_KEY);
    long startedAtNanos = System.nanoTime();

    response.setHeader(REQUEST_ID_HEADER, requestId);
    MDC.put(REQUEST_ID_MDC_KEY, requestId);

    boolean completedNormally = false;
    try {
      filterChain.doFilter(request, response);
      completedNormally = true;
    } finally {
      int completionStatus = response.getStatus();
      if (!completedNormally && completionStatus < 400) {
        completionStatus = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
      }
      long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
      log.info(
          "HTTP request completed method={} path={} status={} durationMs={}",
          request.getMethod(),
          request.getRequestURI(),
          completionStatus,
          durationMs);
      restoreMdc(previousRequestId);
    }
  }

  private static String resolveRequestId(HttpServletRequest request) {
    String candidate = request.getHeader(REQUEST_ID_HEADER);
    if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
      return candidate;
    }
    return UUID.randomUUID().toString();
  }

  private static void restoreMdc(String previousRequestId) {
    if (previousRequestId == null) {
      MDC.remove(REQUEST_ID_MDC_KEY);
    } else {
      MDC.put(REQUEST_ID_MDC_KEY, previousRequestId);
    }
  }
}
