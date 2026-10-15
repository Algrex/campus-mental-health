package com.algrex.campus.portal;

import com.algrex.campus.demo.DemoAccounts;
import com.algrex.campus.demo.DemoAccounts.Account;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PortalController {
  private final PortalService service;
  private final DemoAccounts accounts;

  public PortalController(PortalService service, DemoAccounts accounts) {
    this.service = service;
    this.accounts = accounts;
  }

  private Account who(Authentication authentication) {
    return accounts.get(authentication.getName());
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(Authentication authentication) {
    return service.dashboard(who(authentication));
  }

  @GetMapping("/students")
  public List<Map<String, Object>> students(
      Authentication authentication,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String college,
      @RequestParam(defaultValue = "") String level) {
    return service.students(who(authentication), q, college, level);
  }

  @GetMapping("/students/{id}")
  public Map<String, Object> student(Authentication authentication, @PathVariable UUID id) {
    return service.detail(who(authentication), id);
  }

  @PostMapping("/students")
  public Map<String, Object> create(
      Authentication authentication, @Valid @RequestBody StudentDraft draft) {
    return service.saveStudent(who(authentication), null, draft);
  }

  @PutMapping("/students/{id}")
  public Map<String, Object> update(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody StudentDraft draft) {
    return service.saveStudent(who(authentication), id, draft);
  }

  @PostMapping("/students/import")
  public Map<String, Object> importStudents(
      Authentication authentication, @Valid @RequestBody CsvDraft draft) {
    return service.importStudents(who(authentication), draft.csv());
  }

  @GetMapping("/profile")
  public Map<String, Object> profile(Authentication authentication) {
    Account account = who(authentication);
    if (!account.role().equals("STUDENT")) {
      throw new AccessDeniedException("student required");
    }
    return service.detail(account, UUID.fromString(account.studentId()));
  }

  @PostMapping("/assessments")
  public Map<String, Object> assess(
      Authentication authentication, @Valid @RequestBody AssessmentDraft draft) {
    return service.assess(who(authentication), draft);
  }

  @PostMapping("/check-ins")
  public Map<String, Object> checkIn(
      Authentication authentication, @Valid @RequestBody CheckInDraft draft) {
    return service.checkIn(who(authentication), draft);
  }

  @GetMapping("/alerts")
  public List<Map<String, Object>> alerts(
      Authentication authentication, @RequestParam(defaultValue = "") String status) {
    return service.alerts(who(authentication), status);
  }

  @GetMapping("/alerts/{id}")
  public Map<String, Object> alert(Authentication authentication, @PathVariable UUID id) {
    return service.alertDetail(who(authentication), id);
  }

  @PostMapping("/alerts/{id}/{action:accept|review|close}")
  public Map<String, Object> transition(
      Authentication authentication,
      @PathVariable UUID id,
      @PathVariable String action,
      @Valid @RequestBody ActionDraft draft) {
    return service.transition(who(authentication), id, action, draft.reason());
  }

  @PostMapping("/alerts/{id}/interventions")
  public Map<String, Object> intervention(
      Authentication authentication,
      @PathVariable UUID id,
      @Valid @RequestBody InterventionDraft draft) {
    return service.intervention(who(authentication), id, draft);
  }

  @GetMapping("/follow-ups")
  public List<Map<String, Object>> followUps(Authentication authentication) {
    return service.followUps(who(authentication));
  }

  @GetMapping("/notifications")
  public List<Map<String, Object>> notifications(Authentication authentication) {
    return service.notifications(who(authentication));
  }

  @PostMapping("/notifications/{id}/read")
  public Map<String, Object> read(Authentication authentication, @PathVariable UUID id) {
    service.readNotification(who(authentication), id);
    return Map.of("ok", true);
  }

  public record StudentDraft(
      @NotBlank @Size(max = 64) String studentCode,
      @NotBlank @Size(max = 100) String displayName,
      @NotBlank @Size(max = 100) String college,
      @NotBlank @Size(max = 100) String major,
      @NotBlank @Size(max = 30) String yearName,
      @NotBlank @Size(max = 20) String gender,
      @NotNull @Min(16) @Max(80) Integer age,
      @NotBlank @Size(max = 100) String contactName) {}

  public record CsvDraft(@NotBlank @Size(max = 200000) String csv) {}

  public record AssessmentDraft(
      @NotNull @Size(min = 9, max = 9) List<@NotNull @Min(0) @Max(3) Integer> answers,
      @NotBlank @Size(max = 80) String requestId) {}

  public record CheckInDraft(
      @NotNull @Min(1) @Max(5) Integer mood,
      @NotNull @DecimalMin("0") @DecimalMax("24") @Digits(integer = 2, fraction = 1)
          BigDecimal sleepHours,
      @NotNull @Min(1) @Max(5) Integer stress,
      @NotNull @Size(max = 600) String note) {}

  public record ActionDraft(@Size(max = 600) String reason) {}

  public record InterventionDraft(
      @NotBlank @Size(max = 50) String kind,
      @NotBlank @Size(min = 5, max = 1200) String content,
      @FutureOrPresent LocalDate nextDate) {}
}
