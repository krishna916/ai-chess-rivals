package dev.krishnamurti.ai_chess_rivals;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(AiChessRivalsApplication.class).verify();
  }
}
