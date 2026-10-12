package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;

import com.algrex.campus.students.domain.StudentProfile;
import com.algrex.campus.students.repository.StudentProfileRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ScaffoldIntegrationTest {
  @LocalServerPort private int port;

  @Autowired private StudentProfileRepository students;

  @Test
  void healthAndScaffoldMetadataAreAvailableWithoutAccounts() throws Exception {
    assertThat(get("/actuator/health").statusCode()).isEqualTo(200);
    HttpResponse<String> info = get("/api/v1/system/info");
    assertThat(info.statusCode()).isEqualTo(200);
    assertThat(info.body()).contains("campus-mental-health", "SCAFFOLD", "PLANNED");
  }

  @Test
  void businessEndpointsAreClosedUntilAuthenticationIsImplemented() throws Exception {
    assertThat(get("/api/v1/students").statusCode()).isEqualTo(401);
  }

  @Test
  void migrationAndEntityMappingSupportPersistence() {
    UUID id = UUID.randomUUID();
    students.saveAndFlush(new StudentProfile(id, "TEST-" + id, "Anonymous test"));
    assertThat(students.findById(id)).isPresent();
    students.deleteById(id);
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
