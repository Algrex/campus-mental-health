package com.algrex.campus.ml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.evaluation.NominalPrediction;
import weka.classifiers.evaluation.Prediction;
import weka.classifiers.evaluation.ThresholdCurve;
import weka.classifiers.functions.Logistic;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.J48;
import weka.classifiers.trees.RandomForest;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.filters.unsupervised.attribute.ReplaceMissingValues;

@Service
@Order(20)
public class MachineLearningService implements ApplicationRunner {
  private static final Logger LOG = LoggerFactory.getLogger(MachineLearningService.class);
  private final Path dataset;
  private final Path fallback;
  private final Path artifactDirectory;
  private final boolean autoTrain;
  private Map<String, Object> report =
      Map.of(
          "status",
          "NO_DATA",
          "message",
          "准备公开数据后，可运行真实模型实验。",
          "downloadCommand",
          "pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle");

  private record Experiment(
      Map<String, Object> report,
      Map<String, FilteredClassifier> models,
      Map<String, Double> thresholds,
      Instances header,
      boolean lifestyle) {}

  private final Map<String, Experiment> experiments = new LinkedHashMap<>();
  private String defaultDataset;

  @Autowired
  public MachineLearningService(
      @Value("${app.ml.dataset:../data/downloads/student_lifestyle_100k.csv}") String dataset,
      @Value("${app.demo.dataset:../data/downloads/student-mental-health.csv}") String fallback,
      @Value("${app.ml.artifacts:../models/artifacts}") String artifacts,
      @Value("${app.ml.auto-train:true}") boolean autoTrain) {
    this.dataset = Path.of(dataset);
    this.fallback = fallback.isBlank() ? null : Path.of(fallback);
    this.artifactDirectory = Path.of(artifacts);
    this.autoTrain = autoTrain;
  }

  public MachineLearningService(String dataset, String artifacts, boolean autoTrain) {
    this(dataset, "", artifacts, autoTrain);
  }

  private Path available() {
    if (Files.isRegularFile(dataset)) {
      return dataset;
    }
    return fallback != null && Files.isRegularFile(fallback) ? fallback : null;
  }

  @Override
  public void run(ApplicationArguments arguments) {
    if (autoTrain && available() != null) {
      try {
        train();
        if ("lifestyle".equals(defaultDataset)
            && Files.isRegularFile(dataset)
            && fallback != null
            && Files.isRegularFile(fallback)
            && !dataset
                .toAbsolutePath()
                .normalize()
                .equals(fallback.toAbsolutePath().normalize())) {
          train("survey");
        }
      } catch (Exception exception) {
        LOG.error("Model experiment failed; care workflows remain available", exception);
        report =
            Map.of(
                "status",
                "ERROR",
                "message",
                "模型训练失败，请检查数据格式并重新训练。",
                "downloadCommand",
                "pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle");
      }
    }
  }

  public synchronized Map<String, Object> summary() {
    return summary(null);
  }

  public synchronized Map<String, Object> summary(String requestedDataset) {
    String selected = resolveDataset(requestedDataset);
    if (selected == null) {
      return report;
    }
    Experiment experiment = experiments.get(selected);
    if (experiment != null) {
      return experiment.report();
    }
    return Map.of(
        "status",
        "NO_DATA",
        "message",
        "该数据集尚无实验结果，请准备数据后点击训练。",
        "downloadCommand",
        "pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle");
  }

  private String resolveDataset(String requestedDataset) {
    if (requestedDataset == null || requestedDataset.isBlank()) {
      return defaultDataset;
    }
    if (!List.of("survey", "lifestyle").contains(requestedDataset)) {
      throw new IllegalArgumentException("数据集仅支持 survey 或 lifestyle");
    }
    return requestedDataset;
  }

  /** Large data: untouched stratified test partition. Small survey: fold-local cross-validation. */
  public synchronized Map<String, Object> train() throws Exception {
    return train(null);
  }

  public synchronized Map<String, Object> train(String requestedDataset) throws Exception {
    resolveDataset(requestedDataset);
    Path file =
        requestedDataset == null || requestedDataset.isBlank()
            ? available()
            : requestedDataset.equals("survey") ? (fallback != null ? fallback : dataset) : dataset;
    if (file == null || !Files.isRegularFile(file)) {
      throw new IllegalArgumentException(
          "缺少数据，请先运行 scripts/download-demo-data.ps1 -IncludeLifestyle");
    }
    SurveyDataset.Loaded loaded = SurveyDataset.load(file);
    if (requestedDataset != null
        && !requestedDataset.isBlank()
        && !loaded.type().equals(requestedDataset)) {
      throw new IllegalArgumentException("CSV 格式与所选数据集不匹配");
    }
    Instances data = loaded.instances();
    boolean large = data.size() >= 1000;
    boolean isLifestyle = loaded.type().equals("lifestyle");
    Map<String, Classifier> algorithms = new LinkedHashMap<>();
    algorithms.put("baseline", new ZeroR());
    Logistic logistic = new Logistic();
    logistic.setRidge(0.01);
    logistic.setMaxIts(200);
    algorithms.put("logistic", logistic);
    J48 tree = new J48();
    tree.setConfidenceFactor(0.25f);
    tree.setMinNumObj(large ? 100 : 5);
    algorithms.put("j48", tree);
    if (data.size() >= 100000) {
      RandomForest forest = new RandomForest();
      forest.setNumIterations(100);
      forest.setMaxDepth(12);
      forest.setNumExecutionSlots(2);
      forest.setSeed(42);
      ((weka.classifiers.trees.RandomTree) forest.getClassifier()).setMinNum(10);
      algorithms.put("randomForest", forest);
    }
    Instances training = data, validation = null, testing = null;
    if (large) {
      Instances shuffled = new Instances(data);
      shuffled.randomize(new Random(42));
      shuffled.stratify(5);
      testing = shuffled.testCV(5, 0);
      Instances development = shuffled.trainCV(5, 0);
      development.randomize(new Random(43));
      development.stratify(4);
      validation = development.testCV(4, 0);
      training = development.trainCV(4, 0);
    }
    List<Map<String, Object>> results = new ArrayList<>();
    Map<String, FilteredClassifier> trained = new LinkedHashMap<>();
    Map<String, Double> operatingThresholds = new LinkedHashMap<>();
    for (Map.Entry<String, Classifier> entry : algorithms.entrySet()) {
      LOG.info("Training {} on {} rows", entry.getKey(), training.size());
      FilteredClassifier pipeline = new FilteredClassifier();
      pipeline.setFilter(new ReplaceMissingValues());
      pipeline.setClassifier(entry.getValue());
      long start = System.nanoTime();
      Evaluation evaluation = new Evaluation(training);
      double threshold = 0.5;
      Double validationF1 = null;
      if (large) {
        pipeline.buildClassifier(training);
        Evaluation validationEvaluation = new Evaluation(training);
        validationEvaluation.evaluateModel(pipeline, validation);
        if (!entry.getKey().equals("baseline")) {
          threshold = selectThreshold(validationEvaluation.predictions());
        }
        validationF1 = (Double) metrics(validationEvaluation.predictions(), threshold).get("f1");
        evaluation.evaluateModel(pipeline, testing);
      } else {
        evaluation.crossValidateModel(pipeline, data, 5, new Random(42));
        pipeline.buildClassifier(data);
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("id", entry.getKey());
      result.put("name", names().get(entry.getKey()));
      result.put("algorithm", entry.getValue().getClass().getSimpleName());
      result.put(
          "parameters",
          entry.getKey().equals("logistic")
              ? "ridge=0.01, maxIterations=200"
              : entry.getKey().equals("j48")
                  ? "confidence=0.25, minLeaf=" + (large ? 100 : 5) + ", pruned=true"
                  : entry.getKey().equals("randomForest")
                      ? "trees=100, maxDepth=12, minLeaf=10, slots=2, seed=42"
                      : "训练集多数类别");
      result.putAll(metrics(evaluation.predictions(), 0.5));
      result.put("auc", metric(evaluation.areaUnderROC(1)));
      Map<String, Object> operating =
          new LinkedHashMap<>(metrics(evaluation.predictions(), threshold));
      operating.put("threshold", threshold);
      operating.put("validationF1", validationF1);
      result.put("operatingPoint", operating);
      result.put("durationMs", (System.nanoTime() - start) / 1_000_000);
      result.put("explanation", entry.getValue().toString());
      Instances curve = new ThresholdCurve().getCurve(evaluation.predictions(), 1);
      List<List<Double>> roc = new ArrayList<>();
      int step = Math.max(1, curve.size() / 250);
      for (int index = 0; index < curve.size(); index += step) {
        Instance point = curve.get(index);
        double fpr = point.value(curve.attribute("False Positive Rate"));
        double tpr = point.value(curve.attribute("True Positive Rate"));
        if (Double.isFinite(fpr) && Double.isFinite(tpr)) {
          roc.add(List.of(fpr, tpr));
        }
      }
      roc.add(List.of(0.0, 0.0));
      roc.add(List.of(1.0, 1.0));
      result.put("roc", roc);
      results.add(result);
      trained.put(entry.getKey(), pipeline);
      operatingThresholds.put(entry.getKey(), threshold);
    }
    int positive = 0;
    for (Instance row : data) {
      if (row.classValue() == 1) {
        positive++;
      }
    }
    Map<String, Object> datasetInfo = new LinkedHashMap<>();
    datasetInfo.put(
        "name",
        isLifestyle ? "Student Depression & Lifestyle (100k Data)" : "Student Mental health");
    datasetInfo.put(
        "source",
        isLifestyle
            ? "https://www.kaggle.com/datasets/aldinwhyudii/student-depression-and-lifestyle-100k-data"
            : "https://www.kaggle.com/datasets/shariful07/student-mental-health");
    datasetInfo.put("license", isLifestyle ? "MIT" : "CC0: Public Domain");
    datasetInfo.put("type", loaded.type());
    datasetInfo.put("nature", isLifestyle ? "作者程序生成的合成数据" : "公开匿名自报问卷");
    datasetInfo.put("rows", data.size());
    datasetInfo.put("positive", positive);
    datasetInfo.put("negative", data.size() - positive);
    datasetInfo.put("missingValues", loaded.missingValues());
    datasetInfo.put("sha256", loaded.sha256());
    Map<String, Object> next = new LinkedHashMap<>();
    next.put("status", "READY");
    next.put("trainedAt", OffsetDateTime.now().toString());
    next.put("dataset", datasetInfo);
    next.put(
        "target",
        isLifestyle
            ? "合成数据的 Depression 标签（False / True，内部归一为 No / Yes）；不是临床诊断"
            : "问卷中自报的 Depression（No / Yes）；不是临床诊断");
    next.put(
        "features",
        isLifestyle
            ? List.of("年龄", "性别", "院系", "CGPA", "睡眠时长", "学习时长", "社交媒体时长", "每周运动分钟", "压力等级")
            : List.of("年龄", "性别", "专业组", "在读年级", "CGPA 区间中点", "婚姻状态"));
    next.put(
        "excluded",
        isLifestyle
            ? List.of("Student_ID", "Depression（标签）", "所有平台合成身份、测评与打卡")
            : List.of(
                "时间戳", "Depression（标签）", "Anxiety", "Panic attack", "治疗信息", "所有平台合成身份、测评与打卡"));
    Map<String, Object> protocol = new LinkedHashMap<>();
    protocol.put("method", large ? "分层训练 / 验证 / 测试 60 / 20 / 20" : "分层 5 折交叉验证");
    protocol.put("seed", 42);
    protocol.put("validationSeed", large ? 43 : 42);
    protocol.put("positiveClass", "Yes");
    protocol.put("threshold", 0.5);
    protocol.put("preprocessing", "仅在训练数据拟合缺失值填充；Logistic 内部名义编码与标准化");
    if (large) {
      protocol.put("trainRows", training.size());
      protocol.put("validationRows", validation.size());
      protocol.put("testRows", testing.size());
      protocol.put("thresholdSelection", "只在验证集以正类 F1 选择阈值，候选 0.05–0.95（步长 0.01）；基线固定 0.5");
    } else {
      protocol.put("folds", 5);
      protocol.put("testRows", data.size());
    }
    next.put("validation", protocol);
    next.put(
        "randomForest",
        algorithms.containsKey("randomForest") ? "100000 条样本：已加入 100 棵树的随机森林对比" : "未达到十万条，不添加随机森林");
    next.put("models", results);
    next.put(
        "limitations",
        isLifestyle
            ? "数据由作者程序生成，指标反映合成分布的可预测性。测试集未参与训练或阈值选择，仍不能证明真实校园中的效果；压力与标签可能受相同生成机制影响。"
            : "小样本单次交叉验证仅用于方法比较。评估后全量重训用于交互演示；没有独立外部测试集，不能宣称临床效力。");
    Path output = artifactDirectory.resolve(loaded.type());
    Files.createDirectories(output);
    JsonMapper mapper = JsonMapper.builder().build();
    Files.writeString(
        output.resolve("evaluation.json"),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(next));
    Files.writeString(output.resolve("training.arff"), training.toString());
    if (large) {
      Files.writeString(output.resolve("validation.arff"), validation.toString());
      Files.writeString(output.resolve("testing.arff"), testing.toString());
    }
    for (Map.Entry<String, FilteredClassifier> entry : trained.entrySet()) {
      SerializationHelper.write(
          output.resolve(entry.getKey() + ".model").toString(), entry.getValue());
    }
    // Predict with only locally trained objects; never deserialize arbitrary model uploads.
    experiments.put(
        loaded.type(),
        new Experiment(
            next,
            Map.copyOf(trained),
            Map.copyOf(operatingThresholds),
            new Instances(data, 0),
            isLifestyle));
    if (defaultDataset == null || requestedDataset == null || requestedDataset.isBlank()) {
      defaultDataset = loaded.type();
    }
    report = next;
    return report;
  }

  private static Map<String, String> names() {
    return Map.of(
        "baseline", "多数类基线", "logistic", "逻辑回归", "j48", "J48 决策树", "randomForest", "随机森林");
  }

  private static Double metric(double value) {
    return Double.isFinite(value) ? Math.round(value * 10000.0) / 10000.0 : null;
  }

  private static Map<String, Object> metrics(List<Prediction> predictions, double threshold) {
    double[][] matrix = new double[2][2];
    for (Prediction prediction : predictions) {
      NominalPrediction row = (NominalPrediction) prediction;
      matrix[(int) row.actual()][row.distribution()[1] >= threshold ? 1 : 0]++;
    }
    double tp = matrix[1][1], fp = matrix[0][1], fn = matrix[1][0], tn = matrix[0][0];
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accuracy", metric((tp + tn) / (tp + fp + fn + tn)));
    result.put("precision", metric(tp / (tp + fp)));
    result.put("recall", metric(tp / (tp + fn)));
    result.put("f1", metric(2 * tp / (2 * tp + fp + fn)));
    result.put("confusionMatrix", matrix);
    return result;
  }

  private static double selectThreshold(List<Prediction> predictions) {
    double best = 0.5, bestF1 = -1;
    for (int point = 5; point <= 95; point++) {
      double threshold = point / 100.0;
      Double f1 = (Double) metrics(predictions, threshold).get("f1");
      if (f1 != null
          && (f1 > bestF1
              || f1.equals(bestF1) && Math.abs(threshold - 0.5) < Math.abs(best - 0.5))) {
        best = threshold;
        bestF1 = f1;
      }
    }
    return best;
  }

  public synchronized Map<String, Object> predict(MachineLearningController.PredictionDraft draft)
      throws Exception {
    Experiment experiment = experiments.get(resolveDataset(draft.dataset()));
    if (experiment == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "请先准备数据并训练模型");
    }
    Map<String, FilteredClassifier> models = experiment.models();
    Map<String, Double> thresholds = experiment.thresholds();
    Instances header = experiment.header();
    Instance instance;
    if (experiment.lifestyle()) {
      instance = SurveyDataset.lifestyleInstance(header, draft);
    } else {
      if (draft.courseGroup() == null || draft.studyYear() == null || draft.married() == null) {
        throw new IllegalArgumentException("请完整填写问卷特征");
      }
      instance =
          SurveyDataset.instance(
              header,
              draft.age(),
              draft.gender(),
              draft.courseGroup(),
              draft.studyYear(),
              draft.cgpa(),
              draft.married());
    }
    List<Map<String, Object>> results = new ArrayList<>();
    for (String key : List.of("logistic", "j48", "randomForest")) {
      if (!models.containsKey(key)) {
        continue;
      }
      double probability = models.get(key).distributionForInstance(instance)[1];
      double threshold = thresholds.get(key);
      results.add(
          Map.of(
              "id",
              key,
              "name",
              names().get(key),
              "positiveProbability",
              metric(probability),
              "predictedClass",
              probability >= threshold ? "Yes" : "No",
              "threshold",
              threshold));
    }
    return Map.of("predictions", results, "note", "概率描述数据分布下的标签预测，不诊断个人状态，也不会生成学生预警。");
  }
}
