<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  providerCode: string
  providerName: string
  configured: boolean
  isLight: boolean
}>()

// These are the providers' own key-management entries. Baidu currently directs to
// the Qianfan console; its key page is reached through 系统管理 → API KEY.
const keyPages: Record<string, { url: string; destination: string; instruction: string }> = {
  DASHSCOPE: {
    url: 'https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key',
    destination: '阿里云百炼 API Key 页面',
    instruction: '选择与模型服务一致的地域，再创建 API Key。',
  },
  DEEPSEEK: {
    url: 'https://platform.deepseek.com/api_keys',
    destination: 'DeepSeek API Keys 页面',
    instruction: '登录后创建新的 API Key，并在页面关闭前复制。',
  },
  VOLCENGINE: {
    url: 'https://ark.volcengine.com/region:cn-beijing/apiKey',
    destination: '火山方舟 API Key 管理',
    instruction: '选择使用的项目空间，再创建 API Key。',
  },
  BAIDU_QIANFAN: {
    url: 'https://console.bce.baidu.com/qianfan/overview',
    destination: '百度千帆控制台',
    instruction: '登录后进入「系统管理 → API KEY」创建密钥。',
  },
  SILICONFLOW: {
    url: 'https://cloud.siliconflow.cn/account/ak',
    destination: '硅基流动 API 密钥页面',
    instruction: '创建一个 Key 后，可在模型广场查看当前可用的文字和图片模型。',
  },
  ZHIPU: {
    url: 'https://bigmodel.cn/usercenter/proj-mgmt/apikeys',
    destination: '智谱开放平台 API Key 页面',
    instruction: '在项目中创建 API Key，并确认模型调用权限。',
  },
  STEPFUN: {
    url: 'https://platform.stepfun.com/',
    destination: '阶跃星辰开放平台',
    instruction: '登录后进入 API Key 页面创建密钥。',
  },
  MOONSHOT: {
    url: 'https://platform.moonshot.cn/',
    destination: 'Kimi 开放平台',
    instruction: '使用中国区开放平台的 Key；Kimi Code 会员密钥不适用于这里。',
  },
  TENCENT_TOKENHUB: {
    url: 'https://console.cloud.tencent.com/tokenhub/apikey',
    destination: '腾讯云 TokenHub API Key 页面',
    instruction: '创建 API Key，并确认该密钥有目标模型的调用权限。',
  },
}

const keyPage = computed(() => keyPages[props.providerCode])
const modelCatalogPages: Record<string, string> = {
  SILICONFLOW: 'https://cloud.siliconflow.cn/models',
  TENCENT_TOKENHUB: 'https://cloud.tencent.com/document/product/1823/130079',
}
const modelCatalogPage = computed(() => modelCatalogPages[props.providerCode])
</script>

<template>
  <div v-if="keyPage" class="key-guide" :class="{ 'is-light': isLight }">
    <div class="key-guide-main">
      <div class="key-guide-copy">
        <span class="key-guide-eyebrow">{{
          configured ? '需要更换密钥？' : '还没有 API Key？'
        }}</span>
        <strong>在 {{ providerName }} 申请，回来连接你的模型</strong>
        <p>{{ keyPage.instruction }} 密钥创建后，回到这里粘贴并保存。</p>
      </div>
      <a
        class="key-guide-link"
        :href="keyPage.url"
        target="_blank"
        rel="noopener noreferrer"
        :aria-label="`在新标签页打开${keyPage.destination}`"
      >
        {{ configured ? '管理 API Key' : '去申请 API Key' }} <span aria-hidden="true">↗</span>
      </a>
    </div>
    <a
      v-if="modelCatalogPage"
      class="catalog-link"
      :href="modelCatalogPage"
      target="_blank"
      rel="noopener noreferrer"
      >查看平台模型目录 <span aria-hidden="true">↗</span></a
    >
    <div class="key-guide-steps" aria-label="配置步骤">
      <span><b>01</b> 打开厂商平台</span><i aria-hidden="true">→</i>
      <span><b>02</b> 创建并复制密钥</span><i aria-hidden="true">→</i>
      <span><b>03</b> 返回这里保存</span>
    </div>
    <small class="key-guide-destination"
      >将在新标签页打开{{ keyPage.destination }}；模型调用费用以厂商页面为准。</small
    >
  </div>
</template>

<style scoped>
.key-guide {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid rgba(148, 163, 184, 0.25);
  border-radius: 15px;
  background: rgba(148, 163, 184, 0.08);
  color: #f8fafc;
}
.key-guide.is-light {
  color: #172033;
  background: #f8fafc;
}
.key-guide-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.key-guide-copy {
  min-width: 0;
}
.key-guide-eyebrow {
  display: block;
  margin-bottom: 4px;
  color: #38d7c1;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.04em;
}
.is-light .key-guide-eyebrow {
  color: #0d9488;
}
.key-guide-copy strong {
  display: block;
  font-size: 13px;
  line-height: 1.4;
}
.key-guide-copy p {
  margin: 5px 0 0;
  color: #adbbcc;
  font-size: 12px;
  line-height: 1.55;
}
.is-light .key-guide-copy p {
  color: #64748b;
}
.key-guide-link {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 0 13px;
  border-radius: 10px;
  background: #0d9488;
  color: #fff;
  font-size: 12px;
  font-weight: 750;
  text-decoration: none;
  transition:
    background 0.18s,
    transform 0.18s;
}
.key-guide-link:hover {
  background: #0f766e;
  transform: translateY(-1px);
}
.key-guide-link:focus-visible {
  outline: 2px solid #2dd4bf;
  outline-offset: 3px;
}
.catalog-link {
  display: inline-flex;
  gap: 5px;
  margin-top: 10px;
  color: #38d7c1;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}
.is-light .catalog-link {
  color: #0d9488;
}
.catalog-link:hover {
  text-decoration: underline;
}
.catalog-link:focus-visible {
  outline: 2px solid #2dd4bf;
  outline-offset: 3px;
}
.key-guide-steps {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 15px;
  padding-top: 12px;
  border-top: 1px solid rgba(148, 163, 184, 0.22);
  color: #bdc9d7;
  font-size: 11px;
}
.is-light .key-guide-steps {
  color: #52627a;
}
.key-guide-steps span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.key-guide-steps b {
  color: #38d7c1;
  font-size: 10px;
}
.is-light .key-guide-steps b {
  color: #0d9488;
}
.key-guide-steps i {
  font-style: normal;
  opacity: 0.6;
}
.key-guide-destination {
  display: block;
  margin-top: 10px;
  color: #95a6ba;
  font-size: 11px;
  line-height: 1.45;
}
.is-light .key-guide-destination {
  color: #64748b;
}
@media (max-width: 560px) {
  .key-guide-main {
    align-items: stretch;
    flex-direction: column;
  }
  .key-guide-link {
    width: max-content;
  }
  .key-guide-steps {
    gap: 7px;
  }
  .key-guide-steps i {
    display: none;
  }
}
</style>
