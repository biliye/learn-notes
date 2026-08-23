import { ref, onMounted, onUnmounted } from 'vue'

/**
 * 响应式移动端判断（断点与全局 --bp-mobile 一致：768px）。
 * 用于需要 JS 区分布局的组件（如表格列显隐），纯 CSS 能解决的场景优先用 @media。
 */
export function useIsMobile(bp = 768) {
  const mql = window.matchMedia(`(max-width: ${bp}px)`)
  const isMobile = ref(mql.matches)

  const onChange = (e) => {
    isMobile.value = e.matches
  }

  onMounted(() => {
    mql.addEventListener('change', onChange)
    isMobile.value = mql.matches
  })
  onUnmounted(() => mql.removeEventListener('change', onChange))

  return isMobile
}
