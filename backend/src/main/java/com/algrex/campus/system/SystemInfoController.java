package com.algrex.campus.system;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Publishes scaffold metadata, never student records or fabricated monitoring results. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {
  @GetMapping("/info")
  public SystemInfo info() {
    return new SystemInfo(
        "campus-mental-health",
        "SCAFFOLD",
        List.of(
            new ModuleInfo("auth", "账号与工程基础", "A", "PLANNED"),
            new ModuleInfo("students", "档案与多源数据", "B", "PLANNED"),
            new ModuleInfo("risk", "模型与风险评估", "C", "PLANNED"),
            new ModuleInfo("alerts", "预警与干预闭环", "D", "PLANNED"),
            new ModuleInfo("student-ui", "学生端", "E", "PLANNED"),
            new ModuleInfo("workspace-ui", "工作端", "F", "PLANNED")));
  }

  public record SystemInfo(String name, String stage, List<ModuleInfo> modules) {}

  public record ModuleInfo(String key, String title, String owner, String status) {}
}
