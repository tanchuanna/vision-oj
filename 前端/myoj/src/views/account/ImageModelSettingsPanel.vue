<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import { aiImageModelsApi, type ImageProvider, type SavedImageConfig } from '@/api/aiImageModels'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import ProviderKeyGuide from './ProviderKeyGuide.vue'

const props = defineProps<{ isLight: boolean }>()
const providers = ref<ImageProvider[]>([])
const saved = ref<SavedImageConfig[]>([])
const selectedCode = ref('DASHSCOPE')
const selectedModel = ref('')
const search = ref('')
const keyInput = ref('')
const showKey = ref(false)
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref('')
const testImageUrl = ref('')
const testedModel = ref('')
const failedIcons = ref<string[]>([])

const provider = computed(() => providers.value.find((item) => item.code === selectedCode.value))
const config = computed(() => saved.value.find((item) => item.providerCode === selectedCode.value))
const visibleModels = computed(() =>
  (provider.value?.models || []).filter((item) =>
    `${item.name} ${item.id} ${item.description}`
      .toLowerCase()
      .includes(search.value.trim().toLowerCase()),
  ),
)
const iconMap: Record<string, string> = {
  DASHSCOPE: '/ai-providers/dashscope.png',
  VOLCENGINE: '/ai-providers/volcengine.png',
  BAIDU_QIANFAN: '/ai-providers/baidu.png',
  SILICONFLOW: '/ai-providers/siliconflow.png',
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [catalog, mine] = await Promise.all([aiImageModelsApi.catalog(), aiImageModelsApi.mine()])
    providers.value = catalog
    saved.value = mine
    if (!catalog.some((item) => item.code === selectedCode.value))
      selectedCode.value = catalog[0]?.code || ''
    syncModel()
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '图片模型配置加载失败'
  } finally {
    loading.value = false
  }
}

function syncModel() {
  const available = provider.value?.models || []
  const preferred = config.value?.modelId
  selectedModel.value =
    available.find((item) => item.id === preferred)?.id || available[0]?.id || ''
}

function selectProvider(code: string) {
  selectedCode.value = code
  keyInput.value = ''
  showKey.value = false
  search.value = ''
  testImageUrl.value = ''
  testedModel.value = ''
  errorMessage.value = ''
  syncModel()
}

function selectModel(id: string) {
  selectedModel.value = id
  testImageUrl.value = ''
  testedModel.value = ''
}

async function save() {
  if (!selectedCode.value || !selectedModel.value) return void Message.warning('请先选择图片模型')
  if (!config.value && !keyInput.value.trim())
    return void Message.warning('首次配置请填写厂商 API Key')
  busy.value = true
  errorMessage.value = ''
  try {
    await aiImageModelsApi.save(selectedCode.value, selectedModel.value, keyInput.value.trim())
    keyInput.value = ''
    saved.value = await aiImageModelsApi.mine()
    testedModel.value = ''
    Message.success('图片模型配置已保存')
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '保存失败'
  } finally {
    busy.value = false
  }
}

function testConnection() {
  if (!config.value || !selectedModel.value) return void Message.warning('请先保存图片厂商配置')
  Modal.confirm({
    title: '生成一张测试图片？',
    content: '这会真实调用所选厂商的图片模型，可能产生费用。测试成功后会在页面显示图片。',
    okText: '确认生成',
    cancelText: '取消',
    async onOk() {
      busy.value = true
      errorMessage.value = ''
      testImageUrl.value = ''
      try {
        const result = await aiImageModelsApi.test(selectedCode.value, selectedModel.value)
        testImageUrl.value = resolveMediaUrl(result.imageUrl)
        testedModel.value = selectedModel.value
        Message.success('测试成功，图片已生成')
      } catch (error) {
        errorMessage.value = error instanceof Error ? error.message : '测试失败'
        Message.error(errorMessage.value)
      } finally {
        busy.value = false
      }
    },
  })
}

function remove() {
  if (!config.value) return
  Modal.confirm({
    title: '删除图片模型配置？',
    content:
      selectedCode.value === 'DASHSCOPE'
        ? '将删除独立图片密钥和模型选择；若保存过百炼文字模型密钥，图片模型仍可复用它。'
        : '将删除该图片厂商的个人密钥，删除后无法恢复。',
    okText: '删除',
    cancelText: '取消',
    async onOk() {
      busy.value = true
      try {
        await aiImageModelsApi.remove(selectedCode.value)
        saved.value = await aiImageModelsApi.mine()
        keyInput.value = ''
        testImageUrl.value = ''
        testedModel.value = ''
        syncModel()
        Message.success('已删除图片模型配置')
      } catch (error) {
        Message.error(error instanceof Error ? error.message : '删除失败')
      } finally {
        busy.value = false
      }
    },
  })
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="image-settings" :class="{ 'is-light': props.isLight }">
    <section class="image-hero">
      <div>
        <span class="image-eyebrow">账户设置 / 图片模型</span>
        <h3>选择图片模型</h3>
        <p>连接自己的国内厂商账号，生成题图后由 YOLO / OCR 识别，并在发布前人工复核。</p>
      </div>
      <span class="image-hero-count">{{ providers.length }} 家平台</span>
    </section>

    <p v-if="loading" class="image-state">正在加载图片模型…</p>
    <template v-else>
      <p v-if="errorMessage" class="image-error" role="alert">{{ errorMessage }}</p>
      <p v-if="!providers.length" class="image-state">暂无已接通的图片厂商，请检查后端服务。</p>

      <template v-else>
        <div class="section-heading">
          <div>
            <span>01 / 图片厂商</span>
            <h4>选择要连接的平台</h4>
          </div>
          <small>密钥只在保存时提交，服务端加密存储</small>
        </div>
        <div class="provider-grid">
          <button
            v-for="item in providers"
            :key="item.code"
            type="button"
            class="provider-card"
            :class="{ 'provider-card--active': item.code === selectedCode }"
            :aria-pressed="item.code === selectedCode"
            @click="selectProvider(item.code)"
          >
            <span class="provider-icon">
              <img
                v-if="iconMap[item.code] && !failedIcons.includes(item.code)"
                :src="iconMap[item.code]"
                :alt="`${item.name}图标`"
                @error="failedIcons.push(item.code)"
              />
              <span v-else>{{ item.shortName }}</span>
            </span>
            <span class="provider-card-copy"
              ><strong>{{ item.name }}</strong
              ><small>{{ item.description }}</small></span
            >
            <span
              class="provider-status"
              :class="{
                'provider-status--ready': saved.some((entry) => entry.providerCode === item.code),
              }"
            >
              {{ saved.some((entry) => entry.providerCode === item.code) ? '已配置' : '未配置' }}
            </span>
            <span class="provider-card-foot"
              >{{ item.models.length }} 个已接入模型 <span aria-hidden="true">↗</span></span
            >
          </button>
        </div>

        <div class="section-heading">
          <div>
            <span>02 / 模型选择</span>
            <h4>{{ provider?.name }} · 文生图</h4>
          </div>
          <small>仅列出已实现调用接口的模型</small>
        </div>
        <div class="model-toolbar">
          <input
            v-model="search"
            type="search"
            placeholder="搜索模型名称或 ID"
            aria-label="搜索图片模型"
          /><span>{{ visibleModels.length }} 个模型</span>
        </div>
        <p v-if="!visibleModels.length" class="image-state">没有匹配的模型</p>
        <div v-else class="model-grid">
          <button
            v-for="item in visibleModels"
            :key="item.id"
            type="button"
            class="model-card"
            :class="{ 'model-card--active': selectedModel === item.id }"
            :aria-pressed="selectedModel === item.id"
            @click="selectModel(item.id)"
          >
            <span class="model-card-top"
              ><strong>{{ item.name }}</strong
              ><span>{{ testedModel === item.id ? '本次测试通过' : '待实测' }}</span></span
            >
            <small>{{ item.description }}</small>
            <code>{{ item.id }}</code>
          </button>
        </div>

        <div class="section-heading">
          <div>
            <span>03 / 个人密钥</span>
            <h4>连接 {{ provider?.name }}</h4>
          </div>
          <small>厂商按自己的账号规则计费</small>
        </div>
        <section class="credential-card">
          <div class="credential-head">
            <div>
              <strong>API Key</strong>
              <p v-if="config">
                已保存 {{ config.keyMask
                }}<span v-if="config.credentialSource === 'TEXT_SHARED'">
                  · 复用百炼文字模型密钥</span
                >
              </p>
              <p v-else>尚未配置，请在厂商控制台创建密钥</p>
            </div>
            <span class="credential-dot" :class="{ 'credential-dot--ready': config }" />
          </div>
          <ProviderKeyGuide
            :provider-code="selectedCode"
            :provider-name="provider?.name || selectedCode"
            :configured="Boolean(config)"
            :is-light="isLight"
          />
          <div class="credential-input">
            <input
              v-model="keyInput"
              :type="showKey ? 'text' : 'password'"
              autocomplete="off"
              spellcheck="false"
              :placeholder="config ? '留空保留已有密钥' : '输入个人 API Key'"
            /><button type="button" @click="showKey = !showKey">
              {{ showKey ? '隐藏' : '显示' }}
            </button>
          </div>
          <div class="credential-actions">
            <button type="button" class="primary-action" :disabled="busy" @click="save">
              {{ busy ? '处理中…' : '保存配置' }}</button
            ><button type="button" :disabled="busy || !config" @click="testConnection">
              生成测试图片</button
            ><button
              v-if="config"
              type="button"
              class="danger-action"
              :disabled="busy"
              @click="remove"
            >
              删除配置
            </button>
          </div>
        </section>

        <div v-if="testImageUrl" class="test-result">
          <div>
            <strong>测试图片</strong><span>{{ provider?.name }} · {{ testedModel }}</span>
          </div>
          <img :src="testImageUrl" alt="图片模型测试生成结果" />
        </div>
        <p class="image-footer-note">
          完整生成一道图像题通常会调用图片模型 3 次（公开题图 1 张、隐藏用例 2
          张），每次可能产生厂商费用；生成后仍需人工核对识别结果。
        </p>
      </template>
    </template>
  </div>
</template>

<style scoped>
.image-settings {
  --im-text: #f8fafc;
  --im-muted: #a6b5c8;
  --im-surface: rgba(15, 23, 42, 0.56);
  --im-border: rgba(148, 163, 184, 0.26);
  --im-teal: #34d3ba;
  color: var(--im-text);
}
.image-settings.is-light {
  --im-text: #172033;
  --im-muted: #64748b;
  --im-surface: #fff;
  --im-border: rgba(15, 23, 42, 0.13);
  --im-teal: #0d9488;
}
.image-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 26px;
  border: 1px solid var(--im-border);
  border-radius: 18px;
  background: var(--im-surface);
}
.image-eyebrow,
.section-heading span {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.14em;
  color: var(--im-teal);
}
.image-hero h3 {
  margin: 8px 0 7px;
  font-size: 25px;
  line-height: 1.25;
}
.image-hero p {
  margin: 0;
  max-width: 540px;
  color: var(--im-muted);
  font-size: 13px;
  line-height: 1.65;
}
.image-hero-count {
  flex: 0 0 auto;
  padding: 8px 11px;
  border-radius: 999px;
  background: rgba(45, 212, 191, 0.13);
  color: var(--im-teal);
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
  color: var(--im-muted);
  font-size: 12px;
}
.provider-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.provider-card,
.model-card {
  color: var(--im-text);
  text-align: left;
  border: 1px solid var(--im-border);
  background: var(--im-surface);
  cursor: pointer;
  transition:
    transform 0.18s,
    border-color 0.18s,
    box-shadow 0.18s;
}
.provider-card:hover,
.model-card:hover {
  transform: translateY(-2px);
  border-color: var(--im-teal);
}
.provider-card:focus-visible,
.model-card:focus-visible {
  outline: 2px solid var(--im-teal);
  outline-offset: 3px;
}
.provider-card--active,
.model-card--active {
  border-color: var(--im-teal);
  box-shadow: 0 0 0 2px rgba(45, 212, 191, 0.13);
}
.provider-card {
  display: grid;
  grid-template-columns: 48px 1fr;
  align-items: start;
  gap: 12px;
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
  font-size: 13px;
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
.provider-card-copy {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.provider-card-copy strong {
  font-size: 14px;
}
.provider-card-copy small {
  color: var(--im-muted);
  font-size: 11px;
  line-height: 1.45;
}
.provider-status {
  grid-column: 1 / -1;
  width: max-content;
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--im-muted);
  background: rgba(148, 163, 184, 0.13);
  font-size: 11px;
  font-weight: 750;
}
.provider-status--ready {
  color: var(--im-teal);
  background: rgba(20, 184, 166, 0.13);
}
.provider-card-foot {
  grid-column: 1 / -1;
  display: flex;
  justify-content: space-between;
  color: var(--im-muted);
  font-size: 11px;
}
.model-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--im-muted);
  font-size: 12px;
}
.model-toolbar input,
.credential-input input {
  box-sizing: border-box;
  width: 100%;
  min-height: 43px;
  padding: 10px 13px;
  border: 1px solid var(--im-border);
  border-radius: 11px;
  background: var(--im-surface);
  color: var(--im-text);
  font: inherit;
}
.model-toolbar input {
  max-width: 310px;
}
.model-toolbar input:focus,
.credential-input input:focus {
  outline: 2px solid rgba(20, 184, 166, 0.35);
}
.model-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
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
  flex: 0 0 auto;
  color: var(--im-teal);
  font-size: 10px;
  font-weight: 750;
}
.model-card small {
  color: var(--im-muted);
  font-size: 12px;
}
.model-card code {
  color: var(--im-muted);
  font-size: 11px;
  overflow-wrap: anywhere;
}
.credential-card {
  padding: 19px;
  border: 1px solid var(--im-border);
  border-radius: 17px;
  background: var(--im-surface);
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
  color: var(--im-muted);
  font-size: 12px;
}
.credential-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #94a3b8;
}
.credential-dot--ready {
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
  color: var(--im-teal);
  cursor: pointer;
}
.image-footer-note {
  color: var(--im-muted);
  font-size: 12px;
  line-height: 1.65;
}
.credential-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}
.credential-actions button {
  padding: 9px 15px;
  border: 1px solid var(--im-border);
  border-radius: 10px;
  background: var(--im-surface);
  color: var(--im-text);
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
.image-state,
.image-error {
  padding: 22px;
  border: 1px dashed var(--im-border);
  border-radius: 14px;
  color: var(--im-muted);
  font-size: 13px;
}
.image-error {
  border-style: solid;
  border-color: rgba(220, 80, 67, 0.3);
  color: #dd776c;
  background: rgba(220, 80, 67, 0.07);
}
.test-result {
  margin-top: 18px;
  padding: 15px;
  border: 1px solid var(--im-border);
  border-radius: 15px;
  background: var(--im-surface);
}
.test-result div {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: baseline;
  margin-bottom: 12px;
}
.test-result strong {
  font-size: 14px;
}
.test-result span {
  color: var(--im-muted);
  font-size: 12px;
}
.test-result img {
  display: block;
  max-width: 100%;
  max-height: 400px;
  border-radius: 10px;
  object-fit: contain;
}
.image-footer-note {
  margin: 18px 0 0;
}
@media (max-width: 850px) {
  .provider-grid {
    grid-template-columns: 1fr;
  }
  .provider-card {
    min-height: 0;
  }
  .model-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 560px) {
  .image-hero {
    flex-direction: column;
    padding: 20px;
  }
  .image-hero h3 {
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
