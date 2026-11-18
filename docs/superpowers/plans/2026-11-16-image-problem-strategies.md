# Image Problem Strategies Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有五类图像编程题完善为协议明确、配置独立、语义一致、可测试且适合毕业答辩的多策略判题体系。

**Architecture:** 保留统一图像用例与 Docker 沙箱链路，在 Spring Boot 中集中解析 `judgeConfig` 的语义配置，并让正式判题、公开样例和发布预检共用该规则；前端按领域分组并按题型展示专用参数；FastAPI 继续负责候选识别与图像质量分析，不把候选结果当作未经确认的标准答案。

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Vue 3, TypeScript, Arco Design, FastAPI, pytest, Docker

**Spec:** `docs/superpowers/specs/2026-11-16-image-problem-strategies-design.md`

## Global Constraints

- 不修改论文题目、任务书文档、生产数据库或历史题目数据。
- 不破坏普通编程题；不泄露隐藏用例、密钥或内部沙箱信息。
- 复用 `question.judge_config`，本次不新增数据库字段或业务表。
- 新配置优先，历史 `countTolerance` 保持兼容。
- 不把单元测试、编译成功或构建成功宣传成真实服务链路验证。
- 当前在用户指定的现有干净工作区实施，不创建提交或推送；所有改动由用户最终决定如何纳入版本历史。

## Review Focus

- 五类题型的边界值、非法格式与历史配置兼容。
- 正式判题、公开运行和发布预检是否真正共用同一语义。
- 隐藏用例失败信息是否泄露标准答案或图片信息。
- 分类题只能称为 YOLO 主目标标签，不得误称独立整图分类模型。
- `OPENCV_ANALYSIS` 只能称为分析策略标识。

---

### Task 1: Semantic image judge configuration

**Files:**
- Modify: `spingboot-init/src/main/java/com/spingbootinit/model/dto/question/JudgeConfig.java`
- Modify/Create: `spingboot-init/src/main/java/com/spingbootinit/judo/strategy/ImageJudgeConfigPolicy.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/judo/strategy/ImageOutputJudgeStrategy.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/judo/strategy/ImageOutputJudgeStrategyTest.java`

**Interfaces:**
- Produces: one resolver for count, IoU, OCR and analysis tolerances with legacy fallback.

- [ ] Write failing tests for independent fields, exact boundaries and legacy fallback.
- [ ] Implement semantic fields and centralized resolution.
- [ ] Make all five output protocols validate required structure and numeric bounds.
- [ ] Run targeted strategy tests.

### Task 2: Validation and release preflight

**Files:**
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/impl/QuestionServiceImpl.java`
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/impl/QuestionPreflightServiceImpl.java`
- Test: relevant question validation and preflight tests.

**Interfaces:**
- Consumes: semantic configuration policy from Task 1.
- Produces: per-type validation, manual confirmation and multi-image fixed-output checks.

- [ ] Write failing validation/preflight tests for each protocol and duplicate expected outputs.
- [ ] Replace ambiguous tolerance validation with per-type ranges and defaults.
- [ ] Strengthen coordinates, fixed analysis fields, model combination and candidate confirmation checks.
- [ ] Preserve public/hidden/two-image checks and count tolerance-aware diversity.
- [ ] Run targeted service tests.

### Task 3: Runtime and formal judge parity

**Files:**
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/impl/QuestionRunServiceImpl.java`
- Modify: formal judge integration call sites.
- Test: `spingboot-init/src/test/java/com/spingbootinit/service/QuestionRunServiceImplTest.java`
- Test: formal judge related tests.

**Interfaces:**
- Consumes: Task 1 policy.
- Produces: identical semantics for public samples, preflight and formal submission.

- [ ] Write failing tests demonstrating current public/formal mismatch.
- [ ] Route count and four other types through the same config-aware matching API.
- [ ] Sanitize hidden-case feedback while retaining actionable category/field/reason messages.
- [ ] Run backend image-judge test suite.

### Task 4: Teacher editor grouping and dedicated controls

**Files:**
- Modify: `前端/myoj/src/views/admin/ProblemEditorView.vue`
- Modify: `前端/myoj/generated/models/JudgeConfig.ts`
- Add: `前端/myoj/scripts/check-image-problem-types.mjs`
- Modify: `前端/myoj/package.json`

**Interfaces:**
- Consumes: backend semantic `JudgeConfig` fields.
- Produces: grouped type chooser and type-specific configuration payload.

- [ ] Add a failing static contract check for grouping, exact labels and semantic fields.
- [ ] Add grouped metadata with descriptions, examples, scenarios and judge methods.
- [ ] Render only relevant controls and synchronize legacy payload fields.
- [ ] Add clear recommendation and manual-confirmation/public-hidden guidance.
- [ ] Run contract check and Vue type check.

### Task 5: Vision service, sandbox and documentation verification

**Files:**
- Modify/Add: focused tests under `vision-ai/tests` only if audit reveals missing verifiable behavior.
- Modify/Add: focused tests under `Sandbox/src/test` only if audit reveals missing verifiable behavior.
- Modify: `docs/VISION_JUDGE_DESIGN.md`
- Add: `docs/evidence/IMAGE_JUDGE_VERIFICATION_2026-11-16.md`

**Interfaces:**
- Consumes: final five-type contract.
- Produces: truthful architecture/risk report and reproducible verification commands.

- [ ] Verify FastAPI candidate output and OpenCV quality/analysis behavior.
- [ ] Verify sandbox no-network, non-root, read-only image and resource configuration from code/tests.
- [ ] Record taskbook metric conflicts as verified, unverified or pending experiment.
- [ ] Clearly separate unit, build and live-chain evidence.

### Task 6: Final verification and review

**Files:**
- Verify all changed files.

- [ ] Run backend relevant JUnit tests and backend package.
- [ ] Run frontend type check and production build.
- [ ] Run Vision AI pytest suite and Sandbox related tests.
- [ ] Run `git diff --check`, inspect changed files and perform a fresh self-review because delegation was not requested.
- [ ] Report changed files, test results, remaining live experiments and deployment order.

