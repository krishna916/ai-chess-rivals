package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class DialogueRepositoryTestConfiguration {

  @Bean
  DialogueLineRepository dialogueLineRepository() {
    return Mockito.mock(DialogueLineRepository.class);
  }
}
