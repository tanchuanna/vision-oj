# Management Console Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复本地热重启的 SSE 端口占用问题，并将学生管理和用户管理统一为现代教育管理台。

**Architecture:** 后端在应用关闭时主动完成账号状态 SSE 连接，避免 DevTools 热重启遗留活动请求。前端复用现有接口和 Arco 组件，仅重组两个管理页面的信息层级、筛选、统计和师生分配交互。

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Vue 3, TypeScript, Arco Design, CSS

**Spec:** `docs/superpowers/specs/2026-11-15-management-console-design.md`

## Global Constraints

- 不新增数据库表或新 UI 框架。
- 保留现有三角色权限和后端鉴权。
- 保留现有用户详情、头像预览、禁用和解除能力。
- 页面兼容桌面与手机布局。

## Review Focus

- DevTools 热重启时存在多个 SSE 连接，旧端口必须能释放。
- 空学生列表和空用户列表必须给出明确下一步。
- 师生分配必须阻止空选择或同角色错误组合。
- 超长昵称、用户名和雪花 ID 不得撑破卡片。
- 禁用和角色切换不能因视觉重组绕过现有确认流程。

---

### Task 1: SSE shutdown cleanup

**Files:**
- Modify: `spingboot-init/src/main/java/com/spingbootinit/service/impl/UserStatusNotifyServiceImpl.java`
- Test: `spingboot-init/src/test/java/com/spingbootinit/service/UserStatusNotifyServiceTest.java`

**Interfaces:**
- Produces: `closeAllEmitters()` lifecycle cleanup behavior.

- [ ] Write a failing test proving shutdown completes and removes all registered emitters.
- [ ] Run the targeted test and confirm it fails because cleanup is absent.
- [ ] Add lifecycle cleanup to complete every emitter and clear the registry.
- [ ] Run the targeted test and backend suite.

### Task 2: Student management experience

**Files:**
- Modify: `前端/myoj/src/views/teaching/TeachingStudentsView.vue`

**Interfaces:**
- Consumes: `GET /teaching/students/page` response.

- [ ] Add summary metrics, clear search/reset behavior, active-status formatting, skeleton and empty states.
- [ ] Rebuild student cards with accessible actions and responsive layout.
- [ ] Run Vue type checking.

### Task 3: User directory and relationship assignment

**Files:**
- Modify: `前端/myoj/src/views/admin/AdminUserManagementView.vue`
- Modify: `前端/myoj/src/views/account/AdminUsersPanel.vue`

**Interfaces:**
- Consumes: role-filtered admin user page and existing bind/unbind endpoints.

- [ ] Replace ID-only relationship form with current-list user selection plus optional manual ID fallback.
- [ ] Unify page header, tabs, statistics and directory cards with the student page.
- [ ] Preserve detail drawer, avatar preview and safety actions.
- [ ] Run Vue type checking and production build.

### Task 4: Final verification

**Files:**
- Verify all files above.

- [ ] Run backend tests and package.
- [ ] Run frontend type check and production build.
- [ ] Run `git diff --check` on changed files and review mobile breakpoints.

