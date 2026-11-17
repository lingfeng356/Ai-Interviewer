/**
 * 本地存储统一管理：token / 用户信息 / 简历缓存
 * 后端接口：POST /user/login 返回的 data 就是 token 字符串
 */
import { toIdString } from '@/utils/json'

const TOKEN_KEY = 'ai_interviewer_token'
const USER_KEY = 'ai_interviewer_user'
const RESUME_KEY_PREFIX = 'ai_interviewer_resumes_'

export const TOKEN_HEADER = 'satoken'

/* ---------------- token ---------------- */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  localStorage.setItem(TOKEN_KEY, token)
}

/* ---------------- 用户信息（后端没有 userinfo 接口，登录时本地记住） ---------------- */
export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch (e) {
    return null
  }
}

export function setUser(user) {
  localStorage.setItem(USER_KEY, JSON.stringify(user || {}))
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

/* ---------------- 简历缓存（后端没有"简历列表"接口，只能前端记住） ---------------- */
function resumeKey() {
  const user = getUser()
  return RESUME_KEY_PREFIX + (user && user.username ? user.username : 'anonymous')
}

/**
 * 规范化一条简历缓存记录：
 * - resumeId 一律转成字符串（接口返回的/缓存里的类型统一，=== 比较才不会对不上）
 * - 老版本缓存里存的是 Number，且数值超过安全整数范围时说明它已经在
 *   JSON.parse 阶段丢过精度（不可还原），这种脏数据直接丢弃，否则拿它查后端必然失败
 */
function normalizeResumeRecord(record) {
  if (!record || typeof record !== 'object') return null
  const resumeId = toIdString(record.resumeId)
  if (!resumeId) return null
  return { ...record, resumeId }
}

export function getResumeCache() {
  try {
    const list = JSON.parse(localStorage.getItem(resumeKey()) || '[]')
    if (!Array.isArray(list)) return []
    return list.map(normalizeResumeRecord).filter(Boolean)
  } catch (e) {
    return []
  }
}

export function setResumeCache(list) {
  const normalized = (Array.isArray(list) ? list : []).map(normalizeResumeRecord).filter(Boolean)
  localStorage.setItem(resumeKey(), JSON.stringify(normalized))
}

/**
 * 新增一条已上传简历记录（同一 resumeId 去重，最新的排前面）
 */
export function addResumeCache(item) {
  const record = normalizeResumeRecord(item)
  if (!record) return getResumeCache()
  const list = getResumeCache().filter((r) => r.resumeId !== record.resumeId)
  list.unshift(record)
  const trimmed = list.slice(0, 20)
  setResumeCache(trimmed)
  return trimmed
}
