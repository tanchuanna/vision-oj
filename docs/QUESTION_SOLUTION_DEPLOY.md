# 题解模块上线说明

## 1. 备份

在宝塔“数据库”页面先备份当前 `tan` 库，并确认备份文件不是 0 KB。

## 2. 数据库准备

题解模块已合并进完整总脚本。新建数据库时只上传并执行 `schema.sql`：

```bash
mysql -u root -p tan < /www/wwwroot/schema.sql
```

执行后检查：

```sql
USE tan;
SHOW TABLES LIKE 'question_solution%';
SHOW INDEX FROM question_solution;
```

应当看到 `question_solution`、`question_solution_like`、`question_solution_comment` 三张表。总脚本还包含其他业务表与演示题目；已有生产库执行前须备份并评估演示数据，不能把它当作仅包含题解表的增量脚本。

## 3. 发布顺序

1. 先部署新后端 JAR，重启并查看日志无建表或 Mapper 异常。
2. 访问 `/api/question-solution/public/eligibility?questionId=已有题目ID`，确认返回成功。
3. 再上传前端 `dist` 文件并替换站点文件。
4. 用一个未 AC 账号确认只能看安全提示，用一个已 AC 账号确认可编写和查看完整题解，最后用管理员账号审核发布。

## 4. 回滚边界

前端回滚只需恢复上一版 `dist`；后端回滚恢复上一版 JAR。三张题解表是独立新表，旧版后端不会读取，因此回滚应用时不要删表，以免丢失已投稿题解。
