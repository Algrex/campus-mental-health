package com.algrex.campus.ml;

import com.algrex.campus.demo.CsvRows;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.Utils;

/** Fixed feature schema; never includes the target or closely related symptom/treatment answers. */
public final class SurveyDataset {
  private SurveyDataset() {}

  public record Loaded(Instances instances, String sha256, int missingValues, String type) {}

  public static Instances schema() {
    ArrayList<Attribute> attributes = new ArrayList<>();
    attributes.add(new Attribute("age"));
    attributes.add(new Attribute("gender", List.of("Female", "Male")));
    attributes.add(
        new Attribute("course_group", List.of("Engineering", "Computing", "Business", "Other")));
    attributes.add(new Attribute("study_year"));
    attributes.add(new Attribute("cgpa_midpoint"));
    attributes.add(new Attribute("married", List.of("No", "Yes")));
    attributes.add(new Attribute("self_reported_depression", List.of("No", "Yes")));
    Instances data = new Instances("student_mental_health_survey", attributes, 0);
    data.setClassIndex(attributes.size() - 1);
    return data;
  }

  public static Loaded load(Path file) throws Exception {
    byte[] bytes = Files.readAllBytes(file);
    List<List<String>> rows = CsvRows.parse(new String(bytes, StandardCharsets.UTF_8));
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    if (!rows.isEmpty() && rows.getFirst().getFirst().equals("Student_ID")) {
      return loadLifestyle(rows, hash);
    }
    if (rows.size() < 21
        || rows.size() > 1001
        || rows.getFirst().size() != 11
        || !rows.getFirst().get(7).equals("Do you have Depression?")) {
      throw new IllegalArgumentException("预期包含 20 至 1000 条记录的 Student Mental health 原始 CSV");
    }
    Instances data = schema();
    int missingValues = 0;
    for (int index = 1; index < rows.size(); index++) {
      List<String> row = rows.get(index);
      if (row.size() != 11) {
        throw new IllegalArgumentException("CSV 第 " + (index + 1) + " 行不完整");
      }
      double age =
          row.get(2).isBlank() ? Utils.missingValue() : Double.parseDouble(row.get(2).trim());
      if (Double.isNaN(age)) {
        missingValues++;
      }
      if (!Double.isNaN(age) && (age < 16 || age > 80)) {
        throw new IllegalArgumentException("样本年龄超出范围");
      }
      String gender = canonical(row.get(1), List.of("Female", "Male"));
      Matcher year = Pattern.compile("[1-4]").matcher(row.get(4));
      if (!year.find()) {
        throw new IllegalArgumentException("样本年级无法识别");
      }
      String[] interval = row.get(5).trim().split("\\s*-\\s*");
      if (interval.length != 2) {
        throw new IllegalArgumentException("CGPA 区间无法识别");
      }
      double cgpa = (Double.parseDouble(interval[0]) + Double.parseDouble(interval[1])) / 2;
      if (cgpa < 0 || cgpa > 4) {
        throw new IllegalArgumentException("CGPA 超出 0 至 4");
      }
      Instance instance =
          instance(
              data,
              age,
              gender,
              groupCourse(row.get(3)),
              Integer.parseInt(year.group()),
              cgpa,
              canonical(row.get(6), List.of("No", "Yes")));
      instance.setClassValue(canonical(row.get(7), List.of("No", "Yes")));
      data.add(instance);
    }
    int positive = 0;
    for (Instance instance : data) {
      if (instance.classValue() == 1) {
        positive++;
      }
    }
    if (positive < 5 || data.size() - positive < 5) {
      throw new IllegalArgumentException("两个类别均需至少 5 条记录才能进行分层 5 折评估");
    }
    return new Loaded(data, hash, missingValues, "survey");
  }

  public static Instances lifestyleSchema() {
    ArrayList<Attribute> attributes = new ArrayList<>();
    attributes.add(new Attribute("age"));
    attributes.add(new Attribute("gender", List.of("Female", "Male")));
    attributes.add(
        new Attribute(
            "department", List.of("Science", "Engineering", "Arts", "Medical", "Business")));
    for (String name :
        List.of(
            "cgpa",
            "sleep_hours",
            "study_hours",
            "social_media_hours",
            "physical_activity_minutes",
            "stress_level")) {
      attributes.add(new Attribute(name));
    }
    attributes.add(new Attribute("depression", List.of("No", "Yes")));
    Instances data = new Instances("student_lifestyle_synthetic", attributes, 0);
    data.setClassIndex(attributes.size() - 1);
    return data;
  }

  private static Loaded loadLifestyle(List<List<String>> rows, String hash) {
    List<String> expected =
        List.of(
            "Student_ID",
            "Age",
            "Gender",
            "Department",
            "CGPA",
            "Sleep_Duration",
            "Study_Hours",
            "Social_Media_Hours",
            "Physical_Activity",
            "Stress_Level",
            "Depression");
    if (!rows.getFirst().equals(expected) || rows.size() < 21 || rows.size() > 200001) {
      throw new IllegalArgumentException("生活方式 CSV 字段不匹配或记录数超出 20 至 200000 条");
    }
    Instances data = lifestyleSchema();
    java.util.Set<String> ids = new java.util.HashSet<>();
    int missing = 0;
    for (int index = 1; index < rows.size(); index++) {
      List<String> row = rows.get(index);
      if (row.size() != 11 || !ids.add(row.get(0)) || row.get(0).isBlank()) {
        throw new IllegalArgumentException("生活方式 CSV 第 " + (index + 1) + " 行不完整或标识重复");
      }
      Instance instance = new DenseInstance(data.numAttributes());
      instance.setDataset(data);
      for (int source : new int[] {1, 4, 5, 6, 7, 8, 9}) {
        double value =
            row.get(source).isBlank() ? Utils.missingValue() : Double.parseDouble(row.get(source));
        if (Double.isNaN(value)) {
          missing++;
        } else {
          double max =
              source == 1 ? 80 : source == 4 ? 4 : source == 8 ? 10080 : source == 9 ? 10 : 24;
          if (!Double.isFinite(value) || value < (source == 1 ? 16 : 0) || value > max) {
            throw new IllegalArgumentException("生活方式 CSV 数值超出范围");
          }
        }
        instance.setValue(source == 1 ? 0 : source - 1, value);
      }
      instance.setValue(1, canonical(row.get(2), List.of("Female", "Male")));
      instance.setValue(
          2,
          canonical(row.get(3), List.of("Science", "Engineering", "Arts", "Medical", "Business")));
      instance.setClassValue(
          canonical(row.get(10), List.of("False", "True")).equals("True") ? "Yes" : "No");
      data.add(instance);
    }
    int positive = 0;
    for (Instance row : data) {
      if (row.classValue() == 1) {
        positive++;
      }
    }
    if (positive < 10 || data.size() - positive < 10) {
      throw new IllegalArgumentException("生活方式两个类别均需至少 10 条记录");
    }
    return new Loaded(data, hash, missing, "lifestyle");
  }

  public static Instance lifestyleInstance(
      Instances schema, MachineLearningController.PredictionDraft draft) {
    if (draft.department() == null
        || draft.sleepHours() == null
        || draft.studyHours() == null
        || draft.socialMediaHours() == null
        || draft.physicalActivity() == null
        || draft.stressLevel() == null) {
      throw new IllegalArgumentException("请完整填写生活方式的 9 个特征");
    }
    Instance row = new DenseInstance(schema.numAttributes());
    row.setDataset(schema);
    row.setValue(0, draft.age());
    row.setValue(1, draft.gender());
    row.setValue(2, draft.department());
    row.setValue(3, draft.cgpa());
    row.setValue(4, draft.sleepHours());
    row.setValue(5, draft.studyHours());
    row.setValue(6, draft.socialMediaHours());
    row.setValue(7, draft.physicalActivity());
    row.setValue(8, draft.stressLevel());
    row.setMissing(schema.classIndex());
    return row;
  }

  private static String canonical(String value, List<String> allowed) {
    return allowed.stream()
        .filter(item -> item.equalsIgnoreCase(value.trim()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("CSV 类别值无法识别"));
  }

  public static String groupCourse(String value) {
    String name = value.trim().toLowerCase(Locale.ROOT);
    if (name.contains("engin") || name.equals("koe")) {
      return "Engineering";
    }
    if (name.contains("computer")
        || name.contains("information")
        || List.of("bit", "bcs", "it").contains(name)) {
      return "Computing";
    }
    if (name.contains("business")
        || name.contains("account")
        || name.contains("econom")
        || name.contains("bank")) {
      return "Business";
    }
    return "Other";
  }

  public static Instance instance(
      Instances schema,
      double age,
      String gender,
      String course,
      int year,
      double cgpa,
      String married) {
    Instance row = new DenseInstance(schema.numAttributes());
    row.setDataset(schema);
    row.setValue(0, age);
    row.setValue(1, gender);
    row.setValue(2, course);
    row.setValue(3, year);
    row.setValue(4, cgpa);
    row.setValue(5, married);
    row.setMissing(schema.classIndex());
    return row;
  }
}
