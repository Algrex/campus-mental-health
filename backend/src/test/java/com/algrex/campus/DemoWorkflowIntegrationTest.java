package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;

import com.algrex.campus.demo.DemoAccounts;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DemoWorkflowIntegrationTest {
  @LocalServerPort private int port;
  private final JsonMapper json = JsonMapper.builder().build();

  private class Browser implements AutoCloseable {
    final HttpClient client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    String csrf;

    HttpResponse<String> request(
        String path, String method, String body, boolean token, boolean form) throws Exception {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path));
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
      assertThat(student.ok("/assessments", draft).get("id").asText())
          .isEqualTo(assessment.get("id").asText());
      Map<String, Object> checkIn =
          Map.of("mood", 3, "sleepHours", 7.5, "stress", 2, "note", "integration demo");
      JsonNode first = student.ok("/check-ins", checkIn);
      JsonNode second = student.ok("/check-ins", checkIn);
      assertThat(first.get("id").asText()).isEqualTo(second.get("id").asText());
      JsonNode profile = student.get("/profile");
      assertThat(profile.get("student").get("currentScore").asInt()).isEqualTo(17);
      String alertId = "";
      for (JsonNode alert : staff.get("/alerts")) {
        if (alert.get("studentId").asText().equals(DemoAccounts.studentId(0).toString())
            && !alert.get("status").asText().equals("CLOSED")) {
          alertId = alert.get("id").asText();
        }
      }
      assertThat(alertId).isNotEmpty();
      JsonNode current = staff.get("/alerts/" + alertId).get("alert");
      if (current.get("status").asText().equals("PENDING")) {
        staff.ok("/alerts/" + alertId + "/accept", Map.of());
      }
      staff.ok(
          "/alerts/" + alertId + "/interventions",
          Map.of("kind", "沟通访谈", "content", "已经沟通并约定支持安排"));
      assertThat(staff.ok("/alerts/" + alertId + "/review", Map.of()).get("status").asText())
          .isEqualTo("AWAITING_REASSESSMENT");
      assertThat(
              staff
                  .ok("/alerts/" + alertId + "/close", Map.of("reason", "人工复评完成，继续日常支持"))
                  .get("status")
                  .asText())
          .isEqualTo("CLOSED");
      assertThat(staff.get("/alerts/" + alertId).get("records").size()).isGreaterThan(0);
      assertThat(contact.request("/profile", "GET", null, false, false).statusCode())
          .isEqualTo(403);
      JsonNode notices = contact.get("/notifications");
      assertThat(notices.size()).isGreaterThan(0);
      String notification = notices.get(0).get("id").asText();
      contact.ok("/notifications/" + notification + "/read", Map.of());
      assertThat(contact.get("/notifications").get(0).get("isRead").asBoolean()).isTrue();
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
                  .post("/check-ins", Map.of("mood", 9, "sleepHours", 25, "stress", 0, "note", ""))
                  .statusCode())
          .isEqualTo(400);
      JsonNode ownInbox = counselor.get("/notifications");
      if (!ownInbox.isEmpty()) {
        assertThat(
                student
                    .post(
                        "/notifications/" + ownInbox.get(0).get("id").asText() + "/read", Map.of())
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
}
