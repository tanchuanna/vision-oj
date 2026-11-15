import { onBeforeUnmount, onMounted, watch } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { OpenAPI } from '@generated'

/**
 * 在线心跳：页面活跃期间定时向后端上报，超过 TTL 未上报即视为离线。
 * 登录后自动启动，登出或页面卸载时停止。
 */
export function useOnlineHeartbeat() {
  const auth = useAuthStore()
  let timer: number | null = null

  const BEAT_INTERVAL_MS = 30_000

  async function beat() {
    if (!auth.isLoggedIn) return
    try {
      const tokenResolver = OpenAPI.TOKEN
      const token = typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
      if (!token) return
      await axios.post(`${OpenAPI.BASE.replace(/\/+$/, '')}/user/session/heartbeat`, {}, {
        headers: { Authorization: `Bearer ${token}` },
      })
    } catch {
      // 心跳失败静默处理，下一轮重试
    }
  }

  function start() {
    if (timer !== null) return
    void beat()
    timer = window.setInterval(beat, BEAT_INTERVAL_MS)
  }

  function stop() {
    if (timer !== null) {
      window.clearInterval(timer)
      timer = null
    }
  }

  onMounted(() => {
    if (auth.isLoggedIn) start()
  })

  onBeforeUnmount(() => {
    stop()
  })

  // 登录/登出切换时启停心跳
  watch(
    () => auth.isLoggedIn,
    (loggedIn) => {
      if (loggedIn) start()
      else stop()
    },
  )
}
