package dev.krishnamurti.ai_chess_rivals;

import dev.krishnamurti.ai_chess_rivals.chess.StockfishClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class AiChessRivalsApplicationTests {

  // Prevents the real StockfishEngine from starting — it requires a binary on disk.
  // Spring wiring (config binding, bean graph) is still fully exercised.
  @MockitoBean StockfishClient stockfishClient;

  @Test
  void contextLoads() {}
}
