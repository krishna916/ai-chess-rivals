package dev.krishnamurti.ai_chess_rivals.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

/** Activates configuration properties for the AI module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiProperties.class)
@ImportRuntimeHints(AiRuntimeHints.class)
public class AiConfig {}
