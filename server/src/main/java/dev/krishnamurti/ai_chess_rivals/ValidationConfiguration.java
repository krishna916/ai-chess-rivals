package dev.krishnamurti.ai_chess_rivals;

import org.springframework.boot.validation.autoconfigure.ValidationConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps configuration-property validation independent of JPA persistence reachability.
 *
 * <p>The application currently uses Jakarta Bean Validation for configuration properties, not for
 * lazy JPA entity associations. Revisit this customization if entity validation is introduced.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationConfiguration {

  @Bean
  ValidationConfigurationCustomizer validationConfigurationCustomizer() {
    return configuration -> configuration.traversableResolver(AlwaysTraversableResolver.INSTANCE);
  }
}
