package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchConflictException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchEngineException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchNotFoundException;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = MatchController.class)
@Import(MatchControllerAdvice.class)
class MatchControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private MatchControlService matchControlService;

  @Test
  void startMatchReturns202AndMatchSnapshot() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.startMatch()).thenReturn(new MatchSnapshot(match, true));

    mockMvc
        .perform(post("/api/v1/match/start"))
        .andExpect(status().isAccepted())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.sideToMove").value("WHITE"))
        .andExpect(jsonPath("$.fen").value(match.currentPosition().fen()))
        .andExpect(jsonPath("$.running").value(true))
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
        .andExpect(jsonPath("$.result").value((String) null));
  }

  @Test
  void stopMatchReturns202AndMatchSnapshot() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.stopMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc
        .perform(post("/api/v1/match/stop"))
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
        .thenThrow(new MatchConflictException("Match is already running"));

    mockMvc
        .perform(post("/api/v1/match/start"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Conflict"))
        .andExpect(jsonPath("$.detail").value("Match is already running"));
  }

  @Test
  void stopMatchReturns409ProblemDetailOnConflict() throws Exception {
    when(matchControlService.stopMatch())
        .thenThrow(new MatchConflictException("No match is currently running"));

    mockMvc
        .perform(post("/api/v1/match/stop"))
        .andExpect(status().isConflict())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Conflict"))
        .andExpect(jsonPath("$.detail").value("No match is currently running"));
  }

  @Test
  void unexpectedEngineFailureReturns500ProblemDetail() throws Exception {
    when(matchControlService.startMatch())
        .thenThrow(
            new MatchEngineException("Failed to initialize a new match", new RuntimeException()));

    mockMvc
        .perform(post("/api/v1/match/start"))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.title").value("Match Engine Error"))
        .andExpect(jsonPath("$.detail").value("Failed to initialize a new match"));
  }
}
