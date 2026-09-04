package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.krishnamurti.ai_chess_rivals.game.config.OwnerControlProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = PersonalityController.class)
@EnableConfigurationProperties(OwnerControlProperties.class)
@TestPropertySource(
    properties = {
      "app.owner.control-token=test-owner-token",
      "APP_WEBSOCKET_ALLOWED_ORIGIN=https://ai-chess.krishnamurti.dev"
    })
class PersonalityControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PersonalityService personalityService;

  @Test
  void listsSelectablePersonalitiesWithPublicRosterShapeOnly() throws Exception {
    when(personalityService.listSelectable())
        .thenReturn(
            List.of(
                new PersonalityRosterItem(
                    "alpha", "Alpha", "Alpha description", "/avatars/alpha.svg"),
                new PersonalityRosterItem("zeta", "Zeta", "Zeta description", null)));

    mockMvc
        .perform(get("/api/v1/personalities"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].key").value("alpha"))
        .andExpect(jsonPath("$[0].displayName").value("Alpha"))
        .andExpect(jsonPath("$[0].description").value("Alpha description"))
        .andExpect(jsonPath("$[0].avatarRef").value("/avatars/alpha.svg"))
        .andExpect(jsonPath("$[0].id").doesNotExist())
        .andExpect(jsonPath("$[0].promptTraits").doesNotExist())
        .andExpect(jsonPath("$[0].speakingProbability").doesNotExist())
        .andExpect(jsonPath("$[0].styleGuidance").doesNotExist())
        .andExpect(jsonPath("$[0].boundaryGuidance").doesNotExist())
        .andExpect(jsonPath("$[0].displayOrder").doesNotExist())
        .andExpect(jsonPath("$[0].system").doesNotExist())
        .andExpect(jsonPath("$[0].active").doesNotExist())
        .andExpect(jsonPath("$[1].key").value("zeta"))
        .andExpect(jsonPath("$[1].avatarRef").value((String) null));
  }

  @Test
  void returnsEmptyArrayWhenNoSelectablePersonalitiesExist() throws Exception {
    when(personalityService.listSelectable()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/personalities"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(content().json("[]"));
  }

  @Test
  void listPersonalitiesAllowsConfiguredFrontendOrigin() throws Exception {
    when(personalityService.listSelectable()).thenReturn(List.of());

    mockMvc
        .perform(
            get("/api/v1/personalities")
                .header(HttpHeaders.ORIGIN, "https://ai-chess.krishnamurti.dev"))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://ai-chess.krishnamurti.dev"));
  }

  @Test
  void doesNotExposeMutationEndpoints() throws Exception {
    List<MockHttpServletRequestBuilder> mutationRequests =
        List.of(
            post("/api/v1/personalities"),
            put("/api/v1/personalities"),
            patch("/api/v1/personalities"),
            delete("/api/v1/personalities"));

    for (MockHttpServletRequestBuilder request : mutationRequests) {
      mockMvc.perform(request).andExpect(status().isMethodNotAllowed());
    }
  }
}
