package com.algrex.campus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class CareWorkflowMigrationTest {
  @Test
  void v3PreservesAndBackfillsHistoricalCareWorkflowData() throws Exception {
    String url = "jdbc:h2:mem:care-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("2"))
        .load()
        .migrate();

    UUID studentA = UUID.fromString("90000000-0000-0000-0000-000000000001");
    UUID studentB = UUID.fromString("90000000-0000-0000-0000-000000000002");
    UUID studentC = UUID.fromString("90000000-0000-0000-0000-000000000003");
    UUID studentD = UUID.fromString("90000000-0000-0000-0000-000000000004");
    UUID lowAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000001");
    UUID highAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000002");
    UUID studentBAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000003");
    UUID studentCAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000004");
    UUID studentDAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000005");
    UUID alertA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    UUID alertB = UUID.fromString("a0000000-0000-0000-0000-000000000002");
    UUID alertC = UUID.fromString("a0000000-0000-0000-0000-000000000003");
    UUID alertD = UUID.fromString("a0000000-0000-0000-0000-000000000004");

    UUID aOld = UUID.fromString("10000000-0000-0000-0000-000000000001");
    UUID aNew = UUID.fromString("10000000-0000-0000-0000-000000000002");
    UUID bOld = UUID.fromString("20000000-0000-0000-0000-000000000001");
    UUID bNew = UUID.fromString("20000000-0000-0000-0000-000000000002");
    UUID cOnly = UUID.fromString("30000000-0000-0000-0000-000000000001");
    UUID dSmall = UUID.fromString("00000000-0000-0000-0000-000000000001");
    UUID dLarge = UUID.fromString("00000000-0000-0000-0000-000000000002");

    Map<UUID, LocalDate> originalNextDates = new LinkedHashMap<>();
    originalNextDates.put(aOld, LocalDate.of(2026, 1, 10));
    originalNextDates.put(aNew, LocalDate.of(2026, 1, 11));
    originalNextDates.put(bOld, LocalDate.of(2026, 2, 10));
    originalNextDates.put(bNew, null);
    originalNextDates.put(cOnly, LocalDate.of(2026, 3, 10));
    originalNextDates.put(dSmall, LocalDate.of(2026, 4, 10));
    originalNextDates.put(dLarge, null);

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      insertStudent(connection, studentA, "MIGRATION-A");
      insertStudent(connection, studentB, "MIGRATION-B");
      insertStudent(connection, studentC, "MIGRATION-C");
      insertStudent(connection, studentD, "MIGRATION-D");
      insertAssessment(
          connection,
          lowAssessmentId,
          studentA,
          "1,1,0,0,0,0,0,0,0",
          2,
          "LOW",
          LocalDateTime.of(2025, 12, 1, 9, 0),
          "migration-low");
      insertAssessment(
          connection,
          highAssessmentId,
          studentA,
          "2,2,2,2,2,2,2,2,0",
          16,
          "HIGH",
          LocalDateTime.of(2025, 12, 2, 9, 0),
          "migration-high");
      insertAssessment(
          connection,
          studentBAssessmentId,
          studentB,
          "2,2,2,2,2,2,2,2,0",
          16,
          "HIGH",
          LocalDateTime.of(2025, 12, 3, 9, 0),
          "migration-b");
      insertAssessment(
          connection,
          studentCAssessmentId,
          studentC,
          "2,2,2,2,2,2,2,2,0",
          16,
          "HIGH",
          LocalDateTime.of(2025, 12, 4, 9, 0),
          "migration-c");
      insertAssessment(
          connection,
          studentDAssessmentId,
          studentD,
          "2,2,2,2,2,2,2,2,0",
          16,
          "HIGH",
          LocalDateTime.of(2025, 12, 5, 9, 0),
          "migration-d");

      insertAlert(connection, alertA, studentA, "INTERVENING");
      insertAlert(connection, alertB, studentB, "INTERVENING");
      insertAlert(connection, alertC, studentC, "CLOSED");
      insertAlert(connection, alertD, studentD, "INTERVENING");

      insertIntervention(
          connection,
          aOld,
          alertA,
          originalNextDates.get(aOld),
          LocalDateTime.of(2026, 1, 1, 9, 0));
      insertIntervention(
          connection,
          aNew,
          alertA,
          originalNextDates.get(aNew),
          LocalDateTime.of(2026, 1, 2, 9, 0));
      insertIntervention(
          connection,
          bOld,
          alertB,
          originalNextDates.get(bOld),
          LocalDateTime.of(2026, 2, 1, 9, 0));
      insertIntervention(
          connection,
          bNew,
          alertB,
          originalNextDates.get(bNew),
          LocalDateTime.of(2026, 2, 2, 9, 0));
      insertIntervention(
          connection,
          cOnly,
          alertC,
          originalNextDates.get(cOnly),
          LocalDateTime.of(2026, 3, 1, 9, 0));
      LocalDateTime tiedCreatedAt = LocalDateTime.of(2026, 4, 1, 9, 0);
      insertIntervention(connection, dSmall, alertD, originalNextDates.get(dSmall), tiedCreatedAt);
      insertIntervention(connection, dLarge, alertD, originalNextDates.get(dLarge), tiedCreatedAt);
    }

    Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      assertEquals(4, count(connection, "student_profiles"));
      assertEquals(4, count(connection, "alerts"));
      assertEquals(5, count(connection, "assessments"));
      assertEquals(originalNextDates.size(), count(connection, "interventions"));
      for (Map.Entry<UUID, LocalDate> entry : originalNextDates.entrySet()) {
        assertEquals(
            entry.getValue(),
            interventionNextDate(connection, entry.getKey()),
            "Migration changed next_date for intervention " + entry.getKey());
        assertEquals(
            "historical intervention",
            value(
                connection,
                "SELECT content FROM interventions WHERE id=?",
                entry.getKey(),
                result -> result.getString(1)),
            "Migration changed content for intervention " + entry.getKey());
      }

      assertEquals("SUPERSEDED", interventionStatus(connection, aOld));
      assertEquals("PLANNED", interventionStatus(connection, aNew));
      assertEquals("SUPERSEDED", interventionStatus(connection, bOld));
      assertEquals("NONE", interventionStatus(connection, bNew));
      assertEquals("CANCELLED", interventionStatus(connection, cOnly));
      assertEquals("SUPERSEDED", interventionStatus(connection, dSmall));
      assertEquals("NONE", interventionStatus(connection, dLarge));

      Long lowSequence = assessmentSequence(connection, lowAssessmentId);
      Long highSequence = assessmentSequence(connection, highAssessmentId);
      assertNotNull(lowSequence);
      assertNotNull(highSequence);
      assertNotEquals(lowSequence, highSequence);
      assertEquals(5, assessmentSequenceCount(connection, false));
      assertEquals(5, assessmentSequenceCount(connection, true));
      assertEquals("LOW", assessmentRuleRisk(connection, lowAssessmentId));
      assertEquals("HIGH", assessmentRuleRisk(connection, highAssessmentId));
      assertEquals("HIGH", assessmentRuleRisk(connection, studentBAssessmentId));
      assertEquals("HIGH", assessmentRuleRisk(connection, studentCAssessmentId));
      assertEquals("HIGH", assessmentRuleRisk(connection, studentDAssessmentId));

      long maximumHistoricalSequence = maximumAssessmentSequence(connection);
      UUID newAssessmentId = UUID.fromString("91000000-0000-0000-0000-000000000006");
      insertV3Assessment(connection, newAssessmentId, studentA);
      Long newSequence = assessmentSequence(connection, newAssessmentId);
      assertNotNull(newSequence);
      assertTrue(newSequence > maximumHistoricalSequence);

      assertThrows(
          SQLException.class,
          () ->
              update(
                  connection,
                  "UPDATE interventions SET follow_up_status=? WHERE id=?",
                  "INVALID",
                  aNew));
      assertThrows(
          SQLException.class,
          () ->
              update(
                  connection,
                  "UPDATE alerts SET urgent_assessment_id=? WHERE id=?",
                  UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
                  alertA));
    }
  }

  private static void insertStudent(Connection connection, UUID studentId, String studentCode)
      throws SQLException {
    update(
        connection,
        "INSERT INTO student_profiles(id,student_code,display_name) VALUES(?,?,?)",
        studentId,
        studentCode,
        "Migration Student");
  }

  private static void insertAssessment(
      Connection connection,
      UUID id,
      UUID studentId,
      String answers,
      int score,
      String riskLevel,
      LocalDateTime createdAt,
      String requestId)
      throws SQLException {
    update(
        connection,
        "INSERT INTO assessments(id,student_id,answers,score,risk_level,created_at,request_id,source_name) VALUES(?,?,?,?,?,?,?,?)",
        id,
        studentId,
        answers,
        score,
        riskLevel,
        Timestamp.valueOf(createdAt),
        requestId,
        "migration-test");
  }

  private static void insertV3Assessment(Connection connection, UUID id, UUID studentId)
      throws SQLException {
    update(
        connection,
        "INSERT INTO assessments(id,student_id,answers,score,risk_level,created_at,request_id,source_name,rule_risk_level) VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        studentId,
        "0,0,0,0,0,0,0,0,0",
        0,
        "LOW",
        Timestamp.valueOf(LocalDateTime.of(2026, 5, 1, 9, 0)),
        "migration-v3",
        "migration-test",
        "LOW");
  }

  private static void insertAlert(Connection connection, UUID id, UUID studentId, String status)
      throws SQLException {
    update(
        connection,
        "INSERT INTO alerts(id,student_id,risk_level,status,reason,created_at,updated_at,assigned_to,close_reason) VALUES(?,?,?,?,?,?,?,?,?)",
        id,
        studentId,
        "HIGH",
        status,
        "migration test",
        Timestamp.valueOf(LocalDateTime.of(2025, 12, 20, 9, 0)),
        Timestamp.valueOf(LocalDateTime.of(2025, 12, 20, 9, 0)),
        "center",
        "CLOSED".equals(status) ? "migration closed case" : null);
  }

  private static void insertIntervention(
      Connection connection, UUID id, UUID alertId, LocalDate nextDate, LocalDateTime createdAt)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO interventions(id,alert_id,kind,content,next_date,author_name,created_at) VALUES(?,?,?,?,?,?,?)")) {
      statement.setObject(1, id);
      statement.setObject(2, alertId);
      statement.setString(3, "FOLLOW_UP");
      statement.setString(4, "historical intervention");
      if (nextDate == null) {
        statement.setNull(5, Types.DATE);
      } else {
        statement.setObject(5, nextDate);
      }
      statement.setString(6, "center");
      statement.setTimestamp(7, Timestamp.valueOf(createdAt));
      statement.executeUpdate();
    }
  }

  private static int count(Connection connection, String table) throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(*) FROM " + table);
        ResultSet result = statement.executeQuery()) {
      assertTrue(result.next());
      return result.getInt(1);
    }
  }

  private static LocalDate interventionNextDate(Connection connection, UUID id)
      throws SQLException {
    return value(
        connection,
        "SELECT next_date FROM interventions WHERE id=?",
        id,
        result -> result.getObject(1, LocalDate.class));
  }

  private static String interventionStatus(Connection connection, UUID id) throws SQLException {
    return value(
        connection,
        "SELECT follow_up_status FROM interventions WHERE id=?",
        id,
        result -> result.getString(1));
  }

  private static Long assessmentSequence(Connection connection, UUID id) throws SQLException {
    return value(
        connection,
        "SELECT sequence_no FROM assessments WHERE id=?",
        id,
        result -> result.getObject(1, Long.class));
  }

  private static long maximumAssessmentSequence(Connection connection) throws SQLException {
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT MAX(sequence_no) FROM assessments");
        ResultSet result = statement.executeQuery()) {
      assertTrue(result.next());
      return result.getLong(1);
    }
  }

  private static int assessmentSequenceCount(Connection connection, boolean distinct)
      throws SQLException {
    String expression = distinct ? "DISTINCT sequence_no" : "sequence_no";
    try (PreparedStatement statement =
            connection.prepareStatement("SELECT COUNT(" + expression + ") FROM assessments");
        ResultSet result = statement.executeQuery()) {
      assertTrue(result.next());
      return result.getInt(1);
    }
  }

  private static String assessmentRuleRisk(Connection connection, UUID id) throws SQLException {
    return value(
        connection,
        "SELECT rule_risk_level FROM assessments WHERE id=?",
        id,
        result -> result.getString(1));
  }

  private static <T> T value(Connection connection, String sql, UUID id, SqlResultMapper<T> mapper)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setObject(1, id);
      try (ResultSet result = statement.executeQuery()) {
        assertTrue(result.next(), "Expected one row for " + id);
        return mapper.map(result);
      }
    }
  }

  private static void update(Connection connection, String sql, Object... parameters)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      statement.executeUpdate();
    }
  }

  @FunctionalInterface
  private interface SqlResultMapper<T> {
    T map(ResultSet result) throws SQLException;
  }
}
