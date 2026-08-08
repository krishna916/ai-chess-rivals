package dev.krishnamurti.ai_chess_rivals.ai.personality;

import static org.mockito.Mockito.mock;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class PersonalityRepositoryTestConfiguration {

  @Bean
  PersonalityRepository personalityRepository() {
    return mock(PersonalityRepository.class);
  }
}
