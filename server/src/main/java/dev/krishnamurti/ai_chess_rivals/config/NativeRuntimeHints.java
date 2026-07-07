package dev.krishnamurti.ai_chess_rivals.config;

import dev.krishnamurti.ai_chess_rivals.chess.config.ChessProperties;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import java.util.UUID;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// Register UUID[] for reflection instantiation
		hints.reflection().registerType(UUID[].class);

		// Hibernate Validator's JPATraversableResolver reflectively reads fields of
		// validated @ConfigurationProperties objects to check JPA lazy-loading state.
		// Without these registrations, the native image throws MissingReflectionRegistrationError
		// for every field in ChessProperties and its nested Stockfish record.
		hints.reflection().registerType(ChessProperties.class, MemberCategory.DECLARED_FIELDS);
		hints.reflection().registerType(ChessProperties.Stockfish.class, MemberCategory.DECLARED_FIELDS);
	}

}
