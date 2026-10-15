package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algrex.campus.demo.DemoAccounts;
import com.algrex.campus.portal.PortalController.AssessmentDraft;
import com.algrex.campus.portal.PortalController.CheckInDraft;
import com.algrex.campus.portal.PortalController.InterventionDraft;
import com.algrex.campus.portal.PortalService;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Transactional
class CareRulesIntegrationTest {
  @Autowired PortalService service;
  @Autowired DemoAccounts accounts;
  @Autowired JdbcTemplate jdbc;
  private final UUID studentId = DemoAccounts.studentId(0);

  @BeforeEach
  void isolateStudentRecordsWithinTheRolledBackTransaction() {
    jdbc.update(
        "DELETE FROM notifications WHERE alert_id IN (SELECT id FROM alerts WHERE student_id=?)",
        studentId);
    jdbc.update(
        "DELETE FROM interventions WHERE alert_id IN (SELECT id FROM alerts WHERE student_id=?)",
        studentId);
    for (String table : List.of("alerts", "assessments", "check_ins", "risk_snapshots")) {
      jdbc.update("DELETE FROM " + table + " WHERE student_id=?", studentId);
    }
  }

  private Map<String, Object> assess(int score, boolean urgent) {
    int ninth = urgent ? Math.max(1, score - 24) : 0;
    int remainder = score - ninth;
    List<Integer> answers = new ArrayList<>();
    for (int index = 0; index < 8; index++) {
      int value = Math.min(3, remainder);
      answers.add(value);
      remainder -= value;
    }
    answers.add(ninth);
    return service.assess(
        accounts.get("student"), new AssessmentDraft(answers, UUID.randomUUID().toString()));
  }

  private Map<String, Object> checkIn(int mood, String sleep, int stress) {
    return service.checkIn(
        accounts.get("student"), new CheckInDraft(mood, new BigDecimal(sleep), stress, "回归测试"));
  }

  @ParameterizedTest
  @CsvSource({
    "0,false,LOW",
    "9,false,LOW",
    "10,false,MEDIUM",
    "14,false,MEDIUM",
    "15,false,HIGH",
    "19,false,HIGH",
    "20,false,CRITICAL",
    "27,true,CRITICAL",
    "1,true,CRITICAL"
  })
  void scoreBoundariesAndUrgentAnswerPersistTheExpectedGrade(
      int score, boolean urgent, String level) {
    assertThat(assess(score, urgent))
        .containsEntry("score", score)
        .containsEntry("riskLevel", level);
    assertThat(service.student(accounts.get("center"), studentId))
        .containsEntry("currentScore", score)
        .containsEntry("riskLevel", level);
    assertThat(service.rows("SELECT * FROM risk_snapshots WHERE student_id=?", studentId))
        .hasSize(1);
    assertThat(
            service.alerts(accounts.get("center"), "").stream()
                .filter(row -> row.get("studentId").equals(studentId.toString()))
                .count())
        .isEqualTo(level.equals("LOW") ? 0 : 1);
  }

  @Test
  void poorSleepUsesSevenDayWindowAndStrictFiveHourBoundary() {
    assess(0, false);
    for (int offset : List.of(1, 6, 7)) {
      jdbc.update(
          "INSERT INTO check_ins VALUES(?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          studentId,
          3,
          new BigDecimal("4.5"),
          2,
          "历史测试",
          Date.valueOf(LocalDate.now().minusDays(offset)),
          "测试");
    }
    Map<String, Object> first = checkIn(3, "4.5", 2);
    assertThat(service.student(accounts.get("center"), studentId).get("riskLevel"))
        .isEqualTo("HIGH");
    jdbc.update(
        "UPDATE check_ins SET sleep_hours=5 WHERE student_id=? AND check_date=?",
        studentId,
        Date.valueOf(LocalDate.now().minusDays(6)));
    Map<String, Object> second = checkIn(4, "4.5", 2);
    assertThat(second.get("id")).isEqualTo(first.get("id"));
    assertThat(service.student(accounts.get("center"), studentId).get("riskLevel"))
        .isEqualTo("LOW");
    assertThat(service.rows("SELECT * FROM check_ins WHERE student_id=?", studentId)).hasSize(4);
  }

  @Test
  void moodStressEscalationDoesNotOverrideMoreUrgentAssessment() {
    assess(0, false);
    checkIn(2, "8", 4);
    assertThat(service.student(accounts.get("center"), studentId).get("riskLevel"))
        .isEqualTo("MEDIUM");
    checkIn(3, "8", 4);
    assertThat(service.student(accounts.get("center"), studentId).get("riskLevel"))
        .isEqualTo("LOW");
    assess(1, true);
    checkIn(5, "8", 1);
    assertThat(service.student(accounts.get("center"), studentId).get("riskLevel"))
        .isEqualTo("CRITICAL");
  }

  @Test
  void sameLevelSignalsDeduplicateButNewUrgentAssessmentsNotifyAgain() {
    assess(10, false);
    List<Map<String, Object>> alerts =
        service.rows("SELECT * FROM alerts WHERE student_id=?", studentId);
    UUID alertId = UUID.fromString(alerts.getFirst().get("id").toString());
    int initial = service.rows("SELECT * FROM notifications WHERE alert_id=?", alertId).size();
    assess(12, false);
    assertThat(service.rows("SELECT * FROM alerts WHERE student_id=?", studentId)).hasSize(1);
    assertThat(service.rows("SELECT * FROM notifications WHERE alert_id=?", alertId))
        .hasSize(initial);
    assess(1, true);
    assertThat(service.rows("SELECT * FROM alerts WHERE student_id=?", studentId).getFirst())
        .containsEntry("riskLevel", "CRITICAL");
    assertThat(
            service.rows(
                "SELECT * FROM notifications WHERE alert_id=? AND recipient='contact'", alertId))
        .hasSize(1);
    int escalated = service.rows("SELECT * FROM notifications WHERE alert_id=?", alertId).size();
    assess(2, true);
    assertThat(service.rows("SELECT * FROM notifications WHERE alert_id=?", alertId))
        .hasSize(escalated + 3);
    assertThat(service.rows("SELECT * FROM alerts WHERE student_id=?", studentId)).hasSize(1);
    assertThat(
            service.rows(
                "SELECT * FROM notifications WHERE alert_id=? AND recipient='contact'", alertId))
        .hasSize(2);
    assertThat(
            service
                .rows(
                    "SELECT content FROM notifications WHERE alert_id=? AND recipient='contact'",
                    alertId)
                .toString())
        .doesNotContain("answers", "sleepHours", "测评总分");
  }

  @Test
  void illegalTransitionsLeaveHistoryIntactAndClosedCasesCannotBeReopened() {
    assess(10, false);
    UUID alertId =
        UUID.fromString(
            service
                .rows("SELECT * FROM alerts WHERE student_id=?", studentId)
                .getFirst()
                .get("id")
                .toString());
    var staff = accounts.get("center");
    var record = new InterventionDraft("沟通访谈", "明确约定后续支持安排", LocalDate.now().plusDays(1));
    assertConflict(() -> service.transition(staff, alertId, "review", null));
    assertConflict(() -> service.intervention(staff, alertId, record));
    service.transition(staff, alertId, "accept", null);
    assertConflict(() -> service.transition(staff, alertId, "accept", null));
    service.intervention(staff, alertId, record);
    assertThat(service.followUps(staff))
        .anySatisfy(row -> assertThat(row).containsEntry("alertId", alertId.toString()));
    service.transition(staff, alertId, "review", null);
    assertThatThrownBy(() -> service.transition(staff, alertId, "close", "短"))
        .isInstanceOf(IllegalArgumentException.class);
    service.transition(staff, alertId, "close", "人工复评完成并保留持续支持");
    assertConflict(() -> service.intervention(staff, alertId, record));
    assertThat(service.rows("SELECT * FROM interventions WHERE alert_id=?", alertId)).hasSize(1);
    assertThat(service.student(staff, studentId).get("riskLevel")).isEqualTo("MEDIUM");
    assess(10, false);
    assertThat(service.rows("SELECT * FROM alerts WHERE student_id=?", studentId)).hasSize(2);
  }

  private void assertConflict(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
  }
}
