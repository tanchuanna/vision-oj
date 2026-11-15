<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { Message } from '@arco-design/web-vue'
import { OpenAPI } from '@generated'
import AdminUsersPanel from '@/views/account/AdminUsersPanel.vue'

type DirectoryOption = { id: string; username?: string; nickname?: string; status?: number }
type DirectoryResult = { records: DirectoryOption[]; total: number }
const role = ref<'user' | 'teacher'>('user')
const teacherId = ref('')
const studentId = ref('')
const teachers = ref<DirectoryOption[]>([])
const students = ref<DirectoryOption[]>([])
const teacherTotal = ref(0)
const studentTotal = ref(0)
const directoryLoading = ref(false)
const relationBusy = ref(false)

const enabledTeachers = computed(() => teachers.value.filter((item) => item.status !== 0))
const enabledStudents = computed(() => students.value.filter((item) => item.status !== 0))
function optionName(item: DirectoryOption) {
  return item.nickname?.trim() || item.username?.trim() || `用户 ${item.id}`
}

async function getAuthHeaders() {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

async function loadRoleDirectory(targetRole: 'user' | 'teacher'): Promise<DirectoryResult> {
  const base = OpenAPI.BASE.replace(/\/+$/, '')
  const { data } = await axios.get(`${base}/user/admin/page`, {
    params: { current: 1, size: 200, role: targetRole },
    headers: await getAuthHeaders(),
  })
  if (data?.code !== 0 && data?.code !== 200) throw new Error(data?.message || '用户目录加载失败')
  const records = Array.isArray(data?.data?.records) ? (data.data.records as DirectoryOption[]) : []
  const total = typeof data?.data?.total === 'number' ? data.data.total : records.length
  return { records, total }
}
async function loadDirectories() {
  directoryLoading.value = true
  try {
    const [teacherRes, studentRes] = await Promise.all([
      loadRoleDirectory('teacher'),
      loadRoleDirectory('user'),
    ])
    teachers.value = teacherRes.records
    students.value = studentRes.records
    teacherTotal.value = teacherRes.total
    studentTotal.value = studentRes.total
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '师生目录加载失败')
  } finally {
    directoryLoading.value = false
  }
}
async function updateRelation(bind: boolean) {
  if (!teacherId.value || !studentId.value) {
    Message.warning('请先选择老师和学生')
    return
  }
  relationBusy.value = true
  try {
    const url = `${OpenAPI.BASE.replace(/\/+$/, '')}/teaching/admin/teachers/${teacherId.value}/students/${studentId.value}`
    const headers = await getAuthHeaders()
    if (bind) await axios.post(url, {}, { headers })
    else await axios.delete(url, { headers })
    Message.success(bind ? '已将学生分配给该老师' : '已解除师生分配')
  } catch (error) {
    const message = axios.isAxiosError(error) ? error.response?.data?.message : ''
    Message.error(message || '操作失败，请确认账号角色和当前分配关系')
  } finally {
    relationBusy.value = false
  }
}
onMounted(loadDirectories)
</script>

<template>
  <main class="user-admin-page">
    <section class="admin-heading">
      <div>
        <div class="heading-kicker"><span />系统管理</div>
        <h1>成员与权限</h1>
        <p>管理学生、老师的角色与账号状态，并维护老师负责的学生范围。</p>
      </div>
      <span class="scope-chip">管理员 · 全站范围</span>
    </section>

    <nav class="role-tabs" aria-label="成员类型">
      <button type="button" :class="{ active: role === 'user' }" @click="role = 'user'">
        <span class="tab-icon">学</span
        ><span
          ><strong>学生管理</strong><small>共 {{ studentTotal }} 名学生</small></span
        >
      </button>
      <button type="button" :class="{ active: role === 'teacher' }" @click="role = 'teacher'">
        <span class="tab-icon tab-icon--teacher">师</span
        ><span
          ><strong>老师管理</strong><small>共 {{ teacherTotal }} 名老师</small></span
        >
      </button>
    </nav>

    <section v-if="role === 'teacher'" class="relation-panel">
      <header>
        <div>
          <span class="section-icon">↔</span>
          <div>
            <h2>师生分配</h2>
            <p>一名学生可以由多位老师共同负责；老师只能查看已分配学生的学习数据。</p>
          </div>
        </div>
        <button
          type="button"
          class="refresh-button"
          :disabled="directoryLoading"
          @click="loadDirectories"
        >
          {{ directoryLoading ? '刷新中…' : '刷新名单' }}
        </button>
      </header>
      <div class="relation-form">
        <label
          ><span>选择老师</span>
          <a-select
            v-model="teacherId"
            allow-search
            allow-clear
            placeholder="输入姓名或用户名搜索"
            :loading="directoryLoading"
          >
            <a-option
              v-for="item in enabledTeachers"
              :key="item.id"
              :value="String(item.id)"
              :label="`${optionName(item)} · @${item.username || item.id}`"
            />
          </a-select>
        </label>
        <span class="relation-arrow" aria-hidden="true">→</span>
        <label
          ><span>选择学生</span>
          <a-select
            v-model="studentId"
            allow-search
            allow-clear
            placeholder="输入姓名或用户名搜索"
            :loading="directoryLoading"
          >
            <a-option
              v-for="item in enabledStudents"
              :key="item.id"
              :value="String(item.id)"
              :label="`${optionName(item)} · @${item.username || item.id}`"
            />
          </a-select>
        </label>
        <div class="relation-actions">
          <button
            type="button"
            class="primary-button"
            :disabled="relationBusy || !teacherId || !studentId"
            @click="updateRelation(true)"
          >
            确认分配
          </button>
          <button
            type="button"
            class="secondary-button"
            :disabled="relationBusy || !teacherId || !studentId"
            @click="updateRelation(false)"
          >
            解除关系
          </button>
        </div>
      </div>
      <p class="relation-tip">选择姓名即可完成操作，不需要再复制或记忆用户 ID。</p>
    </section>

    <section class="directory-shell">
      <div class="directory-title">
        <div>
          <h2>{{ role === 'user' ? '学生账号目录' : '老师账号目录' }}</h2>
          <p>
            {{
              role === 'user'
                ? '查看学生账号状态、异常提醒并调整为老师。'
                : '查看老师账号状态、维护任教身份与账号可用性。'
            }}
          </p>
        </div>
      </div>
      <AdminUsersPanel :role-filter="role" :is-light="true" @directory-changed="loadDirectories" />
    </section>
  </main>
</template>

<style scoped>
.user-admin-page {
  --ink: #172326;
  --muted: #687774;
  --line: #dde6e3;
  --teal: #0d8e81;
  max-width: 1500px;
  margin: 0 auto;
  padding: 26px 28px 48px;
  color: var(--ink);
}
.admin-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 18px;
}
.heading-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--teal);
  font-size: 12px;
  font-weight: 750;
  letter-spacing: 0.12em;
}
.heading-kicker span {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #20ad9d;
}
.admin-heading h1 {
  margin: 7px 0 5px;
  font-size: 30px;
  letter-spacing: -0.03em;
}
.admin-heading p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}
.scope-chip {
  border: 1px solid #b9ddd7;
  border-radius: 999px;
  background: #edf8f6;
  color: #187b71;
  padding: 8px 13px;
  font-size: 13px;
  font-weight: 650;
}
.role-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 240px));
  gap: 10px;
  margin-bottom: 16px;
}
.role-tabs button {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 66px;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
}
.role-tabs button.active {
  border-color: #73bdb3;
  background: #f0f9f7;
  box-shadow: inset 0 -3px 0 #159889;
}
.tab-icon {
  display: grid;
  place-items: center;
  width: 37px;
  height: 37px;
  border-radius: 11px;
  background: #e3f4f0;
  color: #0b8175;
  font-weight: 800;
}
.tab-icon--teacher {
  background: #fff1e1;
  color: #b56518;
}
.role-tabs button > span:last-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.role-tabs strong {
  font-size: 14px;
}
.role-tabs small {
  color: #7a8885;
  font-size: 11px;
}
.relation-panel,
.directory-shell {
  border: 1px solid var(--line);
  border-radius: 17px;
  background: #fff;
}
.relation-panel {
  margin-bottom: 16px;
  padding: 18px 20px;
}
.relation-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}
.relation-panel header > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.section-icon {
  display: grid;
  place-items: center;
  width: 39px;
  height: 39px;
  border-radius: 11px;
  background: #e7f5f2;
  color: #128779;
  font-size: 19px;
}
.relation-panel h2,
.directory-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
}
.relation-panel p,
.directory-title p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.refresh-button,
.primary-button,
.secondary-button {
  min-height: 42px;
  border-radius: 10px;
  padding: 0 14px;
  font-weight: 650;
  cursor: pointer;
}
.refresh-button,
.secondary-button {
  border: 1px solid #ccd8d5;
  background: #fff;
  color: #445451;
}
.primary-button {
  border: 1px solid var(--teal);
  background: var(--teal);
  color: #fff;
}
.relation-form {
  display: grid;
  grid-template-columns: minmax(240px, 1fr) 34px minmax(240px, 1fr) auto;
  align-items: end;
  gap: 10px;
}
.relation-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: #44514f;
  font-size: 12px;
  font-weight: 650;
}
.relation-form :deep(.arco-select-view-single) {
  min-height: 42px;
  border-radius: 10px;
  background: #fff;
}
.relation-arrow {
  display: grid;
  place-items: center;
  height: 42px;
  color: #178c80;
  font-size: 20px;
}
.relation-actions {
  display: flex;
  gap: 8px;
}
.relation-tip {
  margin-top: 12px !important;
  padding-top: 11px;
  border-top: 1px solid #edf1f0;
  color: #73817e !important;
}
.relation-form button:disabled,
.refresh-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.directory-shell {
  overflow: hidden;
}
.directory-title {
  padding: 18px 21px 14px;
  border-bottom: 1px solid var(--line);
}
.directory-shell :deep(.admin-users-panel) {
  padding: 18px 20px 22px;
  border: 0 !important;
  border-radius: 0 !important;
  box-shadow: none !important;
}
@media (max-width: 1000px) {
  .relation-form {
    grid-template-columns: 1fr 28px 1fr;
  }
  .relation-actions {
    grid-column: 1/-1;
    justify-content: flex-end;
  }
}
@media (max-width: 680px) {
  .user-admin-page {
    padding: 18px 14px 36px;
  }
  .admin-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .admin-heading h1 {
    font-size: 25px;
  }
  .scope-chip {
    align-self: flex-start;
  }
  .role-tabs {
    grid-template-columns: 1fr 1fr;
  }
  .role-tabs button {
    padding: 9px;
  }
  .role-tabs small {
    display: none;
  }
  .relation-panel {
    padding: 15px;
  }
  .relation-panel header {
    align-items: flex-start;
    flex-direction: column;
  }
  .relation-form {
    grid-template-columns: 1fr;
  }
  .relation-arrow {
    height: 20px;
    transform: rotate(90deg);
  }
  .relation-actions {
    grid-column: auto;
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .directory-title {
    padding: 16px;
  }
}
</style>
