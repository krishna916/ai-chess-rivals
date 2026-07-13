package dev.krishnamurti.ai_chess_rivals.game.config;

import dev.krishnamurti.ai_chess_rivals.game.web.OwnerTokenInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.ObjectMapper;

@Configuration
class OwnerControlWebConfig implements WebMvcConfigurer {

  private final OwnerTokenInterceptor ownerTokenInterceptor;

  OwnerControlWebConfig(OwnerControlProperties ownerControlProperties, ObjectMapper objectMapper) {
    this.ownerTokenInterceptor = new OwnerTokenInterceptor(ownerControlProperties, objectMapper);
  }

  @Bean
  OwnerTokenInterceptor ownerTokenInterceptor() {
    return ownerTokenInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
        .addInterceptor(ownerTokenInterceptor)
        .addPathPatterns("/api/v1/match/start", "/api/v1/match/stop");
  }
}
