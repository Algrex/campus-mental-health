package com.algrex.campus.demo;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Public local-demo identities; passwords are configured separately and hashed at startup. */
@Component
public class DemoAccounts {
  public record Account(
      String username, String displayName, String role, String studentId, String college) {}

  public static UUID studentId(int index) {
    return UUID.nameUUIDFromBytes(
        ("campus-demo-student-" + index).getBytes(StandardCharsets.UTF_8));
  }

  public List<Account> all() {
    return List.of(
        new Account("center", "沈老师", "CENTER", "", ""),
        new Account("counselor", "陈老师", "COUNSELOR", "", "计算机学院"),
        new Account("student", "林同学", "STUDENT", studentId(0).toString(), "计算机学院"),
        new Account("contact", "林同学家属", "CONTACT", studentId(0).toString(), ""));
  }

  public Account get(String username) {
    return all().stream()
        .filter(account -> account.username().equals(username))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
  }
}
