package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.krishnamurti.ai_chess_rivals.game.TestMatchFixtures;
import dev.krishnamurti.ai_chess_rivals.game.application.InvalidPersonalitySelectionException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchConflictException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchCooldownException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchDailyLimitException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngineException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartAvailability;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchStartBlockReason;
import dev.krishnamurti.ai_chess_rivals.game.config.OwnerControlProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = MatchController.class)
@Import(MatchControllerAdvice.class)
@EnableConfigurationProperties(OwnerControlProperties.class)
@TestPropertySource(
    properties = {
      "app.owner.control-token=test-owner-token",
      "APP_WEBSOCKET_ALLOWED_ORIGIN=https://ai-chess.krishnamurti.dev"
    })
class MatchControllerTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost",
        "http://localhost:3000",
        "https://localhost",
        "https://localhost:9443",
        "http://preview.krishnamurti.dev",
        "https://ai-chess.krishnamurti.dev",
        "http://nested.preview.krishnamurti.dev:3000",
        "https://preview.krishnamurti.dev:9443"
      })
  void preflightAllowsLocalhostAndOwnedSubdomains(String origin) throws Exception {
    mockMvc
        .perform(
            options("/api/v1/match/start")
                .with(
                    request -> {
                      request.setServerName("backend.internal");
                      return request;
                    })
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "https://example.com",
        "http://localhost.evil.com:3000",
        "https://evilkrishnamurti.dev",
        "https://preview.krishnamurti.dev.evil.com",
        "https://krishnamurti.dev",
        "http://127.0.0.1:5173",
        "null"
      })
  void preflightRejectsUntrustedOrigins(String origin) throws Exception {
    mockMvc
        .perform(
            options("/api/v1/match/start")
                .with(
                    request -> {
                      request.setServerName("backend.internal");
                      return request;
                    })
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MatchControlService matchControlService;

  @Test
  void logsAllMatchControllerEntryPoints() throws Exception {
    Match match = TestMatchFixtures.newMatch();
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenReturn(new MatchSnapshot(match, true));
    when(matchControlService.stopMatch()).thenReturn(new MatchSnapshot(match, false));
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));
    Logger logger = (Logger) LoggerFactory.getLogger(MatchController.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    try {
      mockMvc.perform(ownerStartPost()).andExpect(status().isAccepted());
      mockMvc
          .perform(
              post("/api/v1/match/stop")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer test-owner-token"))
          .andExpect(status().isAccepted());
      mockMvc.perform(get("/api/v1/match")).andExpect(status().isOk());

      assertThat(appender.list)
          .extracting(ILoggingEvent::getFormattedMessage)
          .anySatisfy(message -> assertThat(message).contains("operation=match_start"))
          .anySatisfy(message -> assertThat(message).contains("operation=match_stop"))
          .anySatisfy(message -> assertThat(message).contains("operation=match_current"));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  @Test
  void startMatchReturns202AndMatchSnapshot() throws Exception {
    Match match = TestMatchFixtures.newMatch();
    MatchStartAvailability availability =
        new MatchStartAvailability(false, MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0, 3, 12);
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenReturn(new MatchSnapshot(match, true, availability));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.whitePersonality.key").value("white-test"))
        .andExpect(jsonPath("$.whitePersonality.displayName").value("White Test"))
        .andExpect(jsonPath("$.blackPersonality.key").value("black-test"))
        .andExpect(jsonPath("$.blackPersonality.displayName").value("Black Test"))
        .andExpect(jsonPath("$.sideToMove").value("WHITE"))
        .andExpect(jsonPath("$.fen").value(match.currentPosition().fen()))
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.startAvailability.allowed").value(false))
        .andExpect(jsonPath("$.startAvailability.blockedBy").value("MATCH_ALREADY_RUNNING"))
        .andExpect(jsonPath("$.startAvailability.retryAfterSeconds").value(0))
        .andExpect(jsonPath("$.startAvailability.dailyStartsAccepted").value(3))
        .andExpect(jsonPath("$.startAvailability.dailyStartLimit").value(12))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.result").value((String) null));
  }

  @Test
  void stopMatchReturns202AndMatchSnapshot() throws Exception {
    Match match = TestMatchFixtures.newMatch();
    when(matchControlService.stopMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(ownerPost("/api/v1/match/stop"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.running").value(false));
  }

  @Test
  void currentMatchReturns200AndMatchSnapshot() throws Exception {
    Match match = TestMatchFixtures.newMatch();
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(get("/api/v1/match"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.running").value(false));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://localhost:3000",
        "https://localhost",
        "https://preview.krishnamurti.dev:9443",
        "https://ai-chess.krishnamurti.dev"
      })
  void currentMatchAllowsConfiguredFrontendOrigin(String origin) throws Exception {
    Match match = TestMatchFixtures.newMatch();
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(get("/api/v1/match").header(HttpHeaders.ORIGIN, origin))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
  }

  @Test
  void currentMatchReturns404ProblemDetailWhenNotFound() throws Exception {
    when(matchControlService.currentMatch())
        .thenThrow(new MatchNotFoundException("No match has been started"));

    mockMvc
        .perform(get("/api/v1/match"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Not Found"))
        .andExpect(jsonPath("$.detail").value("No match has been started"));
  }

  @Test
  void startMatchReturns409ProblemDetailOnConflict() throws Exception {
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenThrow(new MatchConflictException("A match is already active."));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Conflict"))
        .andExpect(jsonPath("$.detail").value("A match is already active."))
        .andExpect(jsonPath("$.code").value("MATCH_ALREADY_RUNNING"))
        .andExpect(jsonPath("$.message").value("A match is already active."));
  }

  @Test
  void startMatchReturns429AndRetryAfterDuringCooldown() throws Exception {
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenThrow(new MatchCooldownException(60));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MATCH_COOLDOWN_ACTIVE"))
        .andExpect(jsonPath("$.message").value("A new match cannot be started yet."))
        .andExpect(jsonPath("$.retryAfterSeconds").value(60));
  }

  @Test
  void startMatchReturns429WhenDailyLimitIsReached() throws Exception {
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenThrow(new MatchDailyLimitException(12));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isTooManyRequests())
        .andExpect(header().doesNotExist("Retry-After"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MATCH_DAILY_LIMIT_REACHED"))
        .andExpect(
            jsonPath("$.message").value("The configured daily match limit has been reached."));
  }

  @Test
  void unexpectedEngineFailureReturns500ProblemDetail() throws Exception {
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenThrow(
            new MatchEngineException("Failed to initialize a new match", new RuntimeException()));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Engine Error"))
        .andExpect(jsonPath("$.detail").value("Failed to initialize a new match"));
  }

  private static MockHttpServletRequestBuilder ownerPost(String path) {
    return post(path).header(HttpHeaders.AUTHORIZATION, "Bearer test-owner-token");
  }

  @Test
  void startMatchReturns400ProblemDetailForInvalidPersonalities() throws Exception {
    when(matchControlService.startMatch("blaze", "vesper"))
        .thenThrow(
            new InvalidPersonalitySelectionException(
                "Unknown or inactive White personality: blaze"));

    mockMvc
        .perform(ownerStartPost())
        .andExpect(status().isBadRequest())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Invalid Personality Selection"))
        .andExpect(jsonPath("$.detail").value("Unknown or inactive White personality: blaze"))
        .andExpect(jsonPath("$.code").value("INVALID_PERSONALITY_SELECTION"))
        .andExpect(jsonPath("$.message").value("Unknown or inactive White personality: blaze"));
  }

  private static MockHttpServletRequestBuilder ownerStartPost() {
    return ownerPost("/api/v1/match/start")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"whitePersonalityKey\":\"blaze\",\"blackPersonalityKey\":\"vesper\"}");
  }
}
