package dev.krishnamurti.ai_chess_rivals.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.krishnamurti.ai_chess_rivals.ai.dialogue.DialogueRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.ai.personality.PersonalityRepositoryTestConfiguration;
import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    properties = {
      "app.owner.control-token=test-owner-token",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.modulith.events.config.EventPublicationAutoConfiguration,"
          + "org.springframework.modulith.events.jpa.JpaEventPublicationAutoConfiguration"
    })
@AutoConfigureMockMvc
@Import({PersonalityRepositoryTestConfiguration.class, DialogueRepositoryTestConfiguration.class})
class RequestTracingFilterIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StockfishClient stockfishClient;

  @Test
  void registersRequestTracingFilterInApplicationContext() throws Exception {
    mockMvc
        .perform(get("/api/v1/personalities").header("X-Request-ID", "integration-trace-001"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Request-ID", "integration-trace-001"));
  }
}
