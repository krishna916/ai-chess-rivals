package dev.krishnamurti.ai_chess_rivals;

import dev.krishnamurti.ai_chess_rivals.chess.api.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
class AiChessRivalsApplicationTests {

  // Prevents the real StockfishEngine from starting — it requires a binary on disk.
  // Spring wiring (config binding, bean graph) is still fully exercised.
  @MockitoBean StockfishClient stockfishClient;

  @Test
  void contextLoads() {}
}
