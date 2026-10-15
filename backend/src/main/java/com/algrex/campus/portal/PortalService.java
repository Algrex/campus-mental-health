package com.algrex.campus.portal;

import com.algrex.campus.demo.CsvRows;
import com.algrex.campus.demo.DemoAccounts.Account;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortalService {
  private final JdbcTemplate jdbc;

  public PortalService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> rows(String sql, Object... arguments) {
    return jdbc.query(sql, (result, index) -> map(result), arguments);
  }

  private Map<String, Object> map(ResultSet result) throws SQLException {
    Map<String, Object> row = new LinkedHashMap<>();
    for (int i = 1; i <= result.getMetaData().getColumnCount(); i++) {
      String[] words = result.getMetaData().getColumnLabel(i).toLowerCase().split("_");
      StringBuilder key = new StringBuilder(words[0]);
      for (int j = 1; j < words.length; j++) {
        key.append(Character.toUpperCase(words[j].charAt(0))).append(words[j].substring(1));
      }
      Object value = result.getObject(i);
      if (value instanceof UUID) {
        value = value.toString();
      }
      if (value instanceof Timestamp timestamp) {
        value = timestamp.toLocalDateTime().toString();
      }
      if (value instanceof Date date) {
        value = date.toLocalDate().toString();
      }
      row.put(key.toString(), value);
    }
    return row;
  }

  public void staff(Account account) {
    if (!List.of("CENTER", "COUNSELOR").contains(account.role())) {
      throw new AccessDeniedException("staff required");
    }
  }

  public Map<String, Object> student(Account account, UUID id) {
    List<Map<String, Object>> list = rows("SELECT * FROM student_profiles WHERE id = ?", id);
    if (list.isEmpty()) {
      throw new IllegalArgumentException("学生记录不存在");
    }
    Map<String, Object> student = list.getFirst();
    if (account.role().equals("CENTER")) {
      return student;
    }
    if (account.role().equals("COUNSELOR") && account.college().equals(student.get("college"))) {
      return student;
    }
    if (account.role().equals("STUDENT") && account.studentId().equals(id.toString())) {
      return student;
    }
    throw new AccessDeniedException("student scope");
  }

  private void lockStudent(Account account, UUID id) {
    if (rows("SELECT id FROM student_profiles WHERE id=? FOR UPDATE", id).isEmpty()) {
      throw new IllegalArgumentException("学生记录不存在");
    }
    student(account, id);
  }

  public List<Map<String, Object>> students(
      Account account, String query, String college, String level) {
    staff(account);
    List<Map<String, Object>> all =
        account.role().equals("COUNSELOR")
            ? rows(
                "SELECT * FROM student_profiles WHERE college = ? ORDER BY current_score DESC, student_code",
                account.college())
            : rows("SELECT * FROM student_profiles ORDER BY current_score DESC, student_code");
    String search = query == null ? "" : query.trim().toLowerCase();
    return all.stream()
        .filter(
            row ->
                search.isBlank()
                    || (row.get("displayName")
                            + " "
                            + row.get("studentCode")
                            + " "
                            + row.get("major"))
                        .toLowerCase()
                        .contains(search))
        .filter(row -> college == null || college.isBlank() || college.equals(row.get("college")))
        .filter(row -> level == null || level.isBlank() || level.equals(row.get("riskLevel")))
        .toList();
  }

  public Map<String, Object> detail(Account account, UUID id) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("student", student(account, id));
    result.put(
        "assessments",
        rows(
            "SELECT * FROM assessments WHERE student_id=? ORDER BY created_at DESC,sequence_no DESC",
            id));
    result.put(
        "checkIns",
        rows("SELECT * FROM check_ins WHERE student_id=? ORDER BY check_date DESC LIMIT 30", id));
    result.put(
        "trend", rows("SELECT * FROM risk_snapshots WHERE student_id=? ORDER BY created_at", id));
    if (!account.role().equals("STUDENT")) {
      result.put(
          "alerts", rows("SELECT * FROM alerts WHERE student_id=? ORDER BY created_at DESC", id));
    }
    return result;
  }

  public Map<String, Object> dashboard(Account account) {
    List<Map<String, Object>> students = students(account, "", "", "");
    List<Map<String, Object>> alerts = alerts(account, "");
    Map<String, Object> result = new LinkedHashMap<>();
    long attention = students.stream().filter(row -> !row.get("riskLevel").equals("LOW")).count();
    long active = alerts.stream().filter(row -> !row.get("status").equals("CLOSED")).count();
    long pending = alerts.stream().filter(row -> row.get("status").equals("PENDING")).count();
    String where = account.role().equals("COUNSELOR") ? " WHERE s.college=? " : " ";
    Object[] args =
        account.role().equals("COUNSELOR") ? new Object[] {account.college()} : new Object[] {};
    long completed =
        ((Number)
                rows(
                        "SELECT COUNT(DISTINCT a.student_id) AS \"value\" FROM assessments a JOIN student_profiles s ON s.id=a.student_id"
                            + where,
                        args)
                    .getFirst()
                    .get("value"))
            .longValue();
    result.put(
        "stats",
        Map.of(
            "students",
            students.size(),
            "attention",
            attention,
            "activeAlerts",
            active,
            "pending",
            pending,
            "assessmentRate",
            students.isEmpty() ? 0 : Math.round(completed * 100.0 / students.size())));
    result.put(
        "distribution",
        List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").stream()
            .map(
                level ->
                    Map.of(
                        "level",
                        level,
                        "value",
                        students.stream()
                            .filter(row -> row.get("riskLevel").equals(level))
                            .count()))
            .toList());
    String trendWhere = account.role().equals("COUNSELOR") ? " WHERE s.college=? AND " : " WHERE ";
    result.put(
        "trend",
        rows(
            "SELECT CAST(r.created_at AS DATE) AS \"day\", AVG(r.score) AS score, COUNT(*) AS evaluations FROM risk_snapshots r JOIN student_profiles s ON s.id=r.student_id"
                + trendWhere
                + "r.created_at >= CURRENT_DATE - 13 GROUP BY CAST(r.created_at AS DATE) ORDER BY \"day\"",
            args));
    result.put(
        "priority",
        alerts.stream().filter(row -> !row.get("status").equals("CLOSED")).limit(5).toList());
    result.put(
        "recent",
        rows(
            "SELECT a.id, a.score, a.risk_level, a.created_at, s.display_name, s.college FROM assessments a JOIN student_profiles s ON s.id=a.student_id"
                + where
                + " ORDER BY a.created_at DESC LIMIT 6",
            args));
    result.put(
        "source",
        rows("SELECT value_text FROM app_meta WHERE key_name='source'").stream()
            .findFirst()
            .map(row -> row.get("valueText"))
            .orElse("演示数据"));
    result.put(
        "followUps",
        followUps(account).stream()
            .filter(
                row ->
                    !row.get("status").equals("CLOSED")
                        && row.get("followUpStatus").equals("PLANNED"))
            .limit(5)
            .toList());
    return result;
  }

  @Transactional
  public Map<String, Object> saveStudent(
      Account account, UUID id, PortalController.StudentDraft draft) {
    staff(account);
    if (account.role().equals("COUNSELOR") && !account.college().equals(draft.college())) {
      throw new AccessDeniedException("college scope");
    }
    if (id == null) {
      id = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO student_profiles(id,student_code,display_name,college,major,year_name,gender,age,contact_name,source_name) VALUES(?,?,?,?,?,?,?,?,?,?)",
          id,
          draft.studentCode().trim(),
          draft.displayName().trim(),
          draft.college(),
          draft.major(),
          draft.yearName(),
          draft.gender(),
          draft.age(),
          draft.contactName(),
          "演示录入");
    } else {
      lockStudent(account, id);
      jdbc.update(
          "UPDATE student_profiles SET student_code=?,display_name=?,college=?,major=?,year_name=?,gender=?,age=?,contact_name=? WHERE id=?",
          draft.studentCode().trim(),
          draft.displayName().trim(),
          draft.college(),
          draft.major(),
          draft.yearName(),
          draft.gender(),
          draft.age(),
          draft.contactName(),
          id);
    }
    return student(account, id);
  }

  public Map<String, Object> importStudents(Account account, String csv) {
    staff(account);
    List<List<String>> input = CsvRows.parse(csv);
    if (input.size() < 2
        || input.size() > 501
        || !input.getFirst().equals(List.of("学号", "姓名", "学院", "专业", "年级", "性别"))) {
      throw new IllegalArgumentException("请提供表头和 1 至 500 条记录");
    }
    List<Map<String, Object>> errors = new ArrayList<>();
    int imported = 0;
    for (int index = 1; index < input.size(); index++) {
      List<String> row = input.get(index);
      try {
        if (row.size() != 6
            || row.get(0).isBlank()
            || row.get(1).isBlank()
            || row.get(2).isBlank()
            || row.get(3).isBlank()
            || row.get(4).isBlank()
            || row.get(5).isBlank()) {
          throw new IllegalArgumentException("格式应为：学号,姓名,学院,专业,年级,性别");
        }
        if (row.stream().anyMatch(value -> value.length() > 80)) {
          throw new IllegalArgumentException("字段过长");
        }
        saveStudent(
            account,
            null,
            new PortalController.StudentDraft(
                row.get(0), row.get(1), row.get(2), row.get(3), row.get(4), row.get(5), 20, "家属"));
        imported++;
      } catch (AccessDeniedException exception) {
        errors.add(Map.of("row", index + 1, "message", "无权导入该学院"));
      } catch (RuntimeException exception) {
        errors.add(Map.of("row", index + 1, "message", "格式不正确或学号已存在"));
      }
    }
    return Map.of("imported", imported, "failed", errors.size(), "errors", errors);
  }

  @Transactional
  public Map<String, Object> assess(Account account, PortalController.AssessmentDraft draft) {
    if (!account.role().equals("STUDENT")) {
      throw new AccessDeniedException("student required");
    }
    UUID id = UUID.fromString(account.studentId());
    lockStudent(account, id);
    String answers = String.join(",", draft.answers().stream().map(String::valueOf).toList());
    List<Map<String, Object>> previous =
        rows("SELECT * FROM assessments WHERE request_id=?", draft.requestId());
    if (!previous.isEmpty()) {
      if (!previous.getFirst().get("studentId").equals(id.toString())) {
        throw new AccessDeniedException("request scope");
      }
      if (!previous.getFirst().get("answers").equals(answers)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "提交标识已用于另一份测评，请刷新后重试");
      }
      return previous.getFirst();
    }
    int score = draft.answers().stream().mapToInt(Integer::intValue).sum();
    String level = scoreLevel(score, draft.answers().get(8) > 0);
    UUID assessmentId = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO assessments(id,student_id,answers,score,risk_level,created_at,request_id,source_name,rule_risk_level) VALUES(?,?,?,?,?,?,?,?,?)",
        assessmentId,
        id,
        answers,
        score,
        level,
        Timestamp.valueOf(LocalDateTime.now()),
        draft.requestId(),
        "学生自评",
        level);
    String ruleLevel = evaluate(id, assessmentId);
    jdbc.update("UPDATE assessments SET rule_risk_level=? WHERE id=?", ruleLevel, assessmentId);
    return rows("SELECT * FROM assessments WHERE id=?", assessmentId).getFirst();
  }

  @Transactional
  public Map<String, Object> checkIn(Account account, PortalController.CheckInDraft draft) {
    if (!account.role().equals("STUDENT")) {
      throw new AccessDeniedException("student required");
    }
    UUID id = UUID.fromString(account.studentId());
    lockStudent(account, id);
    if (draft.sleepHours().stripTrailingZeros().scale() > 1) {
      throw new IllegalArgumentException("睡眠时长最多保留一位小数");
    }
    LocalDate day = LocalDate.now();
    List<Map<String, Object>> previous =
        rows("SELECT * FROM check_ins WHERE student_id=? AND check_date=?", id, Date.valueOf(day));
    boolean ruleInputsChanged = true;
    if (!previous.isEmpty()) {
      Map<String, Object> current = previous.getFirst();
      ruleInputsChanged =
          !draft.mood().equals(current.get("mood"))
              || draft.sleepHours().compareTo((BigDecimal) current.get("sleepHours")) != 0
              || !draft.stress().equals(current.get("stress"));
      if (!ruleInputsChanged && draft.note().equals(current.get("note"))) {
        return current;
      }
    }
    if (previous.isEmpty()) {
      jdbc.update(
          "INSERT INTO check_ins VALUES(?,?,?,?,?,?,?,?)",
          UUID.randomUUID(),
          id,
          draft.mood(),
          draft.sleepHours(),
          draft.stress(),
          draft.note(),
          Date.valueOf(day),
          "学生打卡");
    } else {
      jdbc.update(
          "UPDATE check_ins SET mood=?,sleep_hours=?,stress=?,note=?,source_name='学生打卡' WHERE id=?",
          draft.mood(),
          draft.sleepHours(),
          draft.stress(),
          draft.note(),
          UUID.fromString(previous.getFirst().get("id").toString()));
    }
    if (ruleInputsChanged) {
      evaluate(id, null);
    }
    return rows(
            "SELECT * FROM check_ins WHERE student_id=? AND check_date=?", id, Date.valueOf(day))
        .getFirst();
  }

  public static String scoreLevel(int score, boolean urgentAnswer) {
    if (urgentAnswer || score >= 20) {
      return "CRITICAL";
    }
    if (score >= 15) {
      return "HIGH";
    }
    if (score >= 10) {
      return "MEDIUM";
    }
    return "LOW";
  }

  private int rank(String level) {
    return List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").indexOf(level);
  }

  private String evaluate(UUID id, UUID submittedAssessmentId) {
    List<Map<String, Object>> measurements =
        rows(
            "SELECT * FROM assessments WHERE student_id=? ORDER BY created_at DESC,sequence_no DESC LIMIT 1",
            id);
    int score =
        measurements.isEmpty() ? 0 : ((Number) measurements.getFirst().get("score")).intValue();
    boolean urgent =
        !measurements.isEmpty()
            && Integer.parseInt(measurements.getFirst().get("answers").toString().split(",")[8])
                > 0;
    String level = scoreLevel(score, urgent);
    String reason = urgent ? "测评中出现需优先复核的回答，请尽快人工联系。" : "最近一次测评总分 " + score + "，按演示规则进行关注分级。";
    List<Map<String, Object>> recent =
        rows(
            "SELECT * FROM check_ins WHERE student_id=? AND check_date BETWEEN CURRENT_DATE-6 AND CURRENT_DATE ORDER BY check_date DESC",
            id);
    long poorSleep =
        recent.stream().filter(row -> ((Number) row.get("sleepHours")).doubleValue() < 5).count();
    if (poorSleep >= 3 && rank(level) < 2) {
      level = "HIGH";
      reason = "近 7 天至少有 3 次睡眠不足记录，需要人工了解近期状态。";
    } else if (!recent.isEmpty()
        && ((Number) recent.getFirst().get("mood")).intValue() <= 2
        && ((Number) recent.getFirst().get("stress")).intValue() >= 4
        && rank(level) < 1) {
      level = "MEDIUM";
      reason = "最近一次打卡（" + recent.getFirst().get("checkDate") + "）情绪偏低且压力较高，建议安排一次沟通。";
    }
    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
    jdbc.update(
        "UPDATE student_profiles SET risk_level=?,current_score=?,last_activity=? WHERE id=?",
        level,
        score,
        now,
        id);
    jdbc.update(
        "INSERT INTO risk_snapshots VALUES(?,?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        score,
        level,
        reason,
        now);
    if (level.equals("LOW")) {
      return level;
    }
    List<Map<String, Object>> open =
        rows(
            "SELECT * FROM alerts WHERE student_id=? AND status<>'CLOSED' ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
            id);
    UUID urgentAssessmentId =
        urgent ? UUID.fromString(measurements.getFirst().get("id").toString()) : null;
    if (open.isEmpty()) {
      UUID alertId = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO alerts(id,student_id,risk_level,status,reason,created_at,updated_at,urgent_assessment_id) VALUES(?,?,?,'PENDING',?,?,?,?)",
          alertId,
          id,
          level,
          reason,
          now,
          now,
          urgentAssessmentId);
      notifyAlert(alertId, id, level, false);
    } else {
      Map<String, Object> current = open.getFirst();
      boolean escalation = rank(level) > rank(current.get("riskLevel").toString());
      boolean newUrgent =
          urgentAssessmentId != null
              && urgentAssessmentId.equals(submittedAssessmentId)
              && !urgentAssessmentId.toString().equals(current.get("urgentAssessmentId"));
      if (escalation || newUrgent) {
        UUID alertId = UUID.fromString(current.get("id").toString());
        jdbc.update(
            "UPDATE alerts SET risk_level=?,reason=?,updated_at=?,urgent_assessment_id=?,status=CASE WHEN status='AWAITING_REASSESSMENT' THEN 'ACCEPTED' ELSE status END WHERE id=? AND status<>'CLOSED'",
            level,
            reason,
            now,
            urgentAssessmentId,
            alertId);
        notifyAlert(alertId, id, level, newUrgent ? "新的优先复核提醒" : "关注等级已升级");
      }
    }
    return level;
  }

  public void notifyAlert(UUID alertId, UUID studentId, String level, boolean escalation) {
    notifyAlert(alertId, studentId, level, escalation ? "关注等级已升级" : "新的关注提醒");
  }

  private void notifyAlert(UUID alertId, UUID studentId, String level, String title) {
    Map<String, Object> profile =
        rows("SELECT display_name,college FROM student_profiles WHERE id=?", studentId).getFirst();
    List<String> recipients = new ArrayList<>();
    recipients.add("center");
    if (profile.get("college").equals("计算机学院")) {
      recipients.add("counselor");
    }
    for (String recipient : recipients) {
      jdbc.update(
          "INSERT INTO notifications VALUES(?,?,?,?,?,FALSE,?)",
          UUID.randomUUID(),
          alertId,
          recipient,
          title,
          profile.get("displayName") + "的状态需要关注，请查看并安排沟通。",
          Timestamp.valueOf(LocalDateTime.now()));
    }
    if (level.equals("CRITICAL")
        && studentId.equals(com.algrex.campus.demo.DemoAccounts.studentId(0))) {
      jdbc.update(
          "INSERT INTO notifications VALUES(?,?,?,?,?,FALSE,?)",
          UUID.randomUUID(),
          alertId,
          "contact",
          "请与学生保持联系",
          "学校希望您与林同学保持联系，并配合工作人员进一步了解近况。",
          Timestamp.valueOf(LocalDateTime.now()));
    }
  }

  public List<Map<String, Object>> alerts(Account account, String status) {
    staff(account);
    String sql =
        "SELECT a.*,s.display_name,s.student_code,s.college,s.current_score FROM alerts a JOIN student_profiles s ON s.id=a.student_id";
    List<Object> args = new ArrayList<>();
    List<String> filters = new ArrayList<>();
    if (account.role().equals("COUNSELOR")) {
      filters.add("s.college=?");
      args.add(account.college());
    }
    if (status != null && !status.isBlank()) {
      filters.add("a.status=?");
      args.add(status);
    }
    if (!filters.isEmpty()) {
      sql += " WHERE " + String.join(" AND ", filters);
    }
    return rows(
        sql
            + " ORDER BY CASE a.risk_level WHEN 'CRITICAL' THEN 0 WHEN 'HIGH' THEN 1 ELSE 2 END,a.created_at DESC",
        args.toArray());
  }

  private Map<String, Object> alert(Account account, UUID id) {
    staff(account);
    List<Map<String, Object>> result = rows("SELECT * FROM alerts WHERE id=?", id);
    if (result.isEmpty()) {
      throw new IllegalArgumentException("预警记录不存在");
    }
    student(account, UUID.fromString(result.getFirst().get("studentId").toString()));
    return result.getFirst();
  }

  private Map<String, Object> lockAlert(Account account, UUID id) {
    Map<String, Object> current = alert(account, id);
    // Signal processing, scope changes and case changes take locks in the same order.
    lockStudent(account, UUID.fromString(current.get("studentId").toString()));
    return rows("SELECT * FROM alerts WHERE id=? FOR UPDATE", id).getFirst();
  }

  public Map<String, Object> alertDetail(Account account, UUID id) {
    Map<String, Object> row = alert(account, id);
    return Map.of(
        "alert",
        row,
        "student",
        student(account, UUID.fromString(row.get("studentId").toString())),
        "records",
        rows("SELECT * FROM interventions WHERE alert_id=? ORDER BY created_at", id));
  }

  @Transactional
  public Map<String, Object> transition(Account account, UUID id, String action, String reason) {
    Map<String, Object> row = lockAlert(account, id);
    String status = row.get("status").toString();
    String next;
    if (action.equals("accept") && status.equals("PENDING")) {
      next = "ACCEPTED";
    } else if (action.equals("review") && status.equals("INTERVENING")) {
      next = "AWAITING_REASSESSMENT";
    } else if (action.equals("close") && status.equals("AWAITING_REASSESSMENT")) {
      if (reason == null || reason.trim().length() < 5 || reason.length() > 600) {
        throw new IllegalArgumentException("请填写至少 5 个字符的复评与关闭说明");
      }
      next = "CLOSED";
    } else {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "当前个案状态不允许执行此操作，请刷新后重试");
    }
    int changed =
        jdbc.update(
            "UPDATE alerts SET status=?,assigned_to=CASE WHEN ?='ACCEPTED' THEN ? ELSE assigned_to END,updated_at=?,close_reason=? WHERE id=? AND status=?",
            next,
            next,
            account.displayName(),
            Timestamp.valueOf(LocalDateTime.now()),
            next.equals("CLOSED") ? reason : null,
            id,
            status);
    if (changed != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "个案状态已被其他操作更新，请刷新后重试");
    }
    if (next.equals("CLOSED")) {
      jdbc.update(
          "UPDATE interventions SET follow_up_status='CANCELLED' WHERE alert_id=? AND follow_up_status='PLANNED'",
          id);
    }
    return alert(account, id);
  }

  @Transactional
  public Map<String, Object> intervention(
      Account account, UUID id, PortalController.InterventionDraft draft) {
    Map<String, Object> current = lockAlert(account, id);
    if (!List.of("ACCEPTED", "INTERVENING", "AWAITING_REASSESSMENT")
        .contains(current.get("status").toString())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "请先受理预警，已关闭的个案不能新增记录");
    }
    UUID recordId = UUID.randomUUID();
    int changed =
        jdbc.update(
            "UPDATE alerts SET status='INTERVENING',updated_at=? WHERE id=? AND status=?",
            Timestamp.valueOf(LocalDateTime.now()),
            id,
            current.get("status"));
    if (changed != 1) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "个案状态已被其他操作更新，请刷新后重试");
    }
    jdbc.update(
        "UPDATE interventions SET follow_up_status='SUPERSEDED' WHERE alert_id=? AND follow_up_status='PLANNED'",
        id);
    jdbc.update(
        "INSERT INTO interventions(id,alert_id,kind,content,next_date,author_name,created_at,follow_up_status) VALUES(?,?,?,?,?,?,?,?)",
        recordId,
        id,
        draft.kind(),
        draft.content(),
        draft.nextDate() == null ? null : Date.valueOf(draft.nextDate()),
        account.displayName(),
        Timestamp.valueOf(LocalDateTime.now()),
        draft.nextDate() == null ? "NONE" : "PLANNED");
    return rows("SELECT * FROM interventions WHERE id=?", recordId).getFirst();
  }

  public List<Map<String, Object>> followUps(Account account) {
    staff(account);
    String sql =
        "SELECT i.*,a.student_id,a.status,a.risk_level,s.display_name,s.college FROM interventions i JOIN alerts a ON a.id=i.alert_id JOIN student_profiles s ON s.id=a.student_id";
    return account.role().equals("COUNSELOR")
        ? rows(
            sql + " WHERE s.college=? ORDER BY i.next_date NULLS LAST,i.created_at DESC",
            account.college())
        : rows(sql + " ORDER BY i.next_date NULLS LAST,i.created_at DESC");
  }

  public List<Map<String, Object>> notifications(Account account) {
    List<Object> arguments = new ArrayList<>();
    arguments.add(account.username());
    String scope = notificationScope(account, arguments);
    return rows(
        "SELECT n.* FROM notifications n WHERE n.recipient=?"
            + scope
            + " ORDER BY n.created_at DESC",
        arguments.toArray());
  }

  private String notificationScope(Account account, List<Object> arguments) {
    if (account.role().equals("COUNSELOR")) {
      arguments.add(account.college());
      return " AND (n.alert_id IS NULL OR EXISTS (SELECT 1 FROM alerts a JOIN student_profiles s ON s.id=a.student_id WHERE a.id=n.alert_id AND s.college=?))";
    }
    if (List.of("STUDENT", "CONTACT").contains(account.role())) {
      arguments.add(UUID.fromString(account.studentId()));
      return " AND (n.alert_id IS NULL OR EXISTS (SELECT 1 FROM alerts a WHERE a.id=n.alert_id AND a.student_id=?))";
    }
    return "";
  }

  @Transactional
  public void readNotification(Account account, UUID id) {
    List<Object> arguments = new ArrayList<>();
    arguments.add(id);
    arguments.add(account.username());
    String scope = notificationScope(account, arguments);
    int updated =
        jdbc.update(
            "UPDATE notifications n SET is_read=TRUE WHERE n.id=? AND n.recipient=?" + scope,
            arguments.toArray());
    if (updated == 0) {
      throw new AccessDeniedException("notification recipient");
    }
  }
}
