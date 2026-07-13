package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
@TestPropertySource(properties = "app.owner.control-token=test-owner-token")
class MatchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MatchControlService matchControlService;

  @Test
  void startMatchReturns202AndMatchSnapshot() throws Exception {
    Match match = Match.newGame();
    MatchStartAvailability availability =
        new MatchStartAvailability(false, MatchStartBlockReason.MATCH_ALREADY_RUNNING, 0, 3, 12);
    when(matchControlService.startMatch()).thenReturn(new MatchSnapshot(match, true, availability));

    mockMvc
        .perform(ownerPost("/api/v1/match/start"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
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
    Match match = Match.newGame();
    when(matchControlService.stopMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(ownerPost("/api/v1/match/stop"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.running").value(false));
  }

  @Test
  void currentMatchReturns200AndMatchSnapshot() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(get("/api/v1/match"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.running").value(false));
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
    when(matchControlService.startMatch())
        .thenThrow(new MatchConflictException("A match is already active."));

    mockMvc
        .perform(ownerPost("/api/v1/match/start"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Conflict"))
        .andExpect(jsonPath("$.detail").value("A match is already active."))
        .andExpect(jsonPath("$.code").value("MATCH_ALREADY_RUNNING"))
        .andExpect(jsonPath("$.message").value("A match is already active."));
  }

  @Test
  void startMatchReturns429AndRetryAfterDuringCooldown() throws Exception {
    when(matchControlService.startMatch()).thenThrow(new MatchCooldownException(60));

    mockMvc
        .perform(ownerPost("/api/v1/match/start"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().string("Retry-After", "60"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MATCH_COOLDOWN_ACTIVE"))
        .andExpect(jsonPath("$.message").value("A new match cannot be started yet."))
        .andExpect(jsonPath("$.retryAfterSeconds").value(60));
  }

  @Test
  void startMatchReturns429WhenDailyLimitIsReached() throws Exception {
    when(matchControlService.startMatch()).thenThrow(new MatchDailyLimitException(12));

    mockMvc
        .perform(ownerPost("/api/v1/match/start"))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().doesNotExist("Retry-After"))
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("MATCH_DAILY_LIMIT_REACHED"))
        .andExpect(
            jsonPath("$.message").value("The configured daily match limit has been reached."));
  }

  @Test
  void unexpectedEngineFailureReturns500ProblemDetail() throws Exception {
    when(matchControlService.startMatch())
        .thenThrow(
            new MatchEngineException("Failed to initialize a new match", new RuntimeException()));

    mockMvc
        .perform(ownerPost("/api/v1/match/start"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Engine Error"))
        .andExpect(jsonPath("$.detail").value("Failed to initialize a new match"));
  }

  private static MockHttpServletRequestBuilder ownerPost(String path) {
    return post(path).header(HttpHeaders.AUTHORIZATION, "Bearer test-owner-token");
  }
}
