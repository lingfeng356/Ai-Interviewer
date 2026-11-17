/**
 * 格式化工具
 */

function pad(n) {
  return String(n).padStart(2, '0')
}

/**
 * 后端 LocalDateTime 可能序列化成：
 * - ISO 字符串 "2026-09-28T08:56:22"
 * - 数组 [2026, 9, 28, 8, 56, 22]
 * 两种都兼容
 */
export function formatDateTime(value) {
  if (!value) return '-'

  if (Array.isArray(value)) {
    const [y, m, d, h = 0, mi = 0, s = 0] = value
    return `${y}-${pad(m)}-${pad(d)} ${pad(h)}:${pad(mi)}:${pad(s)}`
  }

  const str = String(value).replace('T', ' ')
  return str.length > 19 ? str.slice(0, 19) : str
}

/**
 * 会话 id 太长，列表里只展示前 8 位
 */
export function shortId(id) {
  if (!id) return '-'
  return String(id).slice(0, 8)
}

/**
 * 面试状态 -> Element Plus tag 类型 + 文案
 */
export function statusTag(status) {
  if (status === 'FINISHED') return { type: 'success', text: '已结束' }
  if (status === 'ONGOING') return { type: 'warning', text: '进行中' }
  return { type: 'info', text: status || '未知' }
}

/**
 * 分数 -> 颜色（高分绿、中等橙、低分红）
 */
export function scoreColor(score) {
  if (score === null || score === undefined) return '#909399'
  if (score >= 85) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}

/**
 * 分数等级文案
 */
export function scoreLevel(score) {
  if (score === null || score === undefined) return '待评分'
  if (score >= 90) return '优秀'
  if (score >= 80) return '良好'
  if (score >= 60) return '合格'
  return '待提升'
}

export function nowTime() {
  const d = new Date()
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/**
 * 生成一个 requestId（用于 /interview/start 幂等）
 */
export function uuid() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') {
    return window.crypto.randomUUID()
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
