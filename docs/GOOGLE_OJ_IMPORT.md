# Google 竞赛题归档导入

此导入包基于 [Google Coding Competitions Archive](https://github.com/google/coding-competitions-archive) 的 Apache-2.0 授权内容。原文保留英文；题面底部提供来源链接。`question_import_source` 单独记录原始路径、许可证与 Git 提交版本。**导入题目不会产生本站用户、提交、排行榜或赛事数据。**

## 当前可用范围

- 官方归档共检索到 732 个题目包。
- 430 个题目包同时具备可读取的题面、公开样例与隐藏输入/输出，并且不使用自定义或交互式校验器，已生成待导入 SQL。
- 其余 302 个的排除原因逐条写在 `docs/evidence/google_oj_import_report.csv`。不能将它们补成虚构用例来凑 983 题。
- 这 430 道是**导入候选题**。已验证数据结构和后端编译；尚未逐题用官方解答跑通完整沙箱，因此不能宣称与 Google 原评测完全一致。

## 在 test 库运行

1. 先备份 `test` 数据库，并确认现有 `schema.sql` 已执行、管理员种子账号 ID `1999990000000000001` 存在。
2. 导入 `spingboot-init/src/main/resources/db/google_oj_import.sql`。脚本可重复执行，不覆盖已有题目；会创建 `question_import_source` 表，并写入通过筛选的题目及来源记录。
3. 核对：

```sql
SELECT COUNT(*) AS imported_google_questions
FROM question_import_source
WHERE source_key = 'google-coding-competitions-archive';

SELECT COUNT(*) AS imported_without_cases
FROM question q JOIN question_import_source s ON q.id = s.question_id
WHERE s.source_key = 'google-coding-competitions-archive'
  AND (JSON_LENGTH(q.judge_case) < 2 OR JSON_VALID(q.judge_case) = 0);
```

4. 在前端打开几道题，分别用公开样例运行 C++、Java、Python 代码，并提交验证隐藏评测。**不要直接把未经完整验证的题库导入生产库。**

## 重新生成

生成脚本为 `scripts/build_google_oj_sql.py`，源仓库放在本地 `.data-sources/google-coding-competitions-archive`（已忽略，不上传仓库）。需要重新生成时运行：

```powershell
python scripts/build_google_oj_sql.py --repo .data-sources/google-coding-competitions-archive --output spingboot-init/src/main/resources/db/google_oj_import.sql --report docs/evidence/google_oj_import_report.csv
```

脚本只接受带标准输入/输出用例、长度在现有题目限制内的题。原归档采用的特殊校验器、交互协议和大用例不会自动转换。`float_tolerance` 题目通过 `judge_config.floatTolerance` 执行数字 token 容差比较，公开样例和正式评测使用同一规则。
