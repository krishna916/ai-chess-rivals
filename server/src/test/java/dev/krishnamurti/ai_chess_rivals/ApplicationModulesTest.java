package dev.krishnamurti.ai_chess_rivals;

import static org.assertj.core.api.Assertions.assertThat;

import dev.krishnamurti.ai_chess_rivals.ai.api.AiChatGateway;
import dev.krishnamurti.ai_chess_rivals.ai.api.DialogueGenerator;
import dev.krishnamurti.ai_chess_rivals.ai.api.GeneratedDialogue;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ApplicationModulesTest {

  @Test
  void verifiesModuleStructure() {
    ApplicationModules.of(AiChessRivalsApplication.class).verify();
  }

  @Test
  void exposesAiApi() {
    ApplicationModules modules = ApplicationModules.of(AiChessRivalsApplication.class);
    var aiModule = modules.getModuleByName("ai").orElseThrow();
    var aiApi = aiModule.getNamedInterfaces().getByName("api").orElseThrow();

    assertThat(aiApi.contains(AiChatGateway.class)).isTrue();
    assertThat(aiApi.contains(DialogueGenerator.class)).isTrue();
    assertThat(aiApi.contains(GeneratedDialogue.class)).isTrue();
  }
}
