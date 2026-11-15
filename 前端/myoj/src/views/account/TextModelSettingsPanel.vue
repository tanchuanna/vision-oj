<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { aiModelsApi } from '@/api/aiModels'
import { useAiModelsStore } from '@/stores/aiModels'
import ProviderKeyGuide from './ProviderKeyGuide.vue'

const props = defineProps<{ isLight: boolean }>()
const models = useAiModelsStore()
const providerCode = ref('DASHSCOPE')
const modelId = ref('')
const customModelId = ref('')
const customMode = ref(false)
const search = ref('')
const apiKey = ref('')
const showKey = ref(false)
const busy = ref(false)
const errorMessage = ref('')
const testedModel = ref('')
const failedIcons = ref<string[]>([])

const provider = computed(() =>
  models.catalog.providers.find((item) => item.code === providerCode.value),
)
const configured = computed(() =>
  models.saved.find((item) => item.providerCode === providerCode.value),
)
const visibleModels = computed(() =>
  (provider.value?.models || []).filter((id) =>
    id.toLowerCase().includes(search.value.trim().toLowerCase()),
  ),
)
const providerInfo: Record<
  string,
  { shortName: string; description: string; icon: string; kind: string }
> = {
  DASHSCOPE: {
    shortName: '百炼',
    description: '千问系列 · 中文创作与通用问答',
    icon: '/ai-providers/dashscope.png',
    kind: '厂商直连',
  },
  DEEPSEEK: {
    shortName: 'DeepSeek',
    description: '推理、编程与知识问答',
    icon: '/ai-providers/deepseek.png',
    kind: '厂商直连',
  },
  SILICONFLOW: {
    shortName: '硅基',
    description: '一个账号可选多家国内模型',
    icon: '/ai-providers/siliconflow.png',
    kind: '聚合平台',
  },
  VOLCENGINE: {
    shortName: '方舟',
    description: '豆包系列模型',
    icon: '/ai-providers/volcengine.png',
    kind: '厂商直连',
  },
  BAIDU_QIANFAN: {
    shortName: '千帆',
    description: '文心系列模型',
    icon: '/ai-providers/baidu.png',
    kind: '厂商直连',
  },
  ZHIPU: {
    shortName: '智谱',
    description: 'GLM 系列模型',
    icon: '/ai-providers/zhipu.png',
    kind: '厂商直连',
  },
  STEPFUN: {
    shortName: '阶跃',
    description: 'Step 系列模型',
    icon: '/ai-providers/stepfun.svg',
    kind: '厂商直连',
  },
  MOONSHOT: {
    shortName: 'Kimi',
    description: 'Kimi 系列模型',
    icon: '/ai-providers/kimi.png',
    kind: '厂商直连',
  },
  TENCENT_TOKENHUB: {
    shortName: '腾讯',
    description: '混元及其他国内模型',
    icon: '/ai-providers/tencent.png',
    kind: '聚合平台',
  },
}

function syncModel() {
  const preferred = configured.value?.modelId || provider.value?.models[0] || ''
  customMode.value = Boolean(preferred && !provider.value?.models.includes(preferred))
  customModelId.value = customMode.value ? preferred : ''
  modelId.value = preferred
}

function selectProvider(code: string) {
  providerCode.value = code
  apiKey.value = ''
  showKey.value = false
  search.value = ''
  testedModel.value = ''
  errorMessage.value = ''
  syncModel()
}

function selectModel(id: string) {
  customMode.value = false
  modelId.value = id
  testedModel.value = ''
}

function selectCustom() {
  customMode.value = true
  modelId.value = customModelId.value.trim()
  testedModel.value = ''
}

watch(customModelId, (value) => {
  if (customMode.value) modelId.value = value.trim()
})

async function save() {
  if (!modelId.value.trim()) return void Message.warning('请先选择或填写模型 ID')
  if (!configured.value && !apiKey.value.trim())
    return void Message.warning('首次配置请填写 API Key')
  busy.value = true
  errorMessage.value = ''
  try {
    await aiModelsApi.save(providerCode.value, modelId.value.trim(), apiKey.value.trim())
    apiKey.value = ''
    await models.refresh()
    models.select(providerCode.value, modelId.value.trim())
    testedModel.value = ''
    Message.success('文字模型配置已保存')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

async function testConnection() {
  if (!configured.value || !modelId.value.trim()) return void Message.warning('请先保存模型配置')
  busy.value = true
  errorMessage.value = ''
  try {
    await aiModelsApi.test(providerCode.value, modelId.value.trim())
    testedModel.value = modelId.value.trim()
    Message.success('连接成功，这个模型可以使用')
  } catch (error) {
    testedModel.value = ''
    errorMessage.value = error instanceof Error ? error.message : '连接失败，请稍后重试'
  } finally {
    busy.value = false
  }
}

function remove() {
  if (!configured.value) return
  Modal.confirm({
    title: '删除文字模型配置？',
    content: '删除后该平台的个人 API Key 无法恢复。',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      busy.value = true
      try {
        await aiModelsApi.remove(providerCode.value)
        await models.refresh()
        apiKey.value = ''
        testedModel.value = ''
        syncModel()
        Message.success('已删除文字模型配置')
      } catch (error) {
        Message.error(error instanceof Error ? error.message : '删除失败')
      } finally {
        busy.value = false
      }
    },
  })
}

onMounted(() => {
  void models
    .load(true)
    .then(() => {
      if (!models.catalog.providers.some((item) => item.code === providerCode.value)) {
        providerCode.value = models.catalog.providers[0]?.code || ''
      }
      syncModel()
    })
    .catch((error: unknown) => {
      errorMessage.value = error instanceof Error ? error.message : '文字模型配置加载失败'
    })
})
</script>

<template>
  <div class="text-settings" :class="{ 'is-light': props.isLight }">
    <section class="text-hero">
      <div>
        <span class="eyebrow">账户设置 / 文字模型</span>
        <h3>选择文字模型</h3>
        <p>连接自己的模型账号，用于做题助手和辅助出题。</p>
      </div>
      <span class="hero-count">{{ models.catalog.providers.length }} 家模型平台</span>
    </section>

    <p v-if="errorMessage" class="state state--error" role="alert">{{ errorMessage }}</p>
    <p v-if="models.loading" class="state">正在加载文字模型…</p>
    <p v-else-if="!models.catalog.providers.length" class="state">暂无可选平台，请检查后端服务。</p>
    <template v-else>
      <div class="section-heading">
        <div>
          <span>01 / 模型平台</span>
          <h4>选择要连接的厂商</h4>
        </div>
        <small>个人配置按账号分别保存</small>
      </div>
      <div class="provider-grid">
        <button
          v-for="item in models.catalog.providers"
          :key="item.code"
          type="button"
          class="provider-card"
          :class="{ active: item.code === providerCode }"
          :aria-pressed="item.code === providerCode"
          @click="selectProvider(item.code)"
        >
          <span class="provider-icon" :class="{ 'provider-icon--dark': item.code === 'STEPFUN' }">
            <img
              v-if="providerInfo[item.code]?.icon && !failedIcons.includes(item.code)"
              :src="providerInfo[item.code]?.icon"
              :alt="`${item.name}图标`"
              @error="failedIcons.push(item.code)"
            />
            <span v-else>{{ providerInfo[item.code]?.shortName || item.name }}</span>
          </span>
          <span class="provider-copy"
            ><strong>{{ item.name }}</strong
            ><small>{{ providerInfo[item.code]?.description || '文字模型平台' }}</small></span
          >
          <span class="provider-kind">{{ providerInfo[item.code]?.kind || '厂商直连' }}</span>
          <span
            class="provider-status"
            :class="{ ready: models.saved.some((entry) => entry.providerCode === item.code) }"
            >{{
              models.saved.some((entry) => entry.providerCode === item.code) ? '已配置' : '未配置'
            }}</span
          >
          <span class="provider-foot"
            >{{ item.models.length }} 个推荐模型 <span aria-hidden="true">↗</span></span
          >
        </button>
      </div>

      <div class="section-heading">
        <div>
          <span>02 / 模型选择</span>
          <h4>{{ provider?.name }} · 文字模型</h4>
        </div>
        <small>也可填写厂商提供的其他模型 ID</small>
      </div>
      <div class="model-toolbar">
        <input
          v-model="search"
          type="search"
          placeholder="搜索模型名称"
          aria-label="搜索文字模型"
        /><span>{{ visibleModels.length }} 个推荐模型</span>
      </div>
      <div class="model-grid">
        <button
          v-for="id in visibleModels"
          :key="id"
          type="button"
          class="model-card"
          :class="{ active: !customMode && modelId === id }"
          :aria-pressed="!customMode && modelId === id"
          @click="selectModel(id)"
        >
          <span class="model-card-top"
            ><strong>{{ id }}</strong
            ><span>{{ testedModel === id ? '本次测试通过' : '推荐模型' }}</span></span
          >
          <small>{{ provider?.name }} · 对话与内容生成</small><code>{{ id }}</code>
        </button>
        <button
          type="button"
          class="model-card custom-card"
          :class="{ active: customMode }"
          :aria-pressed="customMode"
          @click="selectCustom"
        >
          <span class="model-card-top"><strong>自定义模型</strong><span>其他 ID</span></span>
          <small>已在厂商开通其他模型时使用</small
          ><code>{{ customMode && modelId ? modelId : '填写模型 ID' }}</code>
        </button>
      </div>
      <label v-if="customMode" class="custom-input"
        >模型 ID <input v-model="customModelId" maxlength="100" placeholder="输入厂商提供的模型 ID"
      /></label>

      <div class="section-heading">
        <div>
          <span>03 / 个人密钥</span>
          <h4>连接 {{ provider?.name }}</h4>
        </div>
        <small>模型调用可能产生厂商费用</small>
      </div>
      <section class="credential-card">
        <div class="credential-head">
          <div>
            <strong>API Key</strong>
            <p v-if="configured">已保存 {{ configured.keyMask }}</p>
            <p v-else>尚未配置，请输入个人 API Key</p>
          </div>
          <span class="credential-dot" :class="{ ready: configured }" />
        </div>
        <ProviderKeyGuide
          :provider-code="providerCode"
          :provider-name="provider?.name || providerCode"
          :configured="Boolean(configured)"
          :is-light="isLight"
        />
        <div class="credential-input">
          <input
            v-model="apiKey"
            :type="showKey ? 'text' : 'password'"
            autocomplete="off"
            spellcheck="false"
            :placeholder="configured ? '留空保留已有密钥' : '输入个人 API Key'"
          /><button type="button" @click="showKey = !showKey">
            {{ showKey ? '隐藏' : '显示' }}
          </button>
        </div>
        <div class="credential-actions">
          <button type="button" class="primary-action" :disabled="busy" @click="save">
            {{ busy ? '处理中…' : '保存配置' }}</button
          ><button
            type="button"
            :disabled="busy || !configured || !modelId"
            @click="testConnection"
          >
            测试连接</button
          ><button
            v-if="configured"
            type="button"
            class="danger-action"
            :disabled="busy"
            @click="remove"
          >
            删除配置
          </button>
        </div>
      </section>
      <p class="footer-note">
        做题助手和教师 AI 出题可选择已保存的平台；未选择时使用站点默认模型。
      </p>
    </template>
  </div>
</template>

<style scoped>
.text-settings {
  --tm-text: #f8fafc;
  --tm-muted: #a6b5c8;
  --tm-surface: rgba(15, 23, 42, 0.56);
  --tm-border: rgba(148, 163, 184, 0.26);
  --tm-accent: #34d3ba;
  color: var(--tm-text);
}
.text-settings.is-light {
  --tm-text: #172033;
  --tm-muted: #64748b;
  --tm-surface: #fff;
  --tm-border: rgba(15, 23, 42, 0.13);
  --tm-accent: #0d9488;
}
.text-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 26px;
  border: 1px solid var(--tm-border);
  border-radius: 18px;
  background: var(--tm-surface);
}
.eyebrow,
.section-heading span {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--tm-accent);
}
.text-hero h3 {
  margin: 8px 0 7px;
  font-size: 25px;
  line-height: 1.25;
}
.text-hero p {
  margin: 0;
  max-width: 540px;
  color: var(--tm-muted);
  font-size: 13px;
  line-height: 1.65;
}
.hero-count {
  flex: none;
  padding: 8px 11px;
  border-radius: 999px;
  background: rgba(45, 212, 191, 0.13);
  color: var(--tm-accent);
  font-size: 12px;
  font-weight: 750;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 12px;
  margin: 29px 0 14px;
}
.section-heading h4 {
  margin: 5px 0 0;
  font-size: 17px;
}
.section-heading small {
  color: var(--tm-muted);
  font-size: 12px;
}
.provider-grid,
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.provider-card,
.model-card {
  color: var(--tm-text);
  text-align: left;
  border: 1px solid var(--tm-border);
  background: var(--tm-surface);
  cursor: pointer;
  transition:
    transform 0.18s,
    border-color 0.18s,
    box-shadow 0.18s;
}
.provider-card:hover,
.model-card:hover {
  transform: translateY(-2px);
  border-color: var(--tm-accent);
}
.provider-card:focus-visible,
.model-card:focus-visible {
  outline: 2px solid var(--tm-accent);
  outline-offset: 3px;
}
.provider-card.active,
.model-card.active {
  border-color: var(--tm-accent);
  box-shadow: 0 0 0 2px rgba(45, 212, 191, 0.13);
}
.provider-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto;
  align-items: start;
  column-gap: 12px;
  row-gap: 10px;
  min-height: 143px;
  padding: 17px;
  border-radius: 17px;
}
.provider-icon {
  width: 46px;
  height: 46px;
  overflow: hidden;
  border-radius: 13px;
  background: #fff;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 11px;
  font-weight: 800;
}
.provider-icon:has(> span) {
  background: #0f766e;
}
.provider-icon img {
  width: 32px;
  height: 32px;
  object-fit: contain;
}
.provider-icon--dark {
  background: #1e293b;
}
.provider-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.provider-copy strong {
  font-size: 14px;
}
.provider-copy small {
  color: var(--tm-muted);
  font-size: 11px;
  line-height: 1.45;
}
.provider-kind {
  grid-column: 2;
  align-self: end;
  width: max-content;
  color: var(--tm-muted);
  font-size: 11px;
}
.provider-status {
  grid-column: 3;
  grid-row: 1;
  width: max-content;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--tm-muted);
  background: rgba(148, 163, 184, 0.13);
  font-size: 11px;
  font-weight: 750;
}
.provider-status.ready {
  color: var(--tm-accent);
  background: rgba(20, 184, 166, 0.13);
}
.provider-foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  color: var(--tm-muted);
  font-size: 11px;
}
.model-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--tm-muted);
  font-size: 12px;
}
.model-toolbar input,
.custom-input input,
.credential-input input {
  box-sizing: border-box;
  width: 100%;
  min-height: 43px;
  padding: 10px 13px;
  border: 1px solid var(--tm-border);
  border-radius: 11px;
  background: var(--tm-surface);
  color: var(--tm-text);
  font: inherit;
}
.model-toolbar input {
  max-width: 310px;
}
.model-toolbar input:focus,
.custom-input input:focus,
.credential-input input:focus {
  outline: 2px solid rgba(20, 184, 166, 0.35);
}
.model-card {
  display: flex;
  flex-direction: column;
  gap: 9px;
  min-height: 113px;
  padding: 16px;
  border-radius: 14px;
}
.model-card-top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
}
.model-card-top strong {
  font-size: 14px;
}
.model-card-top span {
  flex: none;
  color: var(--tm-accent);
  font-size: 10px;
  font-weight: 750;
}
.model-card small {
  color: var(--tm-muted);
  font-size: 12px;
}
.model-card code {
  color: var(--tm-muted);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.custom-input {
  display: grid;
  gap: 8px;
  margin-top: 13px;
  color: var(--tm-text);
  font-size: 12px;
  font-weight: 700;
}
.credential-card {
  padding: 19px;
  border: 1px solid var(--tm-border);
  border-radius: 17px;
  background: var(--tm-surface);
}
.credential-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.credential-head strong {
  font-size: 14px;
}
.credential-head p {
  margin: 5px 0 0;
  color: var(--tm-muted);
  font-size: 12px;
}
.credential-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #94a3b8;
}
.credential-dot.ready {
  background: #13b89d;
  box-shadow: 0 0 0 4px rgba(19, 184, 157, 0.13);
}
.credential-input {
  position: relative;
  margin-top: 16px;
}
.credential-input input {
  padding-right: 66px;
}
.credential-input button {
  position: absolute;
  right: 7px;
  top: 6px;
  padding: 7px 10px;
  border: 0;
  background: transparent;
  color: var(--tm-accent);
  cursor: pointer;
}
.credential-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}
.credential-actions button {
  padding: 9px 15px;
  border: 1px solid var(--tm-border);
  border-radius: 10px;
  background: var(--tm-surface);
  color: var(--tm-text);
  cursor: pointer;
  font-weight: 700;
}
.credential-actions button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}
.credential-actions .primary-action {
  border: 0;
  color: #fff;
  background: #0d9488;
}
.credential-actions .danger-action {
  color: #dc665d;
}
.state {
  padding: 22px;
  border: 1px dashed var(--tm-border);
  border-radius: 14px;
  color: var(--tm-muted);
  font-size: 13px;
}
.state--error {
  border-style: solid;
  border-color: rgba(220, 80, 67, 0.3);
  color: #dd776c;
  background: rgba(220, 80, 67, 0.07);
}
.footer-note {
  margin: 18px 0 0;
  color: var(--tm-muted);
  font-size: 12px;
  line-height: 1.65;
}
@media (max-width: 850px) {
  .provider-grid,
  .model-grid {
    grid-template-columns: 1fr;
  }
  .provider-card {
    min-height: 0;
  }
}
@media (max-width: 560px) {
  .text-hero {
    flex-direction: column;
    padding: 20px;
  }
  .text-hero h3 {
    font-size: 21px;
  }
  .section-heading {
    align-items: start;
    flex-direction: column;
  }
  .model-toolbar input {
    max-width: none;
  }
}
</style>
