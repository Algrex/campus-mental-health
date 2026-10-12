package com.algrex.campus.system;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemInfoController {
  @GetMapping("/info")
  public Map<String, Object> info() {
    return Map.of(
        "name",
        "campus-mental-health",
        "stage",
        "DEMO",
        "version",
        "1.0",
        "modules",
        List.of("学生档案", "心理测评", "日常打卡", "关注评估", "预警随访", "站内通知", "Weka 模型实验"));
  }
}
