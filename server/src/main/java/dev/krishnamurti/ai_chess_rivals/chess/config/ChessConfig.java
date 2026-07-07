package dev.krishnamurti.ai_chess_rivals.chess.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Activates configuration properties for the chess module. */
@Configuration
@EnableConfigurationProperties(ChessProperties.class)
public class ChessConfig {}
