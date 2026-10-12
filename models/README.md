# 模型产物

`ml` 模块实现 Java Weka Logistic、J48、RandomForest 和 ZeroR 基线。两份数据独立实验，页面支持切换：

- `artifacts/survey/`：匿名问卷，分层 5 折交叉验证后全量重训。
- `artifacts/lifestyle/`：十万条合成记录，分层训练/验证/测试，阈值只由验证集选择。

每个目录保存评估 JSON、ARFF 和本进程生成的模型文件，均被 Git 忽略。推理不加载任意上传的序列化文件；重启默认重新训练。具体特征、标签、参数与限制见 [实验说明](../docs/machine-learning.md)。
