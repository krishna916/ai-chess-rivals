package dev.krishnamurti.ai_chess_rivals.game.web;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import dev.krishnamurti.ai_chess_rivals.game.application.MatchControlService;
import dev.krishnamurti.ai_chess_rivals.game.application.MatchSnapshot;
import dev.krishnamurti.ai_chess_rivals.game.config.OwnerControlProperties;
import dev.krishnamurti.ai_chess_rivals.game.domain.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

class OwnerTokenInterceptorTest {

  private MatchControlService matchControlService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    matchControlService = mock(MatchControlService.class);
    OwnerTokenInterceptor interceptor =
        new OwnerTokenInterceptor(
            new OwnerControlProperties("test-owner-token"),
            JsonMapper.builder()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build());
    mockMvc =
        MockMvcBuilders.standaloneSetup(new MatchController(matchControlService))
            .setControllerAdvice(new MatchControllerAdvice())
            .addInterceptors(interceptor)
            .build();
  }

  @Test
  void startWithoutHeaderReturnsUniformUnauthorizedResponse() throws Exception {
    assertUnauthorized(post("/api/v1/match/start"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void stopWithoutHeaderReturnsUniformUnauthorizedResponse() throws Exception {
    assertUnauthorized(post("/api/v1/match/stop"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void invalidTokenReturnsUniformUnauthorizedResponse() throws Exception {
    assertUnauthorized(
        post("/api/v1/match/start").header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void malformedSchemeReturnsUniformUnauthorizedResponse() throws Exception {
    assertUnauthorized(
        post("/api/v1/match/start").header(HttpHeaders.AUTHORIZATION, "Basic test-owner-token"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void emptyBearerTokenReturnsUniformUnauthorizedResponse() throws Exception {
    assertUnauthorized(post("/api/v1/match/start").header(HttpHeaders.AUTHORIZATION, "Bearer "));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void validTokenReachesController() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.startMatch()).thenReturn(new MatchSnapshot(match, true));

    mockMvc
        .perform(
            post("/api/v1/match/start")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-owner-token"))
        .andExpect(status().isAccepted());

    verify(matchControlService).startMatch();
  }

  @Test
  void matrixParametersCannotBypassOwnerAuthentication() throws Exception {
    assertUnauthorized(post("/api/v1/match/start;ignored=value"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void contextPathCannotBypassOwnerAuthentication() throws Exception {
    assertUnauthorized(post("/showcase/api/v1/match/start").contextPath("/showcase"));
    verifyNoInteractions(matchControlService);
  }

  @Test
  void publicGetDoesNotRequireToken() throws Exception {
    Match match = Match.newGame();
    when(matchControlService.currentMatch()).thenReturn(new MatchSnapshot(match, false));

    mockMvc.perform(get("/api/v1/match")).andExpect(status().isOk());

    verify(matchControlService).currentMatch();
  }

  private void assertUnauthorized(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
      throws Exception {
    mockMvc
        .perform(request)
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("OWNER_AUTH_REQUIRED"))
        .andExpect(jsonPath("$.message").value("A valid owner token is required."));
  }
}
