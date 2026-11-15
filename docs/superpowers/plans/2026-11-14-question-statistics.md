# Question Statistics Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为当前题目提供真实、公开、安全的提交统计与去重排行榜。

**Architecture:** MySQL 生成列将判题 JSON 转为可索引字段；Mapper 聚合总体数据并用窗口函数选出每位用户最佳 AC；Service 计算个人百分位；Vue 独立统计组件请求接口并用 ECharts 渲染环形图。

**Tech Stack:** MySQL 8、Spring Boot、MyBatis-Plus、Vue 3、TypeScript、ECharts、Vue-ECharts

**Spec:** `docs/superpowers/specs/2026-11-14-question-statistics-design.md`

## Global Constraints

- 统计严格限定当前 `question_id` 且 `is_delete = 0`。
- 排行榜每位用户最多一行。
- 响应不得包含代码、邮箱或数据库提交主键。
- SQL 同步写入完整 schema 和独立升级脚本。

## Review Focus

- 无提交、无 AC、只有一个 AC 用户时百分比必须合理。
- JSON 缺少 time/memory/message 时不得进入错误排行榜。
- 同一用户多次 AC 不得占多行。
- 未登录请求不得失败，且不返回个人击败比例。
- 题目 ID 非正数必须拒绝。

---

### Task 1: Database projection and indexes

**Files:**
- Modify: `spingboot-init/src/main/resources/db/schema.sql`
- Create: `spingboot-init/src/main/resources/db/migrations/V20261114__question_statistics.sql`
- Test: `spingboot-init/src/test/java/com/spingbootinit/schema/QuestionStatisticsSchemaTest.java`

- [ ] 先写 schema 契约测试并确认失败。
- [ ] 增加四个生成列和两个排名索引。
- [ ] 运行 schema 测试确认通过。

### Task 2: Public statistics API

**Files:**
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/vo/questionsubmitvo/QuestionStatisticsVO.java`
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/vo/questionsubmitvo/QuestionRankingItemVO.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/mapper/QuestionSubmitMapper.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/QuestionSubmitService.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/impl/QuestionSubmitServiceImpl.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/controller/QuestionSubmitController.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/service/QuestionStatisticsServiceTest.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/controller/QuestionSubmitControllerTest.java`

- [ ] 先写统计口径和非法参数失败测试。
- [ ] 实现总体聚合、每用户最佳记录和个人百分位。
- [ ] 暴露 `GET /question_submit/public/statistics?questionId=`。
- [ ] 运行相关 JUnit 测试与后端打包。

### Task 3: Statistics UI with ECharts

**Files:**
- Create: `前端/myoj/src/api/questionStatistics.ts`
- Create: `前端/myoj/src/views/problem/ProblemStatisticsPanel.vue`
- Modify: `前端/myoj/src/views/problem/ProblemSolveView.vue`
- Modify: `前端/myoj/package.json`
- Modify: `前端/myoj/package-lock.json`
- Test: `前端/myoj/scripts/check-question-statistics.mjs`

- [ ] 先写页面契约检查并确认失败。
- [ ] 安装并按需注册 ECharts 与 Vue-ECharts。
- [ ] 实现指标卡、环形图、排行榜切换、空状态和响应式样式。
- [ ] 接入做题页统计 Tab 并加入构建检查。
- [ ] 运行前端检查、类型检查和生产构建。

### Task 4: End-to-end verification

- [ ] 运行后端统计测试及 Maven package。
- [ ] 运行前端完整 build。
- [ ] 审查接口字段，确认不含代码、邮箱和提交主键。
- [ ] 核对独立升级 SQL 与完整 schema 一致。
