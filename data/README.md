# 数据目录

`downloads/` 保存公开原始 CSV、ZIP 和来源记录，全部被 Git 忽略。准备完整 Demo：

```powershell
pwsh -NoProfile -File scripts/download-demo-data.ps1 -IncludeLifestyle
```

也可以通过 `-LifestyleArchive "自己的 archive.zip 路径"` 使用本地压缩包。脚本固定两份 CSV 的 SHA256；数据许可、字段与训练协议见 [机器学习说明](../docs/machine-learning.md)。小问卷 101 条为公开匿名自报数据；生活方式 100,000 条由原作者程序生成，不称为十万名真实学生。

`samples/` 只用于公开导入格式；`private/` 是获授权资料位置，除占位文件外忽略。不要上传真实学生记录。两份数据不关联到同一个学生；平台虚构身份和趋势明确标注来源。
