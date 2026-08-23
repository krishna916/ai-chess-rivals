package dev.krishnamurti.ai_chess_rivals.ai.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class AiResponseSourceMigrationPostgresIT {

  private static final String ADMIN_URL = "jdbc:postgresql://localhost:55433/postgres";
  private static final String USER = "postgres";
  private static final String PASSWORD = "secretpassword";

  @Test
  void migratesLegacyProviderSourcesToProviderNeutralValues() throws Exception {
    String database = "aichessrivals_migration_" + UUID.randomUUID().toString().replace("-", "");
    String databaseUrl = "jdbc:postgresql://localhost:55433/" + database;

    try {
      createDatabase(database);
      migrate(databaseUrl, MigrationVersion.fromVersion("4"));
      seedLegacyRows(databaseUrl);

      migrate(databaseUrl, MigrationVersion.LATEST);

      assertEquals(List.of("REMOTE_PRIMARY", "REMOTE_FALLBACK"), readResponseSources(databaseUrl));
    } finally {
      dropDatabase(database);
    }
  }

  private static void createDatabase(String database) throws Exception {
    try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE \"" + database + "\"");
    }
  }

  private static void migrate(String databaseUrl, MigrationVersion target) {
    Flyway.configure().dataSource(databaseUrl, USER, PASSWORD).target(target).load().migrate();
  }

  private static void seedLegacyRows(String databaseUrl) throws Exception {
    try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute(
          "INSERT INTO dialogue_line "
              + "(match_id, trigger_type, trigger_ply, personality_key, "
              + "personality_display_name, dialogue_text, emotion, reaction_type, "
              + "response_source, created_at) VALUES "
              + "('00000000-0000-0000-0000-000000000001', 'MOVE', 1, 'blaze', 'Blaze', "
              + "'legacy primary', 'CONFIDENT', 'MOVE_REACTION', 'GROQ', CURRENT_TIMESTAMP), "
              + "('00000000-0000-0000-0000-000000000001', 'MOVE', 2, 'vesper', 'Vesper', "
              + "'legacy fallback', 'CONFIDENT', 'MOVE_REACTION', 'GEMINI', CURRENT_TIMESTAMP)");
    }
  }

  private static List<String> readResponseSources(String databaseUrl) throws Exception {
    try (Connection connection = DriverManager.getConnection(databaseUrl, USER, PASSWORD);
        Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery("SELECT response_source FROM dialogue_line ORDER BY id")) {
      java.util.ArrayList<String> sources = new java.util.ArrayList<>();
      while (resultSet.next()) {
        sources.add(resultSet.getString(1));
      }
      return sources;
    }
  }

  private static void dropDatabase(String database) throws Exception {
    try (Connection connection = DriverManager.getConnection(ADMIN_URL, USER, PASSWORD);
        Statement statement = connection.createStatement()) {
      statement.execute("DROP DATABASE IF EXISTS \"" + database + "\" WITH (FORCE)");
    }
  }
}
