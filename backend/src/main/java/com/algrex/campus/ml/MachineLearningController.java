package com.algrex.campus.ml;

import com.algrex.campus.demo.DemoAccounts;
import com.algrex.campus.portal.PortalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ml")
public class MachineLearningController {
  private final MachineLearningService service;
  private final PortalService portal;
  private final DemoAccounts accounts;

  public MachineLearningController(
      MachineLearningService service, PortalService portal, DemoAccounts accounts) {
    this.service = service;
    this.portal = portal;
    this.accounts = accounts;
  }

  @GetMapping("/summary")
  public Map<String, Object> summary(Authentication authentication) {
    portal.staff(accounts.get(authentication.getName()));
    return service.summary();
  }

  @PostMapping("/train")
  public Map<String, Object> train(Authentication authentication) throws Exception {
    if (!accounts.get(authentication.getName()).role().equals("CENTER")) {
      throw new AccessDeniedException("center required");
    }
    return service.train();
  }

  @PostMapping("/predict")
  public Map<String, Object> predict(
      Authentication authentication, @Valid @RequestBody PredictionDraft draft) throws Exception {
    portal.staff(accounts.get(authentication.getName()));
    return service.predict(draft);
  }

  public record PredictionDraft(
      @NotNull @Min(16) @Max(80) Integer age,
      @NotNull @Pattern(regexp = "Female|Male") String gender,
      @Pattern(regexp = "Engineering|Computing|Business|Other") String courseGroup,
      @Min(1) @Max(4) Integer studyYear,
      @NotNull @DecimalMin("0") @DecimalMax("4") Double cgpa,
      @Pattern(regexp = "No|Yes") String married,
      @Pattern(regexp = "Science|Engineering|Arts|Medical|Business") String department,
      @DecimalMin("0") @DecimalMax("24") Double sleepHours,
      @DecimalMin("0") @DecimalMax("24") Double studyHours,
      @DecimalMin("0") @DecimalMax("24") Double socialMediaHours,
      @Min(0) @Max(10080) Integer physicalActivity,
      @Min(0) @Max(10) Integer stressLevel) {
    public PredictionDraft(
        Integer age,
        String gender,
        String courseGroup,
        Integer studyYear,
        Double cgpa,
        String married) {
      this(age, gender, courseGroup, studyYear, cgpa, married, null, null, null, null, null, null);
    }
  }
}
