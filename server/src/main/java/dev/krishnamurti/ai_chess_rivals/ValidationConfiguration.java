package dev.krishnamurti.ai_chess_rivals;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Provides the validator used directly by Spring Boot configuration-properties binding.
 *
 * <p>Configuration properties are ordinary application configuration, not lazy JPA entities, so
 * validation must not consult persistence reachability. Revisit this boundary if configuration
 * validation is ever replaced with entity validation.
 */
@Configuration(proxyBeanMethods = false)
public class ValidationConfiguration {

  @Bean(name = EnableConfigurationProperties.VALIDATOR_BEAN_NAME)
  static LocalValidatorFactoryBean configurationPropertiesValidator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setTraversableResolver(AlwaysTraversableResolver.INSTANCE);
    return validator;
  }
}
