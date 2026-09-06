package dev.krishnamurti.ai_chess_rivals.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTracingFilterTest {

  private RequestTracingFilter filter;
  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void setUp() {
    filter = new RequestTracingFilter();
    logger = (Logger) LoggerFactory.getLogger(RequestTracingFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    MDC.clear();
  }

  @Test
  void generatesRequestIdAndLogsApiCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/personalities");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("requestId")).isNotNull();
          response.setStatus(200);
        });

    String requestId = response.getHeader("X-Request-ID");
    assertThat(requestId).isNotNull();
    assertThat(UUID.fromString(requestId)).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains(
            "requestId=" + requestId,
            "method=GET",
            "path=/api/v1/personalities",
            "status=200",
            "durationMs=");
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void reusesSafeIncomingRequestId() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/personalities");
    request.addHeader("X-Request-ID", "manual-trace-001");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("requestId")).isEqualTo("manual-trace-001");
          response.setStatus(200);
        });

    assertThat(response.getHeader("X-Request-ID")).isEqualTo("manual-trace-001");
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("requestId=manual-trace-001", "method=GET", "path=/api/v1/personalities");
  }

  @Test
  void rejectsUnsafeIncomingRequestId() throws Exception {
    String unsafeRequestId = "unsafe\nforged-log-line";
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/personalities");
    request.addHeader("X-Request-ID", unsafeRequestId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> response.setStatus(200));

    String requestId = response.getHeader("X-Request-ID");
    assertThat(requestId).isNotEqualTo(unsafeRequestId);
    assertThat(UUID.fromString(requestId)).isNotNull();
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .doesNotContain("unsafe", "forged-log-line");
  }

  @Test
  void restoresExistingMdcValue() throws Exception {
    MDC.put("requestId", "outer-request");
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/personalities");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (req, res) -> {
          assertThat(MDC.get("requestId")).isNotEqualTo("outer-request");
          response.setStatus(200);
        });

    assertThat(MDC.get("requestId")).isEqualTo("outer-request");
  }

  @Test
  void tracesWebSocketHandshakePath() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/match");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> response.setStatus(101));

    assertThat(response.getHeader("X-Request-ID")).isNotNull();
    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage()).contains("path=/ws/match", "status=101");
  }

  @Test
  void skipsManagementAndUnrelatedPaths() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> response.setStatus(200));

    assertThat(response.getHeader("X-Request-ID")).isNull();
    assertThat(appender.list).isEmpty();
  }

  @Test
  void doesNotLogSensitiveRequestData() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/match");
    request.setQueryString("token=super-secret");
    request.addHeader("Authorization", "Bearer secret-auth");
    request.addHeader("Cookie", "session=secret-cookie");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (req, res) -> response.setStatus(200));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("path=/api/v1/match")
        .doesNotContain("super-secret", "secret-auth", "secret-cookie");
  }
}
