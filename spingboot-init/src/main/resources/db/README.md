# 数据库使用说明

项目只保留一份完整 SQL：[schema.sql](./schema.sql)。它用于新建数据库，也可在备份后为已有数据库补齐脚本中列出的部分结构。**先确定目标库，再执行脚本**；脚本不创建数据库，也不切换数据库。

## 选择数据库

| 环境 | 默认库名 | 配置来源 |
| --- | --- | --- |
| 本地开发 | `test` | `application-dev.yml`，可用 `MYSQL_URL` 覆盖 |
| 服务器生产 | `tan` | `application-prod.yml`，可用 `MYSQL_URL` 覆盖 |

先核对后端实际连接的库名。下文以本地 `test` 为例；初始化新的生产库时，把命令中的 `test` 换为 `tan`。不要在尚未确认目标库时运行脚本。

## 新建本地数据库

1. 创建 `test` 库：

   ```bash
   mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
   ```

2. 进入本说明文件所在的 `db` 目录，再执行总脚本：

   ```bash
   mysql -u root -p test < schema.sql
   ```

   上面的重定向命令适用于 Linux/macOS 终端和 Windows CMD。使用 **PowerShell** 时，可以先运行 `mysql -u root -p test`，进入 MySQL 后输入 `SOURCE schema.sql;`；也可以在图形化数据库工具中先选中 `test`，再运行 `schema.sql`。

3. 核对结果：

   ```sql
   SELECT DATABASE();
   SELECT COUNT(*) AS table_count
   FROM information_schema.tables
   WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE';
   SHOW TABLES LIKE 'question_submit';
   SHOW TABLES LIKE 'teacher_student_relation';
   ```

   全新库应有 **21 张业务表**。已有库可能还有项目之外的表，不能只凭数量判断是否正确。

## 脚本会做什么

- 创建缺失的业务表；为 `user`、`question` 和 `question_submit` 补齐脚本明确列出的缺失字段，并为提交统计补齐相关索引。
- 把已知旧角色值统一为 `user`（学生）、`teacher`（老师）、`admin`（管理员）；未知角色值保持原样。
- 用 `INSERT IGNORE` 写入缺失的初始管理员、40 道练习题和示例比赛。已有同主键记录不会被覆盖。
- 不包含删除业务表、清空用户或清空提交记录的语句。脚本中的 `DROP PROCEDURE` 只用于删除执行过程中创建的临时辅助过程。

初始管理员为 `admin` / `123456`，**仅在 `app.security.pepper` 为空时**该密码才与脚本中的密码哈希匹配。生产环境应配置自己的安全参数并妥善设置管理员密码。

## 已有数据库

执行前先备份，并确认后端实际连接的数据库。总脚本包含演示数据，因此不要把它当作“只补一张表”的增量脚本直接导入正在使用的生产库。`CREATE TABLE IF NOT EXISTS` 也不会自动修正已有表的所有历史差异；如果线上结构和总脚本不同，应先比较字段与索引，再决定如何升级。

此前的独立迁移 SQL 和本地演示账号 SQL 已移除。应用启动时仍可能由代码执行它自身的兼容性补列逻辑；这不属于本目录的第二份 SQL 文件。
