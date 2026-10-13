package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;

import com.algrex.campus.demo.DemoAccounts;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:h2:mem:workflow-test;DB_CLOSE_DELAY=-1")
class DemoWorkflowIntegrationTest {
  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  private final JsonMapper json = JsonMapper.builder().build();

  @BeforeEach
  void resetDemoStudent() {
    UUID id = DemoAccounts.studentId(0);
    jdbc.update(
        "DELETE FROM notifications WHERE alert_id IN (SELECT id FROM alerts WHERE student_id=?) OR recipient='contact'",
        id);
    jdbc.update(
        "DELETE FROM interventions WHERE alert_id IN (SELECT id FROM alerts WHERE student_id=?)",
        id);
    jdbc.update("DELETE FROM alerts WHERE student_id=?", id);
    jdbc.update("DELETE FROM risk_snapshots WHERE student_id=?", id);
    jdbc.update("DELETE FROM check_ins WHERE student_id=?", id);
    jdbc.update("DELETE FROM assessments WHERE student_id=?", id);
    jdbc.update(
        "UPDATE student_profiles SET college='计算机学院',risk_level='LOW',current_score=0 WHERE id=?",
        id);
  }

  private class Browser implements AutoCloseable {
    final HttpClient client =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String csrf;

    HttpResponse<String> request(
        String path, String method, String body, boolean token, boolean form) throws Exception {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
              .timeout(Duration.ofSeconds(15));
      if (token) {
        request.header("X-XSRF-TOKEN", csrf);
      }
      if (body != null) {
        request.header(
            "Content-Type", form ? "application/x-www-form-urlencoded" : "application/json");
      }
      return client.send(
          request
              .method(
                  method,
                  body == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(body))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    }

    JsonNode get(String path) throws Exception {
      HttpResponse<String> response = request(path, "GET", null, false, false);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      return json.readTree(response.body());
    }

    HttpResponse<String> post(String path, Object value) throws Exception {
      return request(path, "POST", json.writeValueAsString(value), true, false);
    }

    JsonNode ok(String path, Object value) throws Exception {
      HttpResponse<String> response = post(path, value);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      return json.readTree(response.body());
    }

    void login(String username) throws Exception {
      csrf = get("/auth/session").get("csrfToken").asText();
      HttpResponse<String> response =
          request(
              "/auth/login",
              "POST",
              "username=" + username + "&password=Campus2026%21",
              true,
              true);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      JsonNode session = get("/auth/session");
      csrf = session.get("csrfToken").asText();
      assertThat(session.get("user").get("username").asText()).isEqualTo(username);
    }

    @Override
    public void close() {
      client.close();
    }
  }

  @Test
  void assessmentCheckInAndCareCasePersistThroughAnAuthenticatedWorkflow() throws Exception {
    try (Browser student = new Browser();
        Browser staff = new Browser();
        Browser contact = new Browser()) {
      student.login("student");
      staff.login("center");
      contact.login("contact");
      Map<String, Object> draft =
          Map.of(
              "answers",
              List.of(2, 2, 2, 2, 2, 2, 2, 2, 1),
              "requestId",
              UUID.randomUUID().toString());
      assertThat(
              student
                  .request("/assessments", "POST", json.writeValueAsString(draft), false, false)
                  .statusCode())
          .isEqualTo(403);
      JsonNode assessment = student.ok("/assessments", draft);
      assertThat(assessment.get("riskLevel").asText()).isEqualTo("CRITICAL");
      assertThat(assessment.get("ruleRiskLevel").asText()).isEqualTo("CRITICAL");
      assertThat(assessment.get("score").asInt()).isEqualTo(17);
      int snapshots = count("risk_snapshots");
      assertThat(student.ok("/assessments", draft).get("id").asText())
          .isEqualTo(assessment.get("id").asText());
      assertThat(count("assessments")).isEqualTo(1);
      assertThat(count("risk_snapshots")).isEqualTo(snapshots);
      Map<String, Object> checkIn =
          Map.of("mood", 3, "sleepHours", 7.5, "stress", 2, "note", "integration demo");
      JsonNode first = student.ok("/check-ins", checkIn);
      snapshots = count("risk_snapshots");
      JsonNode second = student.ok("/check-ins", checkIn);
      assertThat(first.get("id").asText()).isEqualTo(second.get("id").asText());
      assertThat(count("check_ins")).isEqualTo(1);
      assertThat(count("risk_snapshots")).isEqualTo(snapshots);
      JsonNode changed =
          student.ok(
              "/check-ins",
              Map.of("mood", 4, "sleepHours", 8, "stress", 1, "note", "updated demo"));
      assertThat(changed.get("id").asText()).isEqualTo(first.get("id").asText());
      assertThat(changed.get("mood").asInt()).isEqualTo(4);
      assertThat(changed.get("sleepHours").asDouble()).isEqualTo(8);
      assertThat(changed.get("stress").asInt()).isEqualTo(1);
      assertThat(changed.get("note").asText()).isEqualTo("updated demo");
      assertThat(count("risk_snapshots")).isEqualTo(snapshots + 1);
      JsonNode noteOnly =
          student.ok(
              "/check-ins",
              Map.of("mood", 4, "sleepHours", 8, "stress", 1, "note", "note-only update"));
      assertThat(noteOnly.get("id").asText()).isEqualTo(first.get("id").asText());
      assertThat(noteOnly.get("note").asText()).isEqualTo("note-only update");
      assertThat(count("risk_snapshots")).isEqualTo(snapshots + 1);
      JsonNode profile = student.get("/profile");
      assertThat(profile.get("student").get("currentScore").asInt()).isEqualTo(17);
      String alertId = onlyActiveAlert(staff).get("id").asText();
      assertThat(count("alerts")).isEqualTo(1);
      assertThat(staff.get("/alerts/" + alertId).get("alert").get("status").asText())
          .isEqualTo("PENDING");
      assertThat(staff.ok("/alerts/" + alertId + "/accept", Map.of()).get("status").asText())
          .isEqualTo("ACCEPTED");
      JsonNode record =
          staff.ok(
              "/alerts/" + alertId + "/interventions",
              Map.of(
                  "kind", "沟通访谈",
                  "content", "已经沟通并约定支持安排",
                  "nextDate", LocalDate.now().plusDays(2).toString()));
      assertStatus(staff, alertId, "INTERVENING");
      assertThat(record.get("followUpStatus").asText()).isEqualTo("PLANNED");
      assertThat(staff.ok("/alerts/" + alertId + "/review", Map.of()).get("status").asText())
          .isEqualTo("AWAITING_REASSESSMENT");
      JsonNode reassessment = student.ok("/assessments", assessment(0, 0));
      assertThat(reassessment.get("riskLevel").asText()).isEqualTo("LOW");
      assertThat(reassessment.get("ruleRiskLevel").asText()).isEqualTo("LOW");
      assertThat(reassessment.get("id").asText()).isNotEqualTo(assessment.get("id").asText());
      assertStatus(staff, alertId, "AWAITING_REASSESSMENT");
      assertThat(
              staff
                  .ok("/alerts/" + alertId + "/close", Map.of("reason", "人工复评完成，继续日常支持"))
                  .get("status")
                  .asText())
          .isEqualTo("CLOSED");
      assertThat(staff.get("/alerts/" + alertId).get("records").size()).isEqualTo(1);
      assertThat(staff.get("/alerts/" + alertId).get("records").get(0).get("id").asText())
          .isEqualTo(record.get("id").asText());
      assertThat(related(staff.get("/dashboard").get("followUps"), alertId)).isEmpty();
      assertThat(contact.request("/profile", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      JsonNode notices = contact.get("/notifications");
      List<JsonNode> relatedNotices = related(notices, alertId);
      assertThat(relatedNotices).hasSize(1);
      assertThat(relatedNotices.getFirst().get("recipient").asText()).isEqualTo("contact");
      String notification = relatedNotices.getFirst().get("id").asText();
      contact.ok("/notifications/" + notification + "/read", Map.of());
      assertThat(
              related(contact.get("/notifications"), alertId).getFirst().get("isRead").asBoolean())
          .isTrue();
      assertThat(notices.toString()).doesNotContain("测评总分", "answers", "sleepHours");
    }
  }

  @Test
  void collegeAndRecordScopesRejectUnauthorizedAccessAndMalformedInput() throws Exception {
    try (Browser counselor = new Browser();
        Browser student = new Browser();
        Browser center = new Browser()) {
      counselor.login("counselor");
      student.login("student");
      center.login("center");
      assertThat(center.get("/dashboard").get("stats").get("students").asInt())
          .isGreaterThanOrEqualTo(36);
      JsonNode collegeDashboard = counselor.get("/dashboard");
      assertThat(collegeDashboard.get("stats").get("students").asInt())
          .isEqualTo(counselor.get("/students").size());
      assertThat(collegeDashboard.get("trend").get(0).get("day").asText()).isNotBlank();
      for (JsonNode row : counselor.get("/students")) {
        assertThat(row.get("college").asText()).isEqualTo("计算机学院");
      }
      assertThat(
              counselor
                  .request("/students/" + DemoAccounts.studentId(1), "GET", null, false, false)
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              student
                  .request("/students/" + DemoAccounts.studentId(1), "GET", null, false, false)
                  .statusCode())
          .isEqualTo(403);
      assertThat(student.request("/dashboard", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      assertThat(student.request("/ml/summary", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      assertThat(counselor.post("/ml/train", Map.of()).statusCode()).isEqualTo(403);
      assertThat(
              student
                  .post("/assessments", Map.of("answers", List.of(99), "requestId", "bad"))
                  .statusCode())
          .isEqualTo(400);
      assertThat(
              student
                  .post(
                      "/assessments",
                      Map.of(
                          "answers",
                          List.of(0, 0, 0, 0, 0, 0, 0, 0, 0.9),
                          "requestId",
                          "fractional-answer"))
                  .statusCode())
          .isEqualTo(400);
      assertThat(
              student
                  .post("/check-ins", Map.of("mood", 9, "sleepHours", 25, "stress", 0, "note", ""))
                  .statusCode())
          .isEqualTo(400);
      assertThat(
              student
                  .post(
                      "/check-ins", Map.of("mood", 2.9, "sleepHours", 7, "stress", 3.1, "note", ""))
                  .statusCode())
          .isEqualTo(400);
      student.ok("/assessments", assessment(10, 0));
      String alertId = onlyActiveAlert(center).get("id").asText();
      List<JsonNode> ownInbox = related(counselor.get("/notifications"), alertId);
      assertThat(ownInbox).hasSize(1);
      assertThat(
              student
                  .post(
                      "/notifications/" + ownInbox.getFirst().get("id").asText() + "/read",
                      Map.of())
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              student
                  .post(
                      "/check-ins", Map.of("mood", 3, "sleepHours", 7.55, "stress", 2, "note", ""))
                  .statusCode())
          .isEqualTo(400);
      String code = "CSV-" + UUID.randomUUID();
      JsonNode imported =
          center.ok(
              "/students/import",
              Map.of(
                  "csv",
                  "学号,姓名,学院,专业,年级,性别\n"
                      + code
                      + ",演示导入,计算机学院,计算机,大一,女\n"
                      + code
                      + ",重复学号,计算机学院,计算机,大一,女\n"));
      assertThat(imported.get("imported").asInt()).isEqualTo(1);
      assertThat(imported.get("failed").asInt()).isEqualTo(1);
      assertThat(center.get("/students?q=" + code).size()).isEqualTo(1);
    }
  }

  @Test
  void invalidTransitionsAreRejectedAndManualReviewKeepsTheOriginalAssignee() throws Exception {
    try (Browser student = new Browser();
        Browser counselor = new Browser();
        Browser center = new Browser()) {
      student.login("student");
      counselor.login("counselor");
      center.login("center");
      student.ok("/assessments", assessment(10, 0));
      String alertId = onlyActiveAlert(center).get("id").asText();
      assertConflict(center, alertId, "review", Map.of(), "PENDING");
      assertConflict(center, alertId, "close", Map.of("reason", "人工复评完成"), "PENDING");
      assertConflict(center, alertId, "interventions", intervention(null), "PENDING");

      JsonNode accepted = counselor.ok("/alerts/" + alertId + "/accept", Map.of());
      assertThat(accepted.get("assignedTo").asText()).isEqualTo("陈老师");
      assertConflict(center, alertId, "accept", Map.of(), "ACCEPTED");
      assertConflict(center, alertId, "review", Map.of(), "ACCEPTED");
      assertConflict(center, alertId, "close", Map.of("reason", "人工复评完成"), "ACCEPTED");
      center.ok("/alerts/" + alertId + "/interventions", intervention(null));
      assertStatus(center, alertId, "INTERVENING");
      assertThat(center.get("/alerts/" + alertId).get("alert").get("assignedTo").asText())
          .isEqualTo("陈老师");
      center.ok("/alerts/" + alertId + "/review", Map.of());
      assertConflict(center, alertId, "accept", Map.of(), "AWAITING_REASSESSMENT");
      assertConflict(center, alertId, "review", Map.of(), "AWAITING_REASSESSMENT");
      for (String reason : List.of("", "四个字内", " ".repeat(5), "长".repeat(601))) {
        assertBadRequest(
            center, alertId, "close", Map.of("reason", reason), "AWAITING_REASSESSMENT");
      }
      JsonNode closed = center.ok("/alerts/" + alertId + "/close", Map.of("reason", "人工复评好"));
      assertThat(closed.get("status").asText()).isEqualTo("CLOSED");
      assertThat(closed.get("assignedTo").asText()).isEqualTo("陈老师");
      assertThat(closed.get("closeReason").asText()).isEqualTo("人工复评好");
      assertThat(count("assessments")).isEqualTo(1);
      assertConflict(center, alertId, "accept", Map.of(), "CLOSED");
      assertConflict(center, alertId, "review", Map.of(), "CLOSED");
      assertConflict(center, alertId, "close", Map.of("reason", "人工复评完成"), "CLOSED");
      assertConflict(center, alertId, "interventions", intervention(null), "CLOSED");
      assertThat(center.get("/alerts/" + alertId).get("records").size()).isEqualTo(1);
    }
  }

  @Test
  void escalationAndNewUrgentAnswersRequireInterventionAgainBeforeClosing() throws Exception {
    try (Browser student = new Browser();
        Browser center = new Browser()) {
      student.login("student");
      center.login("center");
      student.ok("/assessments", assessment(10, 0));
      String alertId = onlyActiveAlert(center).get("id").asText();
      center.ok("/alerts/" + alertId + "/accept", Map.of());
      for (Map<String, Object> draft :
          List.of(assessment(15, 0), assessment(20, 0), assessment(1, 1))) {
        center.ok("/alerts/" + alertId + "/interventions", intervention(null));
        center.ok("/alerts/" + alertId + "/review", Map.of());
        student.ok("/assessments", draft);
        assertThat(onlyActiveAlert(center).get("id").asText()).isEqualTo(alertId);
        assertStatus(center, alertId, "ACCEPTED");
        assertConflict(center, alertId, "review", Map.of(), "ACCEPTED");
        assertConflict(center, alertId, "close", Map.of("reason", "人工复评完成"), "ACCEPTED");
      }
      center.ok("/alerts/" + alertId + "/interventions", intervention(null));
      center.ok("/alerts/" + alertId + "/review", Map.of());
      center.ok("/alerts/" + alertId + "/close", Map.of("reason", "重新干预并人工复评完成"));
      assertStatus(center, alertId, "CLOSED");
      assertThat(count("alerts")).isEqualTo(1);
    }
  }

  @Test
  void notificationsBelongToTheCurrentCaseAndCollegeScopeIsCheckedAgainOnRead() throws Exception {
    try (Browser student = new Browser();
        Browser center = new Browser();
        Browser counselor = new Browser();
        Browser contact = new Browser()) {
      student.login("student");
      center.login("center");
      counselor.login("counselor");
      contact.login("contact");
      student.ok("/assessments", assessment(1, 1));
      String alertId = onlyActiveAlert(center).get("id").asText();
      List<JsonNode> counselorNotices = related(counselor.get("/notifications"), alertId);
      List<JsonNode> centerNotices = related(center.get("/notifications"), alertId);
      List<JsonNode> contactNotices = related(contact.get("/notifications"), alertId);
      assertThat(counselorNotices).hasSize(1);
      assertThat(centerNotices).hasSize(1);
      assertThat(contactNotices).hasSize(1);
      assertThat(counselorNotices.getFirst().get("recipient").asText()).isEqualTo("counselor");
      assertThat(centerNotices.getFirst().get("recipient").asText()).isEqualTo("center");
      assertThat(contactNotices.getFirst().get("recipient").asText()).isEqualTo("contact");
      String notificationId = counselorNotices.getFirst().get("id").asText();
      assertThat(center.post("/notifications/" + notificationId + "/read", Map.of()).statusCode())
          .isEqualTo(403);
      assertThat(contact.post("/notifications/" + notificationId + "/read", Map.of()).statusCode())
          .isEqualTo(403);
      assertThat(student.post("/notifications/" + notificationId + "/read", Map.of()).statusCode())
          .isEqualTo(403);
      assertThat(contactNotices.toString())
          .doesNotContain("测评总分", "answers", "sleepHours", "currentScore");

      JsonNode profile = center.get("/students/" + DemoAccounts.studentId(0)).get("student");
      Map<String, Object> moved =
          Map.of(
              "studentCode", profile.get("studentCode").asText(),
              "displayName", profile.get("displayName").asText(),
              "college", "经济管理学院",
              "major", profile.get("major").asText(),
              "yearName", profile.get("yearName").asText(),
              "gender", profile.get("gender").asText(),
              "age", profile.get("age").asInt(),
              "contactName", profile.get("contactName").asText());
      HttpResponse<String> transferred =
          center.request(
              "/students/" + DemoAccounts.studentId(0),
              "PUT",
              json.writeValueAsString(moved),
              true,
              false);
      assertThat(transferred.statusCode()).withFailMessage(transferred.body()).isEqualTo(200);
      assertThat(related(counselor.get("/notifications"), alertId)).isEmpty();
      assertThat(
              counselor.post("/notifications/" + notificationId + "/read", Map.of()).statusCode())
          .isEqualTo(403);
      assertThat(counselor.request("/alerts/" + alertId, "GET", null, false, false).statusCode())
          .isEqualTo(403);
      assertThat(
              jdbc.queryForObject(
                  "SELECT is_read FROM notifications WHERE id=?",
                  Boolean.class,
                  UUID.fromString(notificationId)))
          .isFalse();
      assertThat(related(center.get("/notifications"), alertId)).hasSize(1);
      assertThat(related(contact.get("/notifications"), alertId)).hasSize(1);
    }
  }

  @Test
  void concurrentSignalsAndRequestRetriesKeepOneActiveAlert() throws Exception {
    try (Browser first = new Browser();
        Browser second = new Browser();
        Browser center = new Browser()) {
      first.login("student");
      second.login("student");
      center.login("center");
      CompletableFuture<HttpResponse<String>> firstResult =
          postAsync(first, "/assessments", assessment(15, 0));
      CompletableFuture<HttpResponse<String>> secondResult =
          postAsync(second, "/assessments", assessment(20, 0));
      assertOk(firstResult.get(20, TimeUnit.SECONDS));
      assertOk(secondResult.get(20, TimeUnit.SECONDS));
      assertThat(count("assessments")).isEqualTo(2);
      assertThat(count("risk_snapshots")).isEqualTo(2);
      assertThat(count("alerts")).isEqualTo(1);
      assertThat(onlyActiveAlert(center).get("riskLevel").asText()).isEqualTo("CRITICAL");

      resetDemoStudent();
      Map<String, Object> retry = assessment(15, 0);
      firstResult = postAsync(first, "/assessments", retry);
      secondResult = postAsync(second, "/assessments", retry);
      JsonNode firstResponse = assertOk(firstResult.get(20, TimeUnit.SECONDS));
      JsonNode secondResponse = assertOk(secondResult.get(20, TimeUnit.SECONDS));
      assertThat(firstResponse.get("id").asText()).isEqualTo(secondResponse.get("id").asText());
      assertThat(count("assessments")).isEqualTo(1);
      assertThat(count("risk_snapshots")).isEqualTo(1);
      assertThat(count("alerts")).isEqualTo(1);
      assertThat(onlyActiveAlert(center).get("riskLevel").asText()).isEqualTo("HIGH");
    }
  }

  @Test
  void concurrentAcceptsHaveExactlyOneWinner() throws Exception {
    try (Browser student = new Browser();
        Browser center = new Browser();
        Browser counselor = new Browser()) {
      student.login("student");
      center.login("center");
      counselor.login("counselor");
      student.ok("/assessments", assessment(10, 0));
      String alertId = onlyActiveAlert(center).get("id").asText();

      CompletableFuture<HttpResponse<String>> centerResult =
          postAsync(center, "/alerts/" + alertId + "/accept", Map.of());
      CompletableFuture<HttpResponse<String>> counselorResult =
          postAsync(counselor, "/alerts/" + alertId + "/accept", Map.of());
      HttpResponse<String> centerResponse = centerResult.get(20, TimeUnit.SECONDS);
      HttpResponse<String> counselorResponse = counselorResult.get(20, TimeUnit.SECONDS);
      assertThat(List.of(centerResponse.statusCode(), counselorResponse.statusCode()))
          .containsExactlyInAnyOrder(200, 409);
      HttpResponse<String> winner =
          centerResponse.statusCode() == 200 ? centerResponse : counselorResponse;
      JsonNode accepted = assertOk(winner);
      assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
      assertThat(center.get("/alerts/" + alertId).get("alert").get("assignedTo").asText())
          .isEqualTo(accepted.get("assignedTo").asText());
      assertThat(count("alerts")).isEqualTo(1);
    }
  }

  @Test
  void concurrentEscalationAndCloseNeverReopenAClosedCase() throws Exception {
    try (Browser student = new Browser();
        Browser center = new Browser()) {
      student.login("student");
      center.login("center");
      student.ok("/assessments", assessment(10, 0));
      String alertId = onlyActiveAlert(center).get("id").asText();
      center.ok("/alerts/" + alertId + "/accept", Map.of());
      center.ok("/alerts/" + alertId + "/interventions", intervention(null));
      center.ok("/alerts/" + alertId + "/review", Map.of());

      CompletableFuture<HttpResponse<String>> closeResult =
          postAsync(center, "/alerts/" + alertId + "/close", Map.of("reason", "人工复评完成，恢复日常支持"));
      CompletableFuture<HttpResponse<String>> signalResult =
          postAsync(student, "/assessments", assessment(15, 0));
      HttpResponse<String> closeResponse = closeResult.get(20, TimeUnit.SECONDS);
      assertOk(signalResult.get(20, TimeUnit.SECONDS));
      assertThat(closeResponse.statusCode()).withFailMessage(closeResponse.body()).isIn(200, 409);
      JsonNode original = center.get("/alerts/" + alertId).get("alert");
      JsonNode active = onlyActiveAlert(center);
      assertThat(active.get("riskLevel").asText()).isEqualTo("HIGH");
      if (closeResponse.statusCode() == 200) {
        assertThat(original.get("status").asText()).isEqualTo("CLOSED");
        assertThat(active.get("id").asText()).isNotEqualTo(alertId);
        assertThat(active.get("status").asText()).isEqualTo("PENDING");
        assertThat(count("alerts")).isEqualTo(2);
      } else {
        assertThat(original.get("status").asText()).isEqualTo("ACCEPTED");
        assertThat(active.get("id").asText()).isEqualTo(alertId);
        assertThat(count("alerts")).isEqualTo(1);
      }
      assertThat(center.get("/alerts/" + alertId).get("records").size()).isEqualTo(1);
    }
  }

  private int count(String table) {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM " + table + " WHERE student_id=?",
        Integer.class,
        DemoAccounts.studentId(0));
  }

  private Map<String, Object> assessment(int score, int urgentAnswer) {
    List<Integer> answers = new ArrayList<>();
    int remaining = score - urgentAnswer;
    for (int index = 0; index < 8; index++) {
      int answer = Math.min(3, remaining);
      answers.add(answer);
      remaining -= answer;
    }
    assertThat(remaining).isZero();
    answers.add(urgentAnswer);
    return Map.of("answers", answers, "requestId", UUID.randomUUID().toString());
  }

  private Map<String, Object> intervention(LocalDate nextDate) {
    return nextDate == null
        ? Map.of("kind", "沟通访谈", "content", "已经沟通并约定支持安排")
        : Map.of(
            "kind", "沟通访谈",
            "content", "已经沟通并约定支持安排",
            "nextDate", nextDate.toString());
  }

  private JsonNode onlyActiveAlert(Browser staff) throws Exception {
    List<JsonNode> active = new ArrayList<>();
    for (JsonNode row : staff.get("/alerts")) {
      if (row.get("studentId").asText().equals(DemoAccounts.studentId(0).toString())
          && !row.get("status").asText().equals("CLOSED")) {
        active.add(row);
      }
    }
    assertThat(active).hasSize(1);
    return active.getFirst();
  }

  private List<JsonNode> related(JsonNode rows, String alertId) {
    List<JsonNode> result = new ArrayList<>();
    for (JsonNode row : rows) {
      if (row.hasNonNull("alertId") && row.get("alertId").asText().equals(alertId)) {
        result.add(row);
      }
    }
    return result;
  }

  private void assertStatus(Browser staff, String alertId, String status) throws Exception {
    assertThat(staff.get("/alerts/" + alertId).get("alert").get("status").asText())
        .isEqualTo(status);
  }

  private void assertConflict(
      Browser staff, String alertId, String action, Object draft, String unchangedStatus)
      throws Exception {
    assertRejected(staff, alertId, action, draft, unchangedStatus, 409);
  }

  private void assertBadRequest(
      Browser staff, String alertId, String action, Object draft, String unchangedStatus)
      throws Exception {
    assertRejected(staff, alertId, action, draft, unchangedStatus, 400);
  }

  private void assertRejected(
      Browser staff,
      String alertId,
      String action,
      Object draft,
      String unchangedStatus,
      int expectedStatus)
      throws Exception {
    HttpResponse<String> response = staff.post("/alerts/" + alertId + "/" + action, draft);
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(expectedStatus);
    assertStatus(staff, alertId, unchangedStatus);
  }

  private CompletableFuture<HttpResponse<String>> postAsync(
      Browser browser, String path, Object value) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            return browser.post(path, value);
          } catch (Exception exception) {
            throw new CompletionException(exception);
          }
        });
  }

  private JsonNode assertOk(HttpResponse<String> response) throws Exception {
    assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
    return json.readTree(response.body());
  }
}
