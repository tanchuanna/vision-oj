import { ref } from 'vue'

export const pendingProblemTitle = ref('')
export const pendingProblemId = ref('')
export let problemSharedTransitionActive = false

export function beginProblemSharedTransition(id: string, title: string) {
  pendingProblemId.value = id
  pendingProblemTitle.value = title
  problemSharedTransitionActive = true
  document.documentElement.classList.add('problem-shared-transition')
}

export function endProblemSharedTransition() {
  problemSharedTransitionActive = false
  pendingProblemId.value = ''
  pendingProblemTitle.value = ''
  document.documentElement.classList.remove('problem-shared-transition')
}
