import { ref } from 'vue'

export const pendingBlogTitle = ref('')
export const pendingBlogId = ref('')
export let blogSharedTransitionActive = false

export function beginBlogSharedTransition(id: string, title: string) {
  pendingBlogId.value = id
  pendingBlogTitle.value = title
  blogSharedTransitionActive = true
  document.documentElement.classList.add('blog-shared-transition')
}

export function endBlogSharedTransition() {
  blogSharedTransitionActive = false
  pendingBlogId.value = ''
  pendingBlogTitle.value = ''
  document.documentElement.classList.remove('blog-shared-transition')
}
