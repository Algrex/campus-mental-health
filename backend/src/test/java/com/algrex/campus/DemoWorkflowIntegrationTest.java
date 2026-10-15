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
      csrf = get("/auth/session").get("csrfToken").asString();
      HttpResponse<String> response =
          request(
              "/auth/login",
              "POST",
              "username=" + username + "&password=Campus2026%21",
              true,
              true);
      assertThat(response.statusCode()).withFailMessage(response.body()).isEqualTo(200);
      JsonNode session = get("/auth/session");
      csrf = session.get("csrfToken").asString();
      assertThat(session.get("user").get("username").asString()).isEqualTo(username);
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
      assertThat(assessment.get("riskLevel").asString()).isEqualTo("CRITICAL");
      assertThat(student.ok("/assessments", draft).get("id").asString())
          .isEqualTo(assessment.get("id").asString());
      Map<String, Object> checkIn =
          Map.of("mood", 3, "sleepHours", 7.5, "stress", 2, "note", "integration demo");
      JsonNode first = student.ok("/check-ins", checkIn);
      snapshots = count("risk_snapshots");
      JsonNode second = student.ok("/check-ins", checkIn);
      assertThat(first.get("id").asString()).isEqualTo(second.get("id").asString());
      JsonNode profile = student.get("/profile");
      assertThat(profile.get("student").get("currentScore").asInt()).isEqualTo(17);
      String alertId = "";
      for (JsonNode alert : staff.get("/alerts")) {
        if (alert.get("studentId").asString().equals(DemoAccounts.studentId(0).toString())
            && !alert.get("status").asString().equals("CLOSED")) {
          alertId = alert.get("id").asString();
        }
      }
      assertThat(alertId).isNotEmpty();
      JsonNode current = staff.get("/alerts/" + alertId).get("alert");
      if (current.get("status").asString().equals("PENDING")) {
        staff.ok("/alerts/" + alertId + "/accept", Map.of());
      }
      staff.ok(
          "/alerts/" + alertId + "/interventions",
          Map.of("kind", "沟通访谈", "content", "已经沟通并约定支持安排"));
      assertThat(staff.ok("/alerts/" + alertId + "/review", Map.of()).get("status").asString())
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
                  .asString())
          .isEqualTo("CLOSED");
      assertThat(staff.get("/alerts/" + alertId).get("records").size()).isEqualTo(1);
      assertThat(staff.get("/alerts/" + alertId).get("records").get(0).get("id").asText())
          .isEqualTo(record.get("id").asText());
      assertThat(related(staff.get("/dashboard").get("followUps"), alertId)).isEmpty();
      assertThat(contact.request("/profile", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      JsonNode notices = contact.get("/notifications");
      assertThat(notices.size()).isGreaterThan(0);
      String notification = notices.get(0).get("id").asString();
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
      assertThat(collegeDashboard.get("trend").get(0).get("day").asString()).isNotBlank();
      for (JsonNode row : counselor.get("/students")) {
        assertThat(row.get("college").asString()).isEqualTo("计算机学院");
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
      JsonNode ownInbox = counselor.get("/notifications");
      if (!ownInbox.isEmpty()) {
        assertThat(
                student
                    .post(
                        "/notifications/" + ownInbox.get(0).get("id").asString() + "/read",
                        Map.of())
                    .statusCode())
            .isEqualTo(403);
      }
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
  void loginLogoutAndAnonymousAccessRespectSessionAndCsrf() throws Exception {
    try (Browser browser = new Browser()) {
      assertThat(browser.get("/auth/session").get("user").isNull()).isTrue();
      for (String path :
          List.of(
              "/dashboard",
              "/students",
              "/profile",
              "/alerts",
              "/follow-ups",
              "/notifications",
              "/ml/summary")) {
        assertThat(browser.request(path, "GET", null, false, false).statusCode()).isEqualTo(401);
      }
      browser.csrf = browser.get("/auth/session").get("csrfToken").asString();
      assertThat(
              browser
                  .request("/auth/login", "POST", "username=center&password=wrong", true, true)
                  .statusCode())
          .isEqualTo(401);
      browser.login("center");
      assertThat(browser.request("/auth/logout", "POST", "{}", false, false).statusCode())
          .isEqualTo(403);
      assertThat(browser.post("/auth/logout", Map.of()).statusCode()).isEqualTo(204);
      assertThat(browser.get("/auth/session").get("user").isNull()).isTrue();
      assertThat(browser.request("/students", "GET", null, false, false).statusCode())
          .isEqualTo(401);
      browser.login("student");
    }
  }

  @Test
  void studentCrudFilteringAndCsvValidationPreserveCollegeScope() throws Exception {
    try (Browser center = new Browser();
        Browser counselor = new Browser();
        Browser student = new Browser()) {
      center.login("center");
      counselor.login("counselor");
      student.login("student");
      String code = "QA-" + UUID.randomUUID();
      Map<String, Object> draft =
          new java.util.HashMap<>(
              Map.of(
                  "studentCode",
                  code,
                  "displayName",
                  "回归测试同学",
                  "college",
                  "计算机学院",
                  "major",
                  "测试专业",
                  "yearName",
                  "大二",
                  "gender",
                  "女",
                  "age",
                  20,
                  "contactName",
                  "测试联系人"));
      JsonNode created = center.ok("/students", draft);
      String path = "/students/" + created.get("id").asString();
      assertThat(center.post("/students", draft).statusCode()).isEqualTo(409);
      assertThat(student.post("/students", draft).statusCode()).isEqualTo(403);
      assertThat(center.get("/students?q=" + code)).hasSize(1);
      assertThat(
              center.get(
                  "/students?college="
                      + java.net.URLEncoder.encode("计算机学院", java.nio.charset.StandardCharsets.UTF_8)
                      + "&level=LOW&q="
                      + code))
          .hasSize(1);
      assertThat(center.get("/students?level=HIGH&q=" + code)).isEmpty();
      draft.put("displayName", "回归修改同学");
      assertThat(
              counselor
                  .request(path, "PUT", json.writeValueAsString(draft), true, false)
                  .statusCode())
          .isEqualTo(200);
      assertThat(center.get(path).get("student").get("displayName").asString()).isEqualTo("回归修改同学");
      draft.put("college", "人文学院");
      assertThat(
              counselor
                  .request(path, "PUT", json.writeValueAsString(draft), true, false)
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              center.request(path, "PUT", json.writeValueAsString(draft), true, false).statusCode())
          .isEqualTo(200);
      assertThat(counselor.request(path, "GET", null, false, false).statusCode()).isEqualTo(403);
      draft.put("age", 15);
      assertThat(center.post("/students", draft).statusCode()).isEqualTo(400);
      assertThat(center.post("/students", Map.of()).statusCode()).isEqualTo(400);
      assertThat(center.post("/students/import", Map.of("csv", "bad,header\nx,y")).statusCode())
          .isEqualTo(400);
      assertThat(center.post("/students/import", Map.of("csv", "\"unclosed")).statusCode())
          .isEqualTo(400);
      JsonNode imported =
          counselor.ok(
              "/students/import",
              Map.of(
                  "csv",
                  "\ufeff学号,姓名,学院,专业,年级,性别\r\n"
                      + code
                      + "-C,导入同学,计算机学院,测试,大一,女\r\n"
                      + code
                      + "-O,越权同学,人文学院,测试,大一,女\r\n"
                      + code
                      + "-B,,计算机学院,测试,大一,女\r\n"));
      assertThat(imported.get("imported").asInt()).isEqualTo(1);
      assertThat(imported.get("failed").asInt()).isEqualTo(2);
      assertThat(
              center
                  .request("/students/" + UUID.randomUUID(), "GET", null, false, false)
                  .statusCode())
          .isEqualTo(400);
      assertThat(center.request("/students/not-a-uuid", "GET", null, false, false).statusCode())
          .isEqualTo(400);
    }
  }

  @Test
  void roleMatrixProtectsEveryBusinessReadAndWriteFamily() throws Exception {
    try (Browser contact = new Browser();
        Browser student = new Browser();
        Browser counselor = new Browser();
        Browser center = new Browser()) {
      contact.login("contact");
      student.login("student");
      counselor.login("counselor");
      center.login("center");
      for (String path :
          List.of("/dashboard", "/students", "/alerts", "/follow-ups", "/ml/summary")) {
        assertThat(contact.request(path, "GET", null, false, false).statusCode())
            .as(path)
            .isEqualTo(403);
        assertThat(student.request(path, "GET", null, false, false).statusCode())
            .as(path)
            .isEqualTo(403);
      }
      assertThat(center.request("/profile", "GET", null, false, false).statusCode()).isEqualTo(403);
      assertThat(counselor.request("/profile", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      for (Browser browser : List.of(center, counselor, contact)) {
        assertThat(
                browser
                    .post(
                        "/assessments",
                        Map.of(
                            "answers",
                            List.of(0, 0, 0, 0, 0, 0, 0, 0, 0),
                            "requestId",
                            UUID.randomUUID().toString()))
                    .statusCode())
            .isEqualTo(403);
        assertThat(
                browser
                    .post("/check-ins", Map.of("mood", 3, "sleepHours", 8, "stress", 2, "note", ""))
                    .statusCode())
            .isEqualTo(403);
      }
      for (Browser browser : List.of(student, contact, counselor)) {
        assertThat(browser.post("/ml/train", Map.of("dataset", "survey")).statusCode())
            .isEqualTo(403);
      }
      assertThat(
              center.request("/ml/summary?dataset=invalid", "GET", null, false, false).statusCode())
          .isEqualTo(400);
      assertThat(center.post("/ml/train", Map.of("dataset", "invalid")).statusCode())
          .isEqualTo(400);
      assertThat(
              center
                  .post("/ml/predict", Map.of("age", 15, "gender", "Other", "cgpa", 5))
                  .statusCode())
          .isEqualTo(400);
      assertThat(
              student
                  .post(
                      "/assessments",
                      Map.of("answers", List.of(0, 0, 0, 0, 0, 0, 0, 0), "requestId", "short"))
                  .statusCode())
          .isEqualTo(400);
      assertThat(
              student
                  .post(
                      "/check-ins",
                      Map.of("mood", 3, "sleepHours", 8, "stress", 2, "note", "x".repeat(601)))
                  .statusCode())
          .isEqualTo(400);
      String foreignId = "";
      for (JsonNode alert : center.get("/alerts")) {
        if (!alert.get("college").asString().equals("计算机学院")) {
          foreignId = alert.get("id").asString();
          break;
        }
      }
      assertThat(foreignId).isNotBlank();
      assertThat(counselor.request("/alerts/" + foreignId, "GET", null, false, false).statusCode())
          .isEqualTo(403);
      assertThat(counselor.post("/alerts/" + foreignId + "/accept", Map.of()).statusCode())
          .isEqualTo(403);
      assertThat(
              counselor
                  .post(
                      "/alerts/" + foreignId + "/interventions",
                      Map.of("kind", "沟通访谈", "content", "不应写入其他学院个案"))
                  .statusCode())
          .isEqualTo(403);
      assertThat(counselor.get("/follow-ups"))
          .allSatisfy(row -> assertThat(row.get("college").asString()).isEqualTo("计算机学院"));
      assertThat(
              center.request("/ml/summary?dataset=survey", "GET", null, false, false).statusCode())
          .isEqualTo(200);
    }
  }
}
