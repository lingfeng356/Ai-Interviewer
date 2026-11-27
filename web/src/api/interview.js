import request from './request'
import { getToken } from '@/utils/storage'

/**
 * 开始面试  POST /interview/start?resumeId=&provider=&requestId=&mode=
 * provider: deepseek | ollama（不传后端默认 deepseek）
 * requestId: 前端生成的幂等 id，防止重复点开始创建两场面试
 * mode: agent（默认，模型自主调用工具）| classic（改造前的固定轮次模式）
 * data: { sessionId, message }  —— message 是 AI 开场白
 */
export function startInterview({ resumeId, provider, requestId, mode }) {
  return request.post('/interview/start', null, {
    params: { resumeId, provider, requestId, mode },
    timeout: 120000
  })
}

/**
 * 回答 + AI 追问  POST /interview/reply?provider=
 * body: { sessionId, answer }
 * provider: 可选，仅本回合临时指定模型（不传则用会话当前模型）
 * data: { sessionId, message, aiAudio } —— aiAudio 是 base64(mp3)
 */
export function replyInterview(data, provider) {
  return request.post('/interview/reply', data, {
    params: { provider },
    timeout: 180000
  })
}

/**
 * 会话级热切换模型  POST /interview/provider?sessionId=&provider=
 * provider: deepseek | ollama（切换后后续所有回合都用新模型）
 */
export function switchProvider(sessionId, provider) {
  return request.post('/interview/provider', null, {
    params: { sessionId, provider }
  })
}

/**
 * 当前可用的模型列表  GET /interview/providers
 * 数据来自后端注册中心里所有已注册的 LlmProvider
 * data: ["deepseek", "ollama"]
 */
export function getProviders() {
  return request.get('/interview/providers')
}

/**
 * 结束面试  POST /interview/finish?sessionId=
 * data: { sessionId, summary: '报告生成中，请稍后查询' } —— 评分是异步的
 */
export function finishInterview(sessionId) {
  return request.post('/interview/finish', null, {
    params: { sessionId },
    timeout: 60000
  })
}

/**
 * 查询本场报告  GET /interview/report?sessionId=
 * data: { sessionId, totalScore, summary, strengths[], weaknesses[], suggestion, createdTime }
 * totalScore 为 null 表示报告还在生成中
 */
export function getInterviewReport(sessionId) {
  return request.get('/interview/report', { params: { sessionId } })
}

/**
 * 面试历史列表  GET /interview/list
 * data: [{ sessionId, status, totalScore, createdTime }]
 */
export function getInterviewList() {
  return request.get('/interview/list')
}

/**
 * SSE 地址。
 * EventSource 不能自定义请求头，所以 token 只能放在 query 里，
 * 后端 /interview/sse 会显式校验它 + 这场面试的归属。
 * 报告生成过程中后端按阶段推 PROGRESS 事件，全部完成时推 DONE
 */
export function buildSseUrl(sessionId) {
  const token = encodeURIComponent(getToken() || '')
  return `/api/interview/sse?sessionId=${encodeURIComponent(sessionId)}&token=${token}`
}

/**
 * 知识库相关（可选功能，后端已实现 /rag/**）
 */
export function ragAsk(query) {
  return request.get('/rag/ask', { params: { query } })
}
