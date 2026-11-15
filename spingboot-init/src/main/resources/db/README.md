# 数据库脚本说明

默认数据库名：**`tan`**（与 `application-prod.yml` 的默认连接一致；如设置 `MYSQL_URL`，以实际配置为准）。

## 安装

```bash
mysql -u root -p < schema.sql
```

或在支持 `DELIMITER` 的 MySQL 客户端中执行 `schema.sql` 全文。此文件包含完整表结构、管理员账号（`admin`/`123456`）、40 道题和一场示例比赛。`CREATE TABLE IF NOT EXISTS`、缺列检查及 `INSERT IGNORE` 不会覆盖已有记录；导入已有库前仍应先备份并核对实际连接的库名。

`schema.sql` 是新建数据库的完整总库，已包含文字模型、图片模型以及题解模块的全部表。`migrations/` 中仅保留线上已有数据库的增量升级脚本，不包含示例数据。线上更新前先备份数据库；不要把完整示例数据导入正在使用的生产库。

题解模块升级已有库时，只执行：

```bash
mysql -u root -p tan < migrations/20261114_question_solution.sql
```

题目统计与排行榜升级已有库时，只执行（不会删除提交数据）：

```bash
mysql -u root -p tan < migrations/V20261114__question_statistics.sql
```

该统计结构也已合并进 `schema.sql`，以后全新部署只需执行总库脚本，不必重复执行迁移脚本。

执行成功后，服务器上单独上传的 SQL 副本可以删除；仓库中的迁移文件建议保留，便于以后重新部署和追溯数据库版本。

| 项 | 值 |
|----|-----|
| 管理员账号 | `admin` |
| 管理员密码 | `123456` |

> 仅当 `app.security.pepper` 为空（默认）时密码有效；生产环境请尽快改密。

配置好数据库连接（`.env` 或 `application-local.yml`）后：

1. 在 **Linux + Docker** 机器上启动 **Sandbox**（判题必需，见仓库 [Sandbox/README.md](../../../../../Sandbox/README.md)）
2. 再启动主服务 `spingboot-init`，即可用 `admin` / `123456` 登录并提交代码

## 线上已有库

如果只需要修复最近登录设备字段，先确认主服务连接的库，再在该库执行 `SHOW COLUMNS FROM user;`，只补缺少的列。不要为了补字段删除数据库或覆盖现有用户、题目和提交记录。
# 三角色权限迁移（2026-11-15）

上线前先备份数据库，然后执行 `migrations/V20261115__three_role_permissions.sql`。脚本只会规范已知的中文/旧角色值，未知值会保留并在最后的角色统计中显示，请人工确认后再继续上线。

- `user`：学生
- `teacher`：老师
- `admin`：管理员
- `teacher_student_relation`：管理员分配的多对多师生关系
