# Question Solution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an AC-gated, moderated editorial system with safe hints for unsolved users and full solutions for accepted users.

**Architecture:** Add a dedicated question-solution domain instead of coupling editorials to public blogs. A single backend access service owns AC/admin decisions and every response is projected to HINT or FULL before serialization; the Vue solve page delegates the complete editorial experience to a focused child component.

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, MySQL 8, JUnit 5/MockMvc, Vue 3, TypeScript, Axios, existing Markdown renderer.

**Spec:** `docs/superpowers/specs/2026-11-14-question-solution-design.md`

## Global Constraints

- Never expose complete solution content or comments to users who have not AC'd the same question.
- AC requires both `question_submit.status = 2` and `judge_info.message = "Accepted"`; admin is the only bypass.
- Author edits to a published solution reset it to pending review.
- Keep BIGINT IDs as strings at the HTTP/TypeScript boundary.
- No new UI framework and no reuse of the public blog tables.
- Merge idempotent DDL into `schema.sql` and also provide the production migration SQL.

## Review Focus

- A forged role or user ID must not unlock full content; controller tests must prove identity comes from the authenticated context.
- Malformed or legacy `judge_info` JSON must fail closed rather than grant AC eligibility.
- A different user or different question's AC must not grant access; access-service tests cover both dimensions.
- Editing published content must remove it from public results until re-approved; service tests pin the state transition.
- Search, empty results, rejected drafts and deleted rows must not leak hidden content; page-service tests cover every status.

---

### Task 1: Database schema and domain entities

**Files:**
- Modify: `spingboot-init/src/main/resources/db/schema.sql`
- Create: `spingboot-init/src/main/resources/db/migrations/20261114_question_solution.sql`
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/entity/QuestionSolution.java`
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/entity/QuestionSolutionLike.java`
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/entity/QuestionSolutionComment.java`
- Create: matching mapper interfaces under `spingboot-init/src/main/java/com/spingbootinit/mapper/`
- Test: `spingboot-init/src/test/java/com/spingbootinit/schema/QuestionSolutionSchemaTest.java`

**Interfaces:**
- Produces: three table/entity mappings and constants `DRAFT=0`, `PENDING=1`, `PUBLISHED=2`, `REJECTED=3`.

- [ ] Write a failing schema test asserting all three tables, the review columns, unique author/question key and indexes exist in both schema files.
- [ ] Run `mvn -Dtest=QuestionSolutionSchemaTest test` and confirm failure because the DDL is absent.
- [ ] Add idempotent DDL, entities and mappers with string-safe ID serialization.
- [ ] Run the schema test and confirm it passes.

### Task 2: Eligibility and response redaction

**Files:**
- Create: `spingboot-init/src/main/java/com/spingbootinit/service/QuestionSolutionAccessService.java`
- Create: `spingboot-init/src/main/java/com/spingbootinit/service/impl/QuestionSolutionAccessServiceImpl.java`
- Create: `spingboot-init/src/main/java/com/spingbootinit/model/vo/question/solution/SolutionEligibilityVO.java`
- Create: `spingboot-init/src/test/java/com/spingbootinit/service/QuestionSolutionAccessServiceTest.java`

**Interfaces:**
- Produces: `boolean hasAccepted(long userId, long questionId)`, `AccessDecision decide(Long userId, long questionId)`, and eligibility VO fields `accepted`, `canWrite`, `canViewFull`, `admin`.

- [ ] Write failing tests for exact-question AC, status-2 non-Accepted, malformed JSON, another user, another question and admin bypass.
- [ ] Run the focused test and verify the missing service failure.
- [ ] Implement a fail-closed AC query and role lookup based only on authenticated user data.
- [ ] Run the focused test and the existing submission tests.

### Task 3: Solution authoring, moderation and read APIs

**Files:**
- Create: DTO/VO classes under `model/dto/question/solution/` and `model/vo/question/solution/`
- Create: `service/QuestionSolutionService.java`
- Create: `service/impl/QuestionSolutionServiceImpl.java`
- Create: `controller/QuestionSolutionController.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/controller/QuestionSolutionControllerTest.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/service/QuestionSolutionServiceTest.java`

**Interfaces:**
- Consumes: Task 1 entities/mappers and Task 2 access decisions.
- Produces: eligibility, public page, detail, mine, save, submit-review, delete, admin review and official endpoints; detail VO uses `accessLevel: HINT | FULL` and nullable `content`.

- [ ] Write failing controller/service tests for anonymous hints, non-AC redaction, AC full detail, create denial, draft/pending filtering, author ownership, review transitions and admin-only operations.
- [ ] Run the focused tests and verify failures are caused by missing APIs.
- [ ] Implement validation, pagination/search, redacted projections, author workflow and moderation transactions.
- [ ] Re-run focused tests and existing controller tests.

### Task 4: Likes and gated comments

**Files:**
- Create: `service/QuestionSolutionInteractionService.java`
- Create: `service/impl/QuestionSolutionInteractionServiceImpl.java`
- Extend: `controller/QuestionSolutionController.java`
- Create: interaction DTO/VO classes under the question solution packages
- Test: `spingboot-init/src/test/java/com/spingbootinit/service/QuestionSolutionInteractionServiceTest.java`

**Interfaces:**
- Consumes: published solution lookup and Task 2 access decisions.
- Produces: transactional like toggle and paged first-level/reply comments.

- [ ] Write failing tests for non-AC denial, duplicate like prevention, unlike decrement, comment ownership/admin deletion and counter consistency.
- [ ] Run focused tests and confirm expected failures.
- [ ] Implement transactions and counter updates with unique-key conflict handling.
- [ ] Run focused tests and the backend suite.

### Task 5: Frontend API contract and editorial panel

**Files:**
- Create: `前端/myoj/src/api/questionSolution.ts`
- Create: `前端/myoj/src/views/problem/ProblemSolutionPanel.vue`
- Modify: `前端/myoj/src/views/problem/ProblemSolveView.vue`
- Modify: `前端/myoj/scripts/check-id-contract.mjs`
- Create: `前端/myoj/scripts/check-question-solution.mjs`
- Modify: `前端/myoj/package.json`

**Interfaces:**
- Consumes: Task 3/4 REST endpoints.
- Produces: a self-contained solution list/detail/editor component receiving `questionId: string` and `isAdmin: boolean`.

- [ ] Write a failing static contract check for string IDs, the absence of full content from hint rendering, AC-locked authoring copy, search and review status UI.
- [ ] Run `node scripts/check-question-solution.mjs` and confirm failure.
- [ ] Implement typed API calls and the Saikr-inspired list with official-first cards, hint/full detail, Markdown rendering, editor, review status, likes and comments.
- [ ] Replace the placeholder solution pane with `ProblemSolutionPanel` and keep the main solve file responsible only for orchestration.
- [ ] Run the new check, type-check and production build.

### Task 6: Deployment documentation and complete verification

**Files:**
- Modify: `README.md`
- Create: `docs/QUESTION_SOLUTION_DEPLOY.md`

**Interfaces:**
- Consumes: migration from Task 1 and deployable backend/frontend from Tasks 3-5.
- Produces: backup, SQL execution, backend-first/frontend-second deployment and rollback instructions.

- [ ] Document the exact migration command, verification queries, safe handling of the uploaded SQL file, application rollout order and rollback boundary.
- [ ] Run `mvn test`, `mvn clean package -DskipTests`, frontend `npm run build`, and inspect the resulting SQL/table names.
- [ ] Manually audit that every non-AC response builder leaves `content` and comments absent.
- [ ] Record any environment-only end-to-end checks that still require the user's live MySQL/server as unverified rather than claiming completion.
