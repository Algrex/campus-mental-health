# Weka 机器学习实验

本项目实现 Logistic（逻辑回归）、J48（剪枝决策树）与达到十万条样本时启用的 RandomForest。ZeroR 作为多数类基线。全部训练、评估和推理在 Java 后端中完成，不依赖 Python 模型服务。

## 数据来源与模式

| 数据 | 样本 | 来源/许可 | 用途 |
| --- | --- | --- | --- |
| Student Mental health | 101 条 | [Shariful07 / Kaggle](https://www.kaggle.com/datasets/shariful07/student-mental-health)，CC0 | 匿名问卷小样本，独立实验；部分属性映射为业务演示档案 |
| Student Depression & Lifestyle | 100,000 条 | [Aldinwhyudii / Kaggle](https://www.kaggle.com/datasets/aldinwhyudii/student-depression-and-lifestyle-100k-data)，MIT | 作者明确说明为程序生成的合成数据，主要用于三模型对比 |

十万条 CSV 在本机核对为 89,938 条 False、10,062 条 True，无缺失；Student_ID 全部唯一。该标签不等同于 PHQ-9 结果或四级预警，平台历史也不参与建模。合成数据可以验证工程流程和算法比较，不能证明真实人群中的预测效力。

CSV SHA256：

```text
student-mental-health.csv
c0d1300dd5225b426f91ee75c81e5068efdadf528416628994a5a6e013063f0a

student_lifestyle_100k.csv
9dc57047a826daff9a442a9975fb8566339335bc8f351ae8189794aa89fc7b6d
```

脚本下载后核对固定哈希。`ML_DATASET` 可以指定模型 CSV；默认优先生活方式数据，两份文件存在时均自动训练。模型页面可切换独立实验；生活方式文件不存在时回退到 `DEMO_DATASET` 指定的小问卷。源码、模型 API 和报告记录数据模式、哈希、许可与标签语义。

## 预处理与特征

生活方式模式使用 9 个特征：年龄、性别、院系、CGPA、每日睡眠时长、每日学习时长、每日社交媒体时长、每周运动分钟、压力等级。移除 ID 和目标 Depression；标签 False/True 在内部映射 No/Yes，Yes 为正类。

小问卷使用年龄、性别、专业组、在读年级、CGPA 区间中点、婚姻状态。专业通过固定规则合为 Engineering/Computing/Business/Other；删除时间戳、目标、Anxiety、Panic attack 和治疗字段。年龄空值保留为缺失，不在切分前用全体数据均值填充。

`FilteredClassifier + ReplaceMissingValues` 将缺失填充拟合限制在训练数据内。Logistic 自身完成名义属性编码和标准化。J48、随机森林直接使用数值/名义特征。没有在测试集做重采样、特征选择或参数搜索。

压力是可观测输入，但合成数据的压力与 Depression 可能来自共同生成机制；模型学到的是该数据分布，不应据此解释因果关系。

## 可复现实验协议

十万条数据采用分层 **60,000 训练 / 20,000 验证 / 20,000 测试**：

1. 原始数据用种子 42 随机化并分层，取五份中的第一份为独立测试集。
2. 剩余 80,000 条用种子 43 再随机化和分层，取四份中的第一份为验证集，其余为训练集。
3. 四个模型共享完全相同的数据划分；模型仅拟合训练集。
4. 首先按统一阈值 0.5 计算测试指标，保证直接可比。
5. 对三种学习模型，仅在验证集搜索 0.05–0.95（步长 0.01），选择正类 F1 最大的阈值；相同时选择更接近 0.5 的候选。ZeroR 保留 0.5。
6. 用固定验证阈值评估独立测试集，另外展示操作阈值、精确率、召回率和 F1。测试集不参与阈值选择。
7. 推理保留训练集模型，避免全量重训后概率变化却沿用旧验证阈值。

不足 1,000 条数据改用同一随机种子 42 的分层 5 折交叉验证，预处理在各训练折中拟合，汇总所有验证折预测；之后全量重训用于演示推理。没有独立测试集时报告明确标注这一限制。

## 固定算法参数

| 模型 | 参数 | 作用 |
| --- | --- | --- |
| ZeroR | 训练集多数类别 | 暴露类别不平衡下“只预测负类”的准确率 |
| Logistic | ridge=0.01、maxIterations=200 | 正则化线性概率模型，输出系数用于解释 |
| J48 | confidence=0.25、minLeaf=100（小问卷=5）、剪枝 | 控制树复杂度，输出可读分支规则 |
| RandomForest | 100 棵树、maxDepth=12、minLeaf=10、2 执行槽、seed=42 | Bagging 与随机特征子集，限制资源并提供非线性对照 |

随机森林仅在实际加载数据 >=100,000 条时加入；不是根据文件名宣称数据规模。参数不依照测试结果反复调优。耗时包括训练、验证和测试预测（小问卷包含交叉验证和重训），不是单次推理延迟。

## 指标与解释

- 准确率衡量整体预测正确比例；本数据约 90% 为负类，不能只凭高准确率判断模型有效。
- 精确率、召回率与 F1 针对正类 Yes；零预测正类时精确率未定义，显示“—”，不伪装为优异指标。
- ROC AUC 使用未见测试样本的连续概率，衡量排序能力；UI 曲线为下采样展示，数值 AUC 使用完整预测计算。
- 混淆矩阵行是实际 No/Yes，列是预测 No/Yes。
- 模型解释展示训练模型的逻辑回归系数、J48 规则或森林结构摘要；不把统计系数说成个人病因。

各数据集的模型 API 和导出的 `evaluation.json` 是实际实验结果。仓库不预置虚构高分，也不自动选择“临床最好”的算法。

## 运行与产物

```powershell
pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle
python start.py
```

模型默认启动时分别训练两份可用数据；中心账号可在“模型实验室”切换并重新训练指定数据集，辅导员只读实验和手动推理。`ML_AUTO_TRAIN=false` 可跳过启动训练，之后手动运行；`ML_ARTIFACTS` 可设置产物目录。

`models/artifacts/survey/` 与 `models/artifacts/lifestyle/` 分别包含 `evaluation.json`、`training.arff`、大数据模式的 `validation.arff`/`testing.arff`、每个算法的 `.model`。全部被 Git 忽略。运行中只使用本进程训练的模型对象，不加载任意上传的序列化文件。清理这些产物不会删除业务数据库；重新训练可恢复。

实现依据：[Weka Logistic](https://weka.sourceforge.io/doc.stable-3-8/weka/classifiers/functions/Logistic.html)、[J48](https://weka.sourceforge.io/doc.stable-3-8/weka/classifiers/trees/J48.html)、[RandomForest](https://weka.sourceforge.io/doc.stable-3-8/weka/classifiers/trees/RandomForest.html)、[FilteredClassifier](https://weka.sourceforge.io/doc.stable-3-8/weka/classifiers/meta/FilteredClassifier.html)、[Evaluation](https://weka.sourceforge.io/doc.stable-3-8/weka/classifiers/Evaluation.html)。
