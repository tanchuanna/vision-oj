import type { InjectionKey, Ref } from 'vue'

/** The layout owns the login dialog, so the intro can yield to it immediately. */
export const HOME_INTRO_AUTH_VISIBLE_KEY: InjectionKey<Ref<boolean>> = Symbol('home-intro-auth-visible')
