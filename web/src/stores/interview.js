import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  startInterview,
  replyInterview,
  finishInterview,
  getInterviewList,
  switchProvider
} from '@/api/interview'
import { voiceInterview } from '@/api/voice'
import { playBase64Audio, stopAudio } from '@/utils/audio'
import { nowTime } from '@/utils/format'

let messageSeq = 0
function nextId() {
  messageSeq += 1
  return `msg_${Date.now()}_${messageSeq}`
}

/**
 * 面试 store：管理一场面试的会话状态与消息列表
 * 消息结构：{ id, role: 'ai' | 'user', content, audio, time, from: 'text' | 'voice' }
 */
export const useInterviewStore = defineStore('interview', () => {
  const sessionId = ref('')
  const status = ref('idle') // idle | ongoing | finished
  const provider = ref('deepseek')
  const resumeId = ref(null)
  const resumeName = ref('')
  const messages = ref([])
  const sending = ref(false)
  const autoPlay = ref(true)

  /**
   * 面试官模式：
   * - agent  ：后端让模型自主调用工具（查简历 / 查知识库 / 记笔记），响应里带 agent 轨迹
   * - classic：改造前的固定轮次模式，行为完全不变
   * 只影响“开新面试”时传给后端的参数，进行中不能切换（后端按会话记录的模式执行）。
   */
  const agentMode = ref('agent')

  /** 最近一轮的 Agent 执行摘要（含工具调用轨迹），用于聊天区顶部的提示 */
  const lastAgent = ref(null)
  const historyList = ref([])
  const historyLoading = ref(false)

  /** 当前面试环节：stage 是枚举名，stageName 是中文名（后端按已回答轮数算出来的） */
  const stage = ref('')
  const stageName = ref('')

  const isOngoing = computed(() => status.value === 'ongoing')
  const canStart = computed(() => !!resumeId.value && status.value !== 'ongoing')

  /** 后端每次回复都会带上当前环节，这里同步一下给页面展示 */
  function applyStage(data) {
    if (data && data.stage) {
      stage.value = data.stage
      stageName.value = data.stageName || ''
    }
  }

  function pushMessage(msg) {
    messages.value.push({
      id: nextId(),
      time: nowTime(),
      from: 'text',
      audio: '',
      agent: null,
      ...msg
    })
  }

  /* ---------------- 开始面试 ---------------- */
  /**
   * @param requestId 幂等 id。由调用方（页面）生成并在重试时复用同一个值，
   *                  放在这里每次 new 一个的话，后端的幂等逻辑就永远不会命中。
   */
  async function start(requestId) {
    if (!resumeId.value) throw new Error('请先选择简历')
    const data = await startInterview({
      resumeId: resumeId.value,
      provider: provider.value,
      requestId,
      // agent | classic：决定后端用哪种方式提问
      mode: agentMode.value
    })

    sessionId.value = data.sessionId
    status.value = 'ongoing'
    messages.value = []
    lastAgent.value = null
    pushMessage({ role: 'ai', content: data.message })
    applyStage(data) // 开场白固定是"自我介绍"环节

    if (autoPlay.value) {
      speakLatest()
    }
    return data
  }

  /* ---------------- 文字回答 ---------------- */
  async function sendText(answer) {
    if (!sessionId.value) throw new Error('请先开始面试')
    const text = String(answer || '').trim()
    if (!text) return

    pushMessage({ role: 'user', content: text, from: 'text' })
    sending.value = true
    try {
      const data = await replyInterview({
        sessionId: sessionId.value,
        answer: text
      })
      pushMessage({
        role: 'ai',
        content: data.message,
        audio: data.aiAudio || '',
        from: 'text',
        // Agent 模式下后端会带回本轮工具调用轨迹；经典模式为 null
        agent: data.agent || null
      })
      lastAgent.value = data.agent || null
      applyStage(data) // 后端可能已经把面试推进到下一个环节
      if (autoPlay.value && data.aiAudio) {
        playBase64Audio(data.aiAudio)
      }
      return data
    } finally {
      sending.value = false
    }
  }

  /* ---------------- 语音回答（录音上传） ---------------- */
  async function sendVoice(audioBlob) {
    if (!sessionId.value) throw new Error('请先开始面试')
    sending.value = true
    try {
      const data = await voiceInterview(sessionId.value, audioBlob)
      pushMessage({
        role: 'user',
        content: data.userText || '（未识别到内容）',
        from: 'voice'
      })
      pushMessage({
        role: 'ai',
        content: data.aiText,
        audio: data.aiAudio || '',
        from: 'voice',
        agent: data.agent || null
      })
      lastAgent.value = data.agent || null
      applyStage(data) // 后端可能已经把面试推进到下一个环节
      if (autoPlay.value && data.aiAudio) {
        playBase64Audio(data.aiAudio)
      }
      return data
    } finally {
      sending.value = false
    }
  }

  /* ---------------- 结束面试 ---------------- */
  async function finish() {
    if (!sessionId.value) throw new Error('当前没有进行中的面试')
    const data = await finishInterview(sessionId.value)
    status.value = 'finished'
    stopAudio()
    return data
  }

  /* ---------------- 历史列表 ---------------- */
  async function loadHistory() {
    historyLoading.value = true
    try {
      const list = await getInterviewList()
      historyList.value = Array.isArray(list) ? list : []
      return historyList.value
    } finally {
      historyLoading.value = false
    }
  }

  /* ---------------- 会话级热切换模型 ---------------- */
  async function switchProviderModel(next) {
    // 面试未开始：只是本地选择，start() 时会带上，不用调接口
    if (!sessionId.value) {
      provider.value = next
      return
    }
    const prev = provider.value
    provider.value = next // 乐观更新，UI 立即响应
    try {
      await switchProvider(sessionId.value, next)
    } catch (e) {
      provider.value = prev // 失败回滚，保持 UI 与后端一致
      throw e
    }
  }

  /* ---------------- 重新播放最后一条 AI 语音 ---------------- */
  function speakLatest() {
    for (let i = messages.value.length - 1; i >= 0; i--) {
      const msg = messages.value[i]
      if (msg.role === 'ai' && msg.audio) {
        playBase64Audio(msg.audio)
        return true
      }
    }
    return false
  }

  function playMessage(msg) {
    if (msg && msg.audio) playBase64Audio(msg.audio)
  }

  function reset() {
    stopAudio()
    sessionId.value = ''
    status.value = 'idle'
    messages.value = []
    sending.value = false
    stage.value = ''
    stageName.value = ''
    lastAgent.value = null
  }

  return {
    sessionId,
    status,
    provider,
    resumeId,
    resumeName,
    messages,
    sending,
    autoPlay,
    historyList,
    historyLoading,
    stage,
    stageName,
    agentMode,
    lastAgent,
    isOngoing,
    canStart,
    start,
    sendText,
    sendVoice,
    finish,
    loadHistory,
    switchProviderModel,
    speakLatest,
    playMessage,
    pushMessage,
    reset
  }
})
