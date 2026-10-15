package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algrex.campus.demo.DemoAccounts.Account;
import com.algrex.campus.portal.PortalController.AssessmentDraft;
import com.algrex.campus.portal.PortalController.CheckInDraft;
import com.algrex.campus.portal.PortalController.InterventionDraft;
import com.algrex.campus.portal.PortalController.StudentDraft;
import com.algrex.campus.portal.PortalService;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:portal-rules-test;DB_CLOSE_DELAY=-1",
      "app.demo.enabled=false"
    })
@Transactional
class PortalServiceRegressionTest {
  @Autowired private PortalService service;
  @Autowired private JdbcTemplate jdbc;

  private final Account center = new Account("center", "沈老师", "CENTER", "", "");
  private final Account counselor = new Account("counselor", "陈老师", "COUNSELOR", "", "计算机学院");
  private Account student;
  private UUID studentId;

  @BeforeEach
  void createIsolatedStudent() {
    studentId = createStudent("计算机学院");
    student = new Account("test-student", "测试学生", "STUDENT", studentId.toString(), "计算机学院");
  }

  @ParameterizedTest
  @CsvSource({
    "0, false, LOW",
    "9, false, LOW",
    "10, false, MEDIUM",
    "14, false, MEDIUM",
    "15, false, HIGH",
    "19, false, HIGH",
    "20, false, CRITICAL",
    "27, false, CRITICAL",
    "0, true, CRITICAL",
    "9, true, CRITICAL",
    "15, true, CRITICAL",
    "20, true, CRITICAL"
  })
  void scoreLevelIncludesEveryThresholdAndUrgentAnswerOverride(
      int score, boolean urgentAnswer, String expected) {
    assertThat(PortalService.scoreLevel(score, urgentAnswer)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "0, 0, LOW",
    "9, 0, LOW",
    "10, 0, MEDIUM",
    "14, 0, MEDIUM",
    "15, 0, HIGH",
    "19, 0, HIGH",
    "20, 0, CRITICAL",
    "24, 0, CRITICAL",
    "1, 1, CRITICAL",
    "2, 2, CRITICAL",
    "3, 3, CRITICAL",
    "27, 3, CRITICAL"
  })
  void submittedAssessmentSumsAllAnswersAndPersistsBothRiskLevels(
      int score, int ninthAnswer, String expected) {
    Map<String, Object> result = service.assess(student, assessment(score, ninthAnswer));
    assertThat(result)
        .containsEntry("score", score)
        .containsEntry("riskLevel", expected)
        .containsEntry("ruleRiskLevel", expected);
    assertThat(service.student(center, studentId))
        .containsEntry("currentScore", score)
        .containsEntry("riskLevel", expected);
    assertThat(service.rows("SELECT * FROM risk_snapshots WHERE student_id=?", studentId))
        .singleElement()
        .satisfies(
            row ->
                assertThat(row).containsEntry("score", score).containsEntry("riskLevel", expected));
  }

  @ParameterizedTest
  @CsvSource({"0, 2, 4, MEDIUM", "0, 3, 4, LOW", "0, 2, 3, LOW", "6, 2, 4, MEDIUM", "7, 2, 4, LOW"})
  void recentMoodAndStressRuleHonorsBothBoundariesAndTheSevenDayWindow(
      int daysAgo, int mood, int stress, String expected) {
    seedCheckIn(studentId, daysAgo, mood, "7.0", stress);
    Map<String, Object> result = service.assess(student, assessment(0, 0));
    assertThat(result).containsEntry("riskLevel", "LOW").containsEntry("ruleRiskLevel", expected);
    assertThat(service.student(center, studentId)).containsEntry("riskLevel", expected);
  }

  @Test
  void aNewerHealthyCheckInReplacesTheOlderMoodSignal() {
    seedCheckIn(studentId, 1, 2, "7.0", 4);
    seedCheckIn(studentId, 0, 4, "7.0", 2);
    assertThat(service.assess(student, assessment(0, 0)))
        .containsEntry("riskLevel", "LOW")
        .containsEntry("ruleRiskLevel", "LOW");
  }

  @ParameterizedTest
  @MethodSource("sleepScenarios")
  void sleepRuleCountsOnlyThreeNightsBelowFiveHoursWithinSevenDays(
      List<Integer> daysAgo, String hours, String expected) {
    for (int offset : daysAgo) {
      seedCheckIn(studentId, offset, 4, hours, 2);
    }
    Map<String, Object> result = service.assess(student, assessment(0, 0));
    assertThat(result).containsEntry("riskLevel", "LOW").containsEntry("ruleRiskLevel", expected);
    assertThat(service.student(center, studentId)).containsEntry("riskLevel", expected);
  }

  static Stream<Arguments> sleepScenarios() {
    return Stream.of(
        Arguments.of(List.of(0, 1), "4.9", "LOW"),
        Arguments.of(List.of(0, 1, 6), "4.9", "HIGH"),
        Arguments.of(List.of(0, 1, 7), "4.9", "LOW"),
        Arguments.of(List.of(0, 1, 2), "5.0", "LOW"));
  }

  @Test
  void sleepRuleCanRaiseAMediumAssessmentToHigh() {
    for (int daysAgo : List.of(0, 1, 2)) {
      seedCheckIn(studentId, daysAgo, 4, "4.9", 2);
    }
    assertThat(service.assess(student, assessment(10, 0)))
        .containsEntry("riskLevel", "MEDIUM")
        .containsEntry("ruleRiskLevel", "HIGH");
    assertThat(service.student(center, studentId))
        .containsEntry("currentScore", 10)
        .containsEntry("riskLevel", "HIGH");
  }

  @ParameterizedTest
  @CsvSource({"15, 0, HIGH", "20, 0, CRITICAL", "1, 1, CRITICAL"})
  void checkInRulesNeverDowngradeAnAlreadyHigherAssessment(
      int score, int ninthAnswer, String expected) {
    for (int daysAgo : List.of(0, 1, 2)) {
      seedCheckIn(studentId, daysAgo, 2, "4.0", 5);
    }
    assertThat(service.assess(student, assessment(score, ninthAnswer)))
        .containsEntry("riskLevel", expected)
        .containsEntry("ruleRiskLevel", expected);
  }

  @Test
  void assessmentRetryKeepsItsOriginalCombinedLevelAndDoesNotEvaluateAgain() {
    service.checkIn(student, checkIn(2, "7.0", 4, "低落且压力较高"));
    AssessmentDraft draft = assessment(0, 0);
    Map<String, Object> submitted = service.assess(student, draft);
    assertThat(submitted)
        .containsEntry("riskLevel", "LOW")
        .containsEntry("ruleRiskLevel", "MEDIUM");
    service.checkIn(student, checkIn(4, "7.0", 2, "沟通后状态平稳"));
    assertThat(service.student(center, studentId)).containsEntry("riskLevel", "LOW");
    int snapshots = count("risk_snapshots");
    Map<String, Object> retry = service.assess(student, draft);
    assertThat(retry)
        .containsEntry("id", submitted.get("id"))
        .containsEntry("riskLevel", "LOW")
        .containsEntry("ruleRiskLevel", "MEDIUM");
    assertThat(count("assessments")).isEqualTo(1);
    assertThat(count("risk_snapshots")).isEqualTo(snapshots);
  }

  @Test
  void assessmentKeysCannotBeReusedForDifferentAnswersOrAnotherStudent() {
    AssessmentDraft original = assessment(10, 0);
    service.assess(student, original);
    int snapshots = count("risk_snapshots");
    AssessmentDraft changed =
        new AssessmentDraft(assessment(15, 0).answers(), original.requestId());
    assertThatThrownBy(() -> service.assess(student, changed))
        .isInstanceOfSatisfying(
            ResponseStatusException.class,
            exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    UUID otherId = createStudent("计算机学院");
    Account other = new Account("other", "其他学生", "STUDENT", otherId.toString(), "计算机学院");
    assertThatThrownBy(() -> service.assess(other, original))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(count("assessments")).isEqualTo(1);
    assertThat(count("risk_snapshots")).isEqualTo(snapshots);
    assertThat(service.rows("SELECT * FROM assessments WHERE student_id=?", otherId)).isEmpty();
  }

  @Test
  void sameLevelSignalsDeduplicateAlertsButANewUrgentAssessmentRefreshesTheCase() {
    service.assess(student, assessment(20, 0));
    Map<String, Object> initial = onlyActiveAlert();
    UUID alertId = UUID.fromString(initial.get("id").toString());
    int notifications = notificationCount(alertId);
    assertThat(notifications).isEqualTo(2);
    service.assess(student, assessment(20, 0));
    service.checkIn(student, checkIn(3, "7.0", 2, "同级信号"));
    assertThat(onlyActiveAlert()).containsEntry("id", alertId.toString());
    assertThat(count("alerts")).isEqualTo(1);
    assertThat(notificationCount(alertId)).isEqualTo(notifications);
    service.transition(counselor, alertId, "accept", null);
    service.intervention(center, alertId, new InterventionDraft("沟通访谈", "已经安排支持与沟通", null));
    service.transition(center, alertId, "review", null);

    AssessmentDraft urgent = assessment(1, 1);
    Map<String, Object> urgentAssessment = service.assess(student, urgent);
    Map<String, Object> refreshed = onlyActiveAlert();
    assertThat(refreshed)
        .containsEntry("id", alertId.toString())
        .containsEntry("riskLevel", "CRITICAL")
        .containsEntry("status", "ACCEPTED")
        .containsEntry("assignedTo", "陈老师")
        .containsEntry("urgentAssessmentId", urgentAssessment.get("id"));
    assertThat(refreshed.get("reason")).isNotEqualTo(initial.get("reason"));
    assertThat(notificationCount(alertId)).isEqualTo(notifications + 2);
    int snapshots = count("risk_snapshots");
    service.assess(student, urgent);
    assertThat(notificationCount(alertId)).isEqualTo(notifications + 2);
    assertThat(count("risk_snapshots")).isEqualTo(snapshots);

    service.assess(student, assessment(1, 1));
    assertThat(notificationCount(alertId)).isEqualTo(notifications + 4);
    assertThat(count("alerts")).isEqualTo(1);
  }

  @Test
  void closedCasesDoNotPreventANewSignalFromOpeningANewCase() {
    service.assess(student, assessment(10, 0));
    UUID firstId = UUID.fromString(onlyActiveAlert().get("id").toString());
    service.transition(center, firstId, "accept", null);
    service.intervention(center, firstId, new InterventionDraft("沟通访谈", "已经安排支持与沟通", null));
    service.transition(center, firstId, "review", null);
    service.transition(center, firstId, "close", "人工复评完成，恢复日常支持");
    service.assess(student, assessment(10, 0));
    assertThat(onlyActiveAlert().get("id")).isNotEqualTo(firstId.toString());
    assertThat(count("alerts")).isEqualTo(2);
    Object closedAlert = service.alertDetail(center, firstId).get("alert");
    assertThat(closedAlert).isInstanceOf(Map.class);
    assertThat(((Map<?, ?>) closedAlert).get("status")).isEqualTo("CLOSED");
  }

  @Test
  void followUpHistoryPreservesPlansWhileTheDashboardShowsOnlyCurrentCollegePlans() {
    service.assess(student, assessment(10, 0));
    UUID alertId = UUID.fromString(onlyActiveAlert().get("id").toString());
    service.transition(counselor, alertId, "accept", null);
    Map<String, Object> withoutDate =
        service.intervention(center, alertId, new InterventionDraft("沟通访谈", "记录不安排后续日期", null));
    Map<String, Object> oldPlan =
        service.intervention(
            center,
            alertId,
            new InterventionDraft("沟通访谈", "第一次安排后续沟通", LocalDate.now().plusDays(1)));
    Map<String, Object> currentPlan =
        service.intervention(
            center,
            alertId,
            new InterventionDraft("沟通访谈", "更新下一次沟通日期", LocalDate.now().plusDays(2)));
    UUID otherStudentId = createStudent("经济管理学院");
    Account otherStudent =
        new Account("other-student", "其他学院学生", "STUDENT", otherStudentId.toString(), "经济管理学院");
    service.assess(otherStudent, assessment(15, 0));
    UUID otherAlertId =
        UUID.fromString(
            service.alerts(center, "").stream()
                .filter(row -> row.get("studentId").equals(otherStudentId.toString()))
                .findFirst()
                .orElseThrow()
                .get("id")
                .toString());
    service.transition(center, otherAlertId, "accept", null);
    service.intervention(
        center,
        otherAlertId,
        new InterventionDraft("沟通访谈", "其他学院下一次沟通", LocalDate.now().plusDays(1)));

    List<Map<String, Object>> history = service.followUps(counselor);
    assertThat(history).hasSize(3);
    assertThat(history).allSatisfy(row -> assertThat(row).containsEntry("college", "计算机学院"));
    assertThat(find(history, withoutDate.get("id"))).containsEntry("followUpStatus", "NONE");
    assertThat(find(history, oldPlan.get("id"))).containsEntry("followUpStatus", "SUPERSEDED");
    assertThat(find(history, currentPlan.get("id"))).containsEntry("followUpStatus", "PLANNED");
    assertThat(service.followUps(center)).hasSize(4);
    assertThat(dashboardFollowUps(counselor))
        .singleElement()
        .satisfies(row -> assertThat(row).containsEntry("id", currentPlan.get("id")));
    assertThat(dashboardFollowUps(center)).hasSize(2);
    assertThatThrownBy(() -> service.followUps(student)).isInstanceOf(AccessDeniedException.class);
    assertThatThrownBy(() -> service.alertDetail(counselor, otherAlertId))
        .isInstanceOf(AccessDeniedException.class);

    Map<String, Object> cancelled =
        service.intervention(center, alertId, new InterventionDraft("沟通访谈", "本次取消后续日期安排", null));
    assertThat(find(service.followUps(counselor), currentPlan.get("id")))
        .containsEntry("followUpStatus", "SUPERSEDED");
    assertThat(cancelled).containsEntry("followUpStatus", "NONE");
    assertThat(dashboardFollowUps(counselor)).isEmpty();

    service.intervention(
        center, alertId, new InterventionDraft("沟通访谈", "关闭前安排后续沟通", LocalDate.now().plusDays(3)));
    service.transition(center, alertId, "review", null);
    service.transition(center, alertId, "close", "人工复评完成，后续日常支持");
    assertThat(dashboardFollowUps(counselor)).isEmpty();
    assertThat(service.followUps(counselor))
        .noneSatisfy(row -> assertThat(row).containsEntry("followUpStatus", "PLANNED"));
  }

  private UUID createStudent(String college) {
    Map<String, Object> result =
        service.saveStudent(
            center,
            null,
            new StudentDraft(
                "TEST-" + UUID.randomUUID(), "测试学生", college, "测试专业", "大一", "女", 20, "演示联系人"));
    return UUID.fromString(result.get("id").toString());
  }

  private AssessmentDraft assessment(int score, int ninthAnswer) {
    List<Integer> answers = new ArrayList<>();
    int remaining = score - ninthAnswer;
    for (int index = 0; index < 8; index++) {
      int answer = Math.min(3, remaining);
      answers.add(answer);
      remaining -= answer;
    }
    assertThat(remaining).isZero();
    answers.add(ninthAnswer);
    return new AssessmentDraft(answers, UUID.randomUUID().toString());
  }

  private CheckInDraft checkIn(int mood, String hours, int stress, String note) {
    return new CheckInDraft(mood, new BigDecimal(hours), stress, note);
  }

  private void seedCheckIn(UUID id, int daysAgo, int mood, String hours, int stress) {
    jdbc.update(
        "INSERT INTO check_ins(id,student_id,mood,sleep_hours,stress,note,check_date,source_name) VALUES(?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        mood,
        new BigDecimal(hours),
        stress,
        "测试前置打卡",
        Date.valueOf(LocalDate.now().minusDays(daysAgo)),
        "自动化测试");
  }

  private int count(String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE student_id=?", Integer.class, studentId);
  }

  private int notificationCount(UUID alertId) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM notifications WHERE alert_id=?", Integer.class, alertId);
  }

  private Map<String, Object> onlyActiveAlert() {
    List<Map<String, Object>> open =
        service.alerts(center, "").stream()
            .filter(row -> row.get("studentId").equals(studentId.toString()))
            .filter(row -> !row.get("status").equals("CLOSED"))
            .toList();
    assertThat(open).hasSize(1);
    return open.getFirst();
  }

  private Map<String, Object> find(List<Map<String, Object>> rows, Object id) {
    return rows.stream().filter(row -> row.get("id").equals(id)).findFirst().orElseThrow();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> dashboardFollowUps(Account account) {
    return (List<Map<String, Object>>) service.dashboard(account).get("followUps");
  }
}
