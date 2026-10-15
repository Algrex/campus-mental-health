package com.algrex.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.algrex.campus.ml.MachineLearningController.PredictionDraft;
import com.algrex.campus.ml.MachineLearningService;
import com.algrex.campus.ml.SurveyDataset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MachineLearningTest {
  @TempDir Path directory;

  @Test
  void lifestyleModeUsesDisjointPartitionsAndExcludesIdentifiers() throws Exception {
    Path file = directory.resolve("lifestyle.csv");
    StringBuilder csv =
        new StringBuilder(
            "Student_ID,Age,Gender,Department,CGPA,Sleep_Duration,Study_Hours,Social_Media_Hours,Physical_Activity,Stress_Level,Depression\n");
    for (int index = 0; index < 1500; index++) {
      csv.append(1001 + index)
          .append(",20,Female,Science,")
          .append(index % 10 == 0 ? "2.0,5.0,7.0,6.0," : "3.5,8.0,5.0,2.0,")
          .append(index)
          .append(index % 10 == 0 ? ",9,True" : ",2,False")
          .append("\n");
    }
    Files.writeString(file, csv);
    SurveyDataset.Loaded loaded = SurveyDataset.load(file);
    assertThat(loaded.type()).isEqualTo("lifestyle");
    assertThat(loaded.instances().numAttributes()).isEqualTo(10);
    assertThat(loaded.instances().attribute("Student_ID")).isNull();
    MachineLearningService service =
        new MachineLearningService(file.toString(), directory.resolve("out").toString(), false);
    Map<String, Object> report = service.train();
    @SuppressWarnings("unchecked")
    Map<String, Object> validation = (Map<String, Object>) report.get("validation");
    assertThat(validation)
        .containsEntry("trainRows", 900)
        .containsEntry("validationRows", 300)
        .containsEntry("testRows", 300);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) report.get("models");
    for (Map<String, Object> model : results) {
      @SuppressWarnings("unchecked")
      Map<String, Object> operating = (Map<String, Object>) model.get("operatingPoint");
      assertThat((Double) operating.get("threshold")).isBetween(0.05, 0.95);
      double[][] matrix = (double[][]) operating.get("confusionMatrix");
      assertThat(matrix[0][0] + matrix[0][1] + matrix[1][0] + matrix[1][1]).isEqualTo(300);
    }
    java.util.Set<Double> seen = new java.util.HashSet<>();
    for (String partition : List.of("training", "validation", "testing")) {
      try (var reader =
          Files.newBufferedReader(directory.resolve("out/lifestyle/" + partition + ".arff"))) {
        weka.core.Instances split = new weka.core.Instances(reader);
        for (weka.core.Instance row : split) {
          assertThat(seen.add(row.value(split.attribute("physical_activity_minutes")))).isTrue();
        }
      }
    }
    assertThat(seen).hasSize(1500);
    assertThat(
            service.predict(
                new PredictionDraft(
                    20, "Female", null, null, 3.25, null, "Science", 7.5, 5.0, 3.0, 90, 4)))
        .containsKey("predictions");
    Path small = directory.resolve("survey-second.csv");
    StringBuilder survey =
        new StringBuilder(
            "Timestamp,Choose your gender,Age,What is your course?,Your current year of Study,What is your CGPA?,Marital status,Do you have Depression?,Do you have Anxiety?,Do you have Panic attack?,Did you seek any specialist for a treatment?\n");
    for (int index = 0; index < 30; index++) {
      survey
          .append("8/7/2020,Female,20,BCS,year 2,3.00 - 3.49,No,")
          .append(index % 3 == 0 ? "Yes" : "No")
          .append(",No,No,No\n");
    }
    Files.writeString(small, survey);
    MachineLearningService dual =
        new MachineLearningService(
            file.toString(), small.toString(), directory.resolve("dual").toString(), true);
    dual.run(null);
    assertThat(dual.summary("survey").get("dataset"))
        .isNotEqualTo(dual.summary("lifestyle").get("dataset"));
    assertThat(dual.summary().get("dataset")).isEqualTo(dual.summary("lifestyle").get("dataset"));
    assertThat(
            dual.predict(
                new PredictionDraft(
                    20, "Female", null, null, 3.25, null, "Science", 7.5, 5.0, 3.0, 90, 4)))
        .containsKey("predictions");
    assertThat(Files.isRegularFile(directory.resolve("dual/survey/evaluation.json"))).isTrue();
    assertThat(Files.isRegularFile(directory.resolve("dual/lifestyle/evaluation.json"))).isTrue();
    assertThatThrownBy(
            () -> service.predict(new PredictionDraft(20, "Female", "Other", 2, 3.25, "No")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void trainingUsesOriginalSurveyFeaturesAndProducesReproducibleEvaluationAndInference()
      throws Exception {
    Path dataset = directory.resolve("survey.csv");
    StringBuilder csv =
        new StringBuilder(
            "Timestamp,Choose your gender,Age,What is your course?,Your current year of Study,What is your CGPA?,Marital status,Do you have Depression?,Do you have Anxiety?,Do you have Panic attack?,Did you seek any specialist for a treatment?\n");
    for (int index = 0; index < 30; index++) {
      csv.append("8/7/2020,Female,")
          .append(index == 0 ? "" : Integer.toString(18 + index % 6))
          .append(",BCS,year ")
          .append(index % 4 + 1)
          .append(",3.00 - 3.49,No,")
          .append(index % 3 == 0 ? "Yes" : "No")
          .append(",Yes,Yes,Yes\n");
    }
    Files.writeString(dataset, csv);
    SurveyDataset.Loaded loaded = SurveyDataset.load(dataset);
    assertThat(loaded.missingValues()).isEqualTo(1);
    assertThat(loaded.instances().numAttributes()).isEqualTo(7);
    assertThat(loaded.instances().toString()).doesNotContain("Anxiety", "Panic", "treatment");
    Path artifacts = directory.resolve("artifacts");
    MachineLearningService service =
        new MachineLearningService(dataset.toString(), artifacts.toString(), false);
    Map<String, Object> first = service.train();
    Map<String, Object> second = service.train();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> models = (List<Map<String, Object>>) first.get("models");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> repeated = (List<Map<String, Object>>) second.get("models");
    assertThat(models).hasSize(3);
    for (int index = 0; index < models.size(); index++) {
      assertThat(models.get(index).get("accuracy")).isEqualTo(repeated.get(index).get("accuracy"));
      double[][] matrix = (double[][]) models.get(index).get("confusionMatrix");
      assertThat(matrix[0][0] + matrix[0][1] + matrix[1][0] + matrix[1][1]).isEqualTo(30);
    }
    assertThat(Files.readString(artifacts.resolve("survey/evaluation.json")))
        .contains("sha256", "folds", "seed");
    assertThat(Files.isRegularFile(artifacts.resolve("survey/j48.model"))).isTrue();
    Map<String, Object> predicted =
        service.predict(new PredictionDraft(20, "Female", "Computing", 2, 3.25, "No"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> predictions =
        (List<Map<String, Object>>) predicted.get("predictions");
    assertThat(predictions).hasSize(2);
    for (Map<String, Object> prediction : predictions) {
      assertThat((Double) prediction.get("positiveProbability")).isBetween(0.0, 1.0);
    }
    assertThatThrownBy(
            () ->
                new MachineLearningService(
                        directory.resolve("missing.csv").toString(), artifacts.toString(), false)
                    .train())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingDataAndInvalidExperimentSelectorsHaveExplicitFailureStates() throws Exception {
    MachineLearningService service =
        new MachineLearningService(
            directory.resolve("missing.csv").toString(), directory.resolve("out").toString(), true);
    service.run(null);
    assertThat(service.summary()).containsEntry("status", "NO_DATA");
    assertThat(service.summary("survey")).containsEntry("status", "NO_DATA");
    assertThatThrownBy(() -> service.summary("unknown"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.train("unknown")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> service.predict(new PredictionDraft(20, "Female", "Computing", 2, 3.25, "No")))
        .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    Path invalid = directory.resolve("invalid.csv");
    Files.writeString(invalid, "bad,header\n1,2\n");
    assertThatThrownBy(() -> SurveyDataset.load(invalid))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
