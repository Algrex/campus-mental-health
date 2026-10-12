package com.algrex.campus.demo;

import com.algrex.campus.portal.PortalService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Public survey flags are optional. All identities and longitudinal records are synthetic. */
@Component
@Order(10)
public class DemoSeeder implements ApplicationRunner {
  private final JdbcTemplate jdbc;
  private final PortalService service;
  private final String dataset;
  private final boolean enabled;

  public DemoSeeder(
      JdbcTemplate jdbc,
      PortalService service,
      @Value("${app.demo.dataset:../data/downloads/student-mental-health.csv}") String dataset,
      @Value("${app.demo.enabled:true}") boolean enabled) {
    this.jdbc = jdbc;
    this.service = service;
    this.dataset = dataset;
    this.enabled = enabled;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) throws Exception {
    if (!enabled || !service.rows("SELECT * FROM app_meta WHERE key_name='seeded'").isEmpty()) {
      return;
    }
    List<List<String>> source = new ArrayList<>();
    Path file = Path.of(dataset);
    if (Files.isRegularFile(file)) {
      List<List<String>> parsed = CsvRows.parse(Files.readString(file, StandardCharsets.UTF_8));
      if (parsed.size() < 2 || parsed.size() > 1001 || parsed.getFirst().size() != 11) {
        throw new IllegalArgumentException("演示数据集格式不正确，预期 Kaggle Student Mental health CSV");
      }
      source.addAll(parsed.subList(1, parsed.size()));
      if (source.stream().anyMatch(row -> row.size() != 11)) {
        throw new IllegalArgumentException("演示数据集包含不完整行");
      }
    }
    boolean publicSurvey = !source.isEmpty();
    int count = publicSurvey ? source.size() : 36;
    String[] colleges = {"计算机学院", "经济管理学院", "人文学院", "理学院"};
    String[] majors = {"计算机科学与技术", "工商管理", "汉语言文学", "应用数学"};
    String[] surnames = {"林", "陈", "王", "李", "张", "刘", "周", "吴", "许", "郑", "徐", "何"};
    String[] given = {"予安", "知远", "沐晴", "星禾", "云舟", "明悦", "景行", "书宁", "亦辰"};
    LocalDate today = LocalDate.now();
    Random random = new Random(20261012);
    for (int i = 0; i < count; i++) {
      UUID id = DemoAccounts.studentId(i);
      List<String> row = publicSurvey ? source.get(i) : List.of();
      int college = i % 4;
      int age = 20;
      if (publicSurvey) {
        try {
          age = Integer.parseInt(row.get(2));
        } catch (NumberFormatException ignored) {
          age = 20;
        }
      }
      String gender =
          publicSurvey
              ? (row.get(1).equalsIgnoreCase("Female") ? "女" : "男")
              : (i % 2 == 0 ? "女" : "男");
      String name =
          i == 0
              ? "林同学"
              : surnames[i % surnames.length] + given[(i / surnames.length) % given.length];
      int flags =
          publicSurvey
              ? (row.get(7).equalsIgnoreCase("Yes") ? 1 : 0)
                  + (row.get(8).equalsIgnoreCase("Yes") ? 1 : 0)
                  + (row.get(9).equalsIgnoreCase("Yes") ? 1 : 0)
              : i % 4;
      int score =
          i == 0
              ? 12
              : flags == 0
                  ? 2 + random.nextInt(7)
                  : flags == 1
                      ? 9 + random.nextInt(5)
                      : flags == 2 ? 14 + random.nextInt(5) : 20 + random.nextInt(4);
      String level = PortalService.scoreLevel(score, false);
      Timestamp last = Timestamp.valueOf(today.minusDays(1).atTime(18, 20));
      jdbc.update(
          "INSERT INTO student_profiles(id,student_code,display_name,college,major,year_name,gender,age,contact_name,risk_level,current_score,last_activity,source_name) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          "2026" + String.format("%05d", i + 1),
          name,
          colleges[college],
          majors[college],
          new String[] {"大一", "大二", "大三", "大四"}[i % 4],
          gender,
          age,
          "演示联系人",
          level,
          score,
          last,
          publicSurvey ? "公开匿名问卷映射（身份为虚构）" : "合成演示数据");
      for (int day = 14; day >= 1; day--) {
        int dailyScore =
            Math.max(0, Math.min(27, score + (day > 7 ? 2 : 0) + random.nextInt(5) - 2));
        jdbc.update(
            "INSERT INTO risk_snapshots VALUES(?,?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            dailyScore,
            PortalService.scoreLevel(dailyScore, false),
            "演示生成的关注趋势",
            Timestamp.valueOf(today.minusDays(day).atTime(18, 20)));
        double sleep =
            level.equals("LOW") ? 7 + random.nextInt(4) * 0.5 : 5 + random.nextInt(6) * 0.5;
        int mood = level.equals("LOW") ? 4 : level.equals("MEDIUM") ? 3 : 2;
        jdbc.update(
            "INSERT INTO check_ins VALUES(?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            Math.min(5, mood + random.nextInt(2)),
            sleep,
            level.equals("LOW") ? 2 : 4,
            day % 3 == 0 ? "课程与生活节奏记录（演示生成）" : "",
            Date.valueOf(today.minusDays(day)),
            "演示生成");
      }
      for (int offset : new int[] {12, 3}) {
        int actualScore = offset == 3 ? score : Math.min(24, score + 2);
        int[] answers = new int[9];
        for (int point = 0; point < actualScore; point++) {
          answers[point % 8]++;
        }
        String answerText =
            String.join(",", java.util.Arrays.stream(answers).mapToObj(String::valueOf).toList());
        jdbc.update(
            "INSERT INTO assessments VALUES(?,?,?,?,?,?,?,?)",
            UUID.randomUUID(),
            id,
            answerText,
            actualScore,
            PortalService.scoreLevel(actualScore, false),
            Timestamp.valueOf(today.minusDays(offset).atTime(15, 30)),
            "seed-" + i + "-" + offset,
            "演示生成，非原问卷测评");
      }
      if (!level.equals("LOW")) {
        UUID alertId = UUID.randomUUID();
        String status =
            new String[] {"PENDING", "ACCEPTED", "INTERVENING", "AWAITING_REASSESSMENT", "CLOSED"}
                [i % 5];
        if (i == 0) {
          status = "PENDING";
        }
        jdbc.update(
            "INSERT INTO alerts VALUES(?,?,?,?,?,?,?,?,?)",
            alertId,
            id,
            level,
            status,
            "最近一次演示测评总分 " + score + "，建议人工了解学习与生活近况。",
            Timestamp.valueOf(today.minusDays(3).atTime(15, 35)),
            last,
            status.equals("PENDING") ? null : "沈老师",
            status.equals("CLOSED") ? "演示复评：沟通后状态平稳，后续保持日常关注。" : null);
        if (!status.equals("PENDING") && !status.equals("ACCEPTED")) {
          jdbc.update(
              "INSERT INTO interventions VALUES(?,?,?,?,?,?,?)",
              UUID.randomUUID(),
              alertId,
              "沟通访谈",
              "演示记录：了解近期学习安排与睡眠状况，讨论可执行的休息计划，约定下一次沟通。",
              Date.valueOf(today.plusDays(i % 5 - 1)),
              "沈老师",
              Timestamp.valueOf(today.minusDays(2).atTime(10, 0)));
        }
        if (!status.equals("CLOSED")) {
          service.notifyAlert(alertId, id, level, false);
        }
      }
    }
    jdbc.update(
        "INSERT INTO notifications VALUES(?,NULL,'contact',?,?,FALSE,?)",
        UUID.randomUUID(),
        "日常关怀提示",
        "请保持与林同学的日常沟通。若有学校的联系请求，可与工作人员协商后续支持安排。",
        Timestamp.valueOf(LocalDateTime.now()));
    jdbc.update(
        "INSERT INTO app_meta VALUES('source',?)",
        count + (publicSurvey ? " 条公开匿名问卷 / 身份与趋势为演示生成" : " 条合成样本 / 身份与趋势为演示生成"));
    jdbc.update("INSERT INTO app_meta VALUES('seeded',?)", today.toString());
  }
}
