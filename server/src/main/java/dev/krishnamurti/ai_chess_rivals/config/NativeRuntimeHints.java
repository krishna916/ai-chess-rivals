package dev.krishnamurti.ai_chess_rivals.config;

import java.util.UUID;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class NativeRuntimeHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		// Register UUID[] for reflection instantiation
		hints.reflection().registerType(UUID[].class);
	}

}
