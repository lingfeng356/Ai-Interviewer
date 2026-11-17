/**
 * 全局 axios 实例
 * - 请求拦截器：自动带上 satoken 请求头
 * - 响应拦截器：统一解包 Result、code !== 200 弹错误、登录失效跳登录页
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { TOKEN_HEADER, getToken, clearAuth } from '@/utils/storage'
import { parseJsonPreserveBigInt } from '@/utils/json'

/**
 * 用大整数安全的解析器替换 axios 默认的 JSON.parse。
 *
 * 后端主键是 MyBatis-Plus 雪花算法生成的 19 位 Long，JSON 里是裸数字，
 * 而 JS 只能精确表示 2^53 - 1，默认解析会把 resumeId 直接四舍五入掉
 * （1856123456789012345 → 1856123456789012300），后面无论怎么传都是错的。
 * 这里在 parse 之前把这类整数转成字符串，id 就能原样保留。
 *
 * 更彻底的做法是后端把 Long 统一序列化成 String（Jackson 注册
 * ToStringSerializer），前端这一层只是兜底，两处都做最稳妥。
 */
const JSON_MIME = /^application\/(?:[\w.+-]*\+)?json\b/i

function transformResponse(data, headers) {
  // 只有字符串需要解析；blob / arraybuffer 原样返回
  if (typeof data !== 'string' || data === '') return data

  const contentType =
    (headers && typeof headers.get === 'function' && headers.get('content-type')) ||
    (headers && headers['content-type']) ||
    ''
  const head = data.slice(0, 16).trimStart()
  const looksLikeJson = head.startsWith('{') || head.startsWith('[')

  // 不是 JSON（例如网关/代理返回的错误页）就原样交给拦截器
  if (!JSON_MIME.test(String(contentType)) && !looksLikeJson) return data

  try {
    return parseJsonPreserveBigInt(data)
  } catch (e) {
    return data
  }
}

const service = axios.create({
  // 走 vite 代理；生产环境可改成后端完整地址
  baseURL: '/api',
  timeout: 120000,
  transformResponse: [transformResponse]
})

/* ============================ 请求拦截器 ============================ */
service.interceptors.request.use(
  (config) => {
    const token = getToken()
    if (token) {
      // Sa-Token 默认请求头名称就是 satoken
      config.headers[TOKEN_HEADER] = token
    }
    return config
  },
  (error) => Promise.reject(error)
)

/* ============================ 登录失效判断 ============================ */
// Sa-Token 抛出的 NotLoginException 会被后端 GlobalExceptionHandler 兜住，
// 返回 code=500 而不是 401，所以这里用关键词兜底识别。
const AUTH_KEYWORDS = [
  'token',
  '未登录',
  '请登录',
  '登录状态',
  '登录已过期',
  '顶下线',
  '踢下线',
  '账号被'
]

function isAuthFailed(status, message) {
  // 后端已经单独处理了 Sa-Token 的 NotLoginException，会返回真正的 HTTP 401，
  // 所以优先看状态码 —— 不用再像以前那样靠关键词猜了。
  if (status === 401) return true

  // 注意：403 不在这里判。403 现在表示"无权访问别人的数据"（业务错误），
  // 如果当成登录失效，用户点一下别人的面试就被踢下线了。

  // 关键词只是兜底：兼容还没改造的接口，或者网关/代理返回的错误页
  if (!message) return false
  const msg = String(message).toLowerCase()
  return AUTH_KEYWORDS.some((kw) => msg.includes(kw.toLowerCase()))
}

let redirecting = false
function redirectToLogin() {
  clearAuth()
  if (redirecting) return
  const current = router.currentRoute.value
  if (current.path === '/login') return
  redirecting = true
  ElMessage.error('登录状态已失效，请重新登录')
  router
    .replace({ path: '/login', query: { redirect: current.fullPath } })
    .finally(() => {
      setTimeout(() => {
        redirecting = false
      }, 800)
    })
}

/* ============================ 响应拦截器 ============================ */
service.interceptors.response.use(
  (response) => {
    // 二进制流直接返回
    if (response.config.responseType === 'blob') return response.data

    const res = response.data

    // 后端返回的不是标准 Result（比如网关错误页），直接把原始数据抛出去
    if (!res || typeof res.code === 'undefined') return res

    if (res.code !== 200) {
      const message = res.message || '请求失败'
      if (isAuthFailed(response.status, message)) {
        redirectToLogin()
      } else {
        ElMessage.error(message)
      }
      return Promise.reject(new Error(message))
    }

    // 解包：业务代码里直接拿 data
    return res.data
  },
  (error) => {
    let message = '网络异常，请稍后重试'
    const status = error.response && error.response.status

    if (error.code === 'ECONNABORTED') {
      message = '请求超时，AI 回答可能较慢，请稍后重试'
    } else if (status) {
      const data = error.response.data
      message = (data && (data.message || data.msg)) || `请求失败（${status}）`
    }

    if (isAuthFailed(status, message)) {
      redirectToLogin()
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  }
)

export default service
