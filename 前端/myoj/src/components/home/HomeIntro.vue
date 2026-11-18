<script setup lang="ts">
import { inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import BrandLogo from '@/components/BrandLogo.vue'
import { HOME_INTRO_AUTH_VISIBLE_KEY } from '@/config/homeIntro'
import { localize as l } from '@/i18n/locale'

const STORAGE_KEY = 'myoj.home.intro:v2'
const DURATION_MS = 3800
const props = defineProps<{ replayKey: number }>()
const route = useRoute()
const authVisible = inject(HOME_INTRO_AUTH_VISIBLE_KEY, ref(false))

function shouldPlay() {
  if (typeof window === 'undefined' || typeof document === 'undefined') return false
  if (route.path !== '/' || route.query.openAuth === '1' || authVisible.value) return false
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return false
  try {
    return window.sessionStorage.getItem(STORAGE_KEY) !== 'seen'
  } catch {
    return true
  }
}

const visible = ref(false)
const forced = ref(false)
const sequence = ref(0)
let timer: ReturnType<typeof setTimeout> | undefined

function finish() {
  if (visible.value && document.visibilityState === 'visible') {
    try {
      window.sessionStorage.setItem(STORAGE_KEY, 'seen')
    } catch {
      /* Private browsing may block storage. */
    }
  }
  visible.value = false
  if (timer) clearTimeout(timer)
  timer = undefined
}

function play(force = false) {
  if (route.path !== '/' || authVisible.value || document.visibilityState !== 'visible') return
  if (!force && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  if (timer) clearTimeout(timer)
  forced.value = force
  sequence.value += 1
  visible.value = true
  timer = setTimeout(finish, DURATION_MS)
}

function onVisibilityChange() {
  if (document.visibilityState === 'hidden' && visible.value) {
    visible.value = false
    if (timer) clearTimeout(timer)
    timer = undefined
  }
  if (shouldPlay() && document.visibilityState === 'visible') play()
}

watch(
  () => props.replayKey,
  () => play(true),
)
watch(authVisible, (open) => {
  if (open) finish()
})
watch(
  () => route.path,
  (path) => {
    if (path !== '/') finish()
  },
)

onMounted(() => {
  if (shouldPlay() && document.visibilityState === 'visible') play()
  document.addEventListener('visibilitychange', onVisibilityChange)
})

onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
  document.removeEventListener('visibilitychange', onVisibilityChange)
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="visible"
      :key="sequence"
      class="home-intro"
      :class="{ 'home-intro--forced': forced }"
      :aria-label="l('码跃 OJ 首页入场', 'OJ home intro')"
    >
      <div class="home-intro__stage" aria-hidden="true">
        <div class="home-intro__mark"><BrandLogo :size="76" /></div>
        <div class="home-intro__wordmark">码跃<span>OJ</span></div>
        <div class="home-intro__trace"><i /></div>
        <p>{{ l('让每一次练习，都看得见进步', 'Make every practice session count') }}</p>
      </div>
      <span class="home-intro__corner home-intro__corner--top" aria-hidden="true">&lt;/&gt;</span>
      <span class="home-intro__corner home-intro__corner--bottom" aria-hidden="true"
        >ONLINE JUDGE</span
      >
    </div>
  </Teleport>
</template>

<style scoped>
.home-intro {
  position: fixed;
  z-index: 3000;
  inset: 0;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #f5f3ec;
  color: #122c2b;
  isolation: isolate;
  animation: intro-reveal 0.68s 3.08s cubic-bezier(0.75, 0, 0.24, 1) forwards;
}
.home-intro::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: -1;
  background: linear-gradient(
    125deg,
    transparent 46%,
    rgba(14, 143, 130, 0.055) 50%,
    transparent 54%
  );
  background-size: 180% 180%;
  animation: intro-wash 1.3s ease-out both;
}
.home-intro__stage {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
  text-align: center;
}
.home-intro__mark {
  filter: drop-shadow(0 14px 25px rgba(14, 143, 130, 0.19));
  animation: intro-mark 0.55s 0.06s cubic-bezier(0.2, 0.85, 0.2, 1) both;
}
.home-intro__wordmark {
  color: #122c2b;
  font-size: clamp(48px, 8vw, 86px);
  font-weight: 850;
  letter-spacing: -0.085em;
  line-height: 1;
  animation: intro-type 0.52s 0.25s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.home-intro__wordmark span {
  margin-left: 0.14em;
  color: #0e8f82;
  font-size: 0.49em;
  letter-spacing: -0.04em;
  vertical-align: middle;
}
.home-intro__trace {
  width: min(320px, 62vw);
  height: 2px;
  overflow: hidden;
  background: rgba(14, 143, 130, 0.16);
}
.home-intro__trace i {
  display: block;
  width: 100%;
  height: 100%;
  background: #0e8f82;
  transform-origin: left;
  animation: intro-trace 0.55s 0.55s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.home-intro__stage p {
  margin: 0;
  color: #57736d;
  font-size: clamp(13px, 2vw, 16px);
  letter-spacing: 0.12em;
  animation: intro-type 0.45s 0.7s ease-out both;
}
.home-intro__corner {
  position: absolute;
  color: rgba(14, 143, 130, 0.35);
  font:
    700 12px/1 ui-monospace,
    monospace;
  letter-spacing: 0.2em;
}
.home-intro__corner--top {
  top: 32px;
  left: 30px;
}
.home-intro__corner--bottom {
  bottom: 28px;
  right: 30px;
}
:global(html[data-theme='dark']) .home-intro {
  background: #111e23;
  color: #f4f7f4;
}
:global(html[data-theme='dark']) .home-intro__wordmark {
  color: #f4f7f4;
}
:global(html[data-theme='dark']) .home-intro__stage p {
  color: #a2c8bf;
}
@keyframes intro-mark {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.72) rotate(-12deg);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1) rotate(0);
  }
}
@keyframes intro-type {
  from {
    opacity: 0;
    transform: translateY(15px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes intro-trace {
  from {
    transform: scaleX(0);
  }
  to {
    transform: scaleX(1);
  }
}
@keyframes intro-wash {
  from {
    background-position: 100% 50%;
  }
  to {
    background-position: 0 50%;
  }
}
@keyframes intro-reveal {
  to {
    transform: translateY(-102%);
  }
}
@media (prefers-reduced-motion: reduce) {
  .home-intro:not(.home-intro--forced),
  .home-intro:not(.home-intro--forced) *,
  .home-intro:not(.home-intro--forced)::before {
    animation: none !important;
  }
}
</style>
