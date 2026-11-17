<script setup>
import { ref, computed, nextTick, onMounted, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { uploadResume } from '@/api/resume'
import { getProviders } from '@/api/interview'
import { useInterviewStore } from '@/stores/interview'
import { getResumeCache, addResumeCache } from '@/utils/storage'
import { formatDateTime, shortId } from '@/utils/format'
import { stopAudio } from '@/utils/audio'
import AppHeader from '@/components/AppHeader.vue'
import ChatBubble from '@/components/ChatBubble.vue'
import TypingBubble from '@/components/TypingBubble.vue'
import VoiceRecorder from '@/components/VoiceRecorder.vue'

const router = useRouter()
const store = useInterviewStore()

/* ============================ 简历相关 ============================ */
const resumeList = ref([])
const currentResumeId = ref(null)
const uploading = ref(false)
const uploadPercent = ref(0)

const currentResume = computed(
  () => resumeList.value.find((r) => r.resumeId === currentResumeId.value) || null
)

/* ---- 简历智能解析的结果（上传时后端用大模型抽出来的） ---- */
const resumeProfile = computed(() => (currentResume.value && currentResume.value.profile) || null)
const profileSkills = computed(() => (resumeProfile.value && resumeProfile.value.skills) || [])
const profileProjects = computed(() => (resumeProfile.value && resumeProfile.value.projects) || [])

function loadResumeCache() {
  resumeList.value = getResumeCache()
  if (resumeList.value.length && !currentResumeId.value) {
    currentResumeId.value = resumeList.value[0].resumeId
  }
}

/* ============================ 可选模型 ============================ */
// 只是给个兜底和友好中文名；真正的选项列表从后端注册中心拉
const PROVIDER_LABELS = {
  deepseek: 'DeepSeek（deepseek-chat）',
  ollama: 'Ollama 本地模型（qwen3:4b）'
}

const providerOptions = ref([
  { value: 'deepseek', label: PROVIDER_LABELS.deepseek },
  { value: 'ollama', label: PROVIDER_LABELS.ollama }
])

/**
 * 从后端注册中心拉可用模型列表。
 * 拉不到就保留上面那份默认的 —— 保证下拉框永远有东西可选。
 */
async function loadProviders() {
  try {
    const list = await getProviders()
    if (Array.isArray(list) && list.length) {
      providerOptions.value = list.map((name) => ({
        value: name,
        label: PROVIDER_LABELS[name] || name
      }))
    }
  } catch (e) {
    /* 静默失败，用默认列表 */
  }
}

function beforeUpload(file) {
  const isPdf = file.name.toLowerCase().endsWith('.pdf')
  if (!isPdf) {
    ElMessage.error('只支持 PDF 格式的简历')
    return false
  }
  if (file.size > 20 * 1024 * 1024) {
    ElMessage.error('文件大小不能超过 20MB')
    return false
  }
  return true
}

// el-upload 自定义上传：走 POST /resume/upload
async function handleUpload(options) {
  if (store.isOngoing) {
    ElMessage.warning('面试进行中，暂不支持更换简历')
    return
  }

  uploading.value = true
  uploadPercent.value = 0

  try {
    const data = await uploadResume(options.file, (percent) => {
      uploadPercent.value = percent
      if (options.onProgress) {
        options.onProgress({ percent })
      }
    })

    const record = {
      // 后端返回的是 19 位雪花 ID，统一按字符串处理，避免 Number 丢精度
      resumeId: data.resumeId === null || data.resumeId === undefined ? '' : String(data.resumeId),
      fileName: data.fileName,
      filePath: data.filePath,
      // 后端用大模型抽出来的结构化画像（解析失败时是 null，不影响上传）
      profile: data.profile || null,
      uploadTime: Date.now()
    }
    const list = addResumeCache(record)
    resumeList.value = list

    // 用缓存里规范化后的字符串 id，保证和 store 中保存的类型一致
    const saved = list.find((r) => r.resumeId === record.resumeId)
    if (!saved) {
      ElMessage.error('简历已上传，但返回的简历 ID 异常，请重新上传')
      if (options.onError) options.onError(new Error('invalid resumeId'))
      return
    }
    currentResumeId.value = saved.resumeId

    ElMessage.success(
      data.profile
        ? '简历上传成功，AI 已完成智能解析'
        : `简历上传成功：${data.fileName}`
    )
    if (options.onSuccess) options.onSuccess(data)
  } catch (e) {
    if (options.onError) options.onError(e)
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

/* ============================ 面试控制 ============================ */
const starting = ref(false)
const finishing = ref(false)

const statusInfo = computed(() => {
  if (store.status === 'ongoing') return { type: 'warning', text: '面试进行中' }
  if (store.status === 'finished') return { type: 'success', text: '面试已结束' }
  return { type: 'info', text: '未开始' }
})

async function handleStart() {
  if (!currentResumeId.value) {
    ElMessage.warning('请先上传一份 PDF 简历')
    return
  }

  starting.value = true
  try {
    store.resumeId = currentResumeId.value
    store.resumeName = currentResume.value ? currentResume.value.fileName : ''
    const data = await store.start()
    ElMessage.success('面试已开始，AI 面试官正在提问')
    scrollToBottom()
    return data
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    starting.value = false
  }
}

async function handleFinish() {
  try {
    await ElMessageBox.confirm(
      '结束后 AI 将根据整场对话生成评估报告（异步生成，大概几秒钟），确定结束本场面试吗？',
      '结束面试',
      {
        type: 'warning',
        confirmButtonText: '结束并生成报告',
        cancelButtonText: '继续面试'
      }
    )
  } catch (e) {
    return
  }

  finishing.value = true
  const sid = store.sessionId
  try {
    await store.finish()
    ElMessage.success('面试已结束，AI 正在生成报告…')
    loadHistory()
    router.push({ path: `/report/${sid}`, query: { fresh: '1' } })
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    finishing.value = false
  }
}

function handleReset() {
  store.reset()
  ElMessage.info('已清空当前会话，可以开始新一场面试')
}

/* 会话级热切换模型：面试进行中切换后，下一回合生效 */
async function onProviderChange(next) {
  // 未开始面试时只是本地选择，start() 会带上这个值，不用调接口
  if (!store.isOngoing) return
  try {
    await store.switchProviderModel(next)
    ElMessage.success('已切换模型，下一回合生效')
  } catch (e) {
    /* 错误提示由拦截器统一弹出；store 内部已把选择回滚 */
  }
}

/* ============================ 聊天 ============================ */
const mode = ref('text') // text | voice
const inputText = ref('')
const listRef = ref(null)
const recorderRef = ref(null)

function scrollToBottom() {
  nextTick(() => {
    const el = listRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

watch(
  () => store.messages.length,
  () => scrollToBottom()
)

async function handleSendText() {
  const text = inputText.value.trim()
  if (!text || store.sending) return

  if (!store.isOngoing) {
    ElMessage.warning('请先开始面试')
    return
  }

  inputText.value = ''
  try {
    await store.sendText(text)
  } catch (e) {
    // 发送失败把内容还回去，方便重发
    inputText.value = text
  }
  scrollToBottom()
}

async function handleVoiceSubmit(blob) {
  if (!store.isOngoing) {
    ElMessage.warning('请先开始面试')
    return
  }
  try {
    await store.sendVoice(blob)
    if (recorderRef.value) recorderRef.value.reset()
  } catch (e) {
    /* 拦截器已提示，保留录音让用户重发 */
  }
  scrollToBottom()
}

function handlePlayMessage(msg) {
  store.playMessage(msg)
}

/* ============================ 历史记录 ============================ */
const historyLoading = ref(false)
const history = ref([])

async function loadHistory() {
  historyLoading.value = true
  try {
    const list = await store.loadHistory()
    history.value = list
  } catch (e) {
    /* ignore */
  } finally {
    historyLoading.value = false
  }
}

function goReport(sessionId) {
  router.push(`/report/${sessionId}`)
}

const historyStatus = (status) => {
  if (status === 'FINISHED') return { type: 'success', text: '已结束' }
  if (status === 'ONGOING') return { type: 'warning', text: '进行中' }
  return { type: 'info', text: status || '未知' }
}

async function copySessionId() {
  if (!store.sessionId) return
  try {
    await navigator.clipboard.writeText(store.sessionId)
    ElMessage.success('会话 ID 已复制')
  } catch (e) {
    ElMessage.warning('浏览器不支持自动复制，请手动选择文本')
  }
}

/* ============================ 生命周期 ============================ */
onMounted(() => {
  loadResumeCache()
  loadHistory()
  loadProviders()
})

onUnmounted(() => {
  stopAudio()
})
</script>

<template>
  <div class="interview-page">
    <AppHeader>
      <template #actions>
        <el-tag v-if="store.sessionId" size="small" effect="plain" type="info">
          会话 {{ shortId(store.sessionId) }}
        </el-tag>
      </template>
    </AppHeader>

    <div class="page-wrap">
      <el-row :gutter="16">
        <!-- ============ 左栏：简历 / 配置 / 历史 ============ -->
        <el-col :xs="24" :sm="24" :md="9" :lg="8">
          <div class="side-col">
            <!-- 简历 -->
            <el-card shadow="never" class="side-card">
              <template #header>
                <div class="flex-between">
                  <span>1. 上传简历</span>
                  <el-tag v-if="currentResume" size="small" type="success" effect="plain">
                    已选择
                  </el-tag>
                </div>
              </template>

              <el-upload
                class="upload-area"
                drag
                accept=".pdf"
                :show-file-list="false"
                :http-request="handleUpload"
                :before-upload="beforeUpload"
                :disabled="uploading || store.isOngoing"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-text">
                  将 PDF 简历拖到此处，或<em>点击上传</em>
                </div>
                <div class="text-muted">仅支持 PDF，后端会自动解析简历内容</div>
              </el-upload>

              <el-progress
                v-if="uploading"
                class="mt-12"
                :percentage="uploadPercent"
                :stroke-width="6"
              />

              <div v-if="resumeList.length" class="mt-12">
                <div class="label">已上传的简历（本次可复用）</div>
                <el-select
                  v-model="currentResumeId"
                  placeholder="请选择简历"
                  class="full-width"
                  :disabled="store.isOngoing"
                >
                  <el-option
                    v-for="item in resumeList"
                    :key="item.resumeId"
                    :label="item.fileName"
                    :value="item.resumeId"
                  >
                    <div class="option-row">
                      <span class="option-name">{{ item.fileName }}</span>
                      <span class="text-muted">{{ formatDateTime(item.uploadTime) }}</span>
                    </div>
                  </el-option>
                </el-select>

                <!-- 简历智能解析结果：上传时后端用大模型抽出来的结构化信息 -->
                <div v-if="resumeProfile" class="profile-box">
                  <div class="profile-title">
                    <el-icon><MagicStick /></el-icon>
                    <span>AI 简历解析</span>
                  </div>

                  <p v-if="resumeProfile.summary" class="profile-summary">
                    {{ resumeProfile.summary }}
                  </p>

                  <div v-if="profileSkills.length" class="skill-tags">
                    <el-tag
                      v-for="skill in profileSkills"
                      :key="skill"
                      size="small"
                      type="primary"
                      effect="plain"
                    >
                      {{ skill }}
                    </el-tag>
                  </div>

                  <ul v-if="profileProjects.length" class="profile-projects">
                    <li v-for="(item, index) in profileProjects" :key="index">
                      <b>{{ item.name }}</b>
                      <span v-if="item.role"> · {{ item.role }}</span>
                    </li>
                  </ul>
                </div>
              </div>
            </el-card>

            <!-- 面试配置 -->
            <el-card shadow="never" class="side-card">
              <template #header>2. 面试配置</template>

              <div class="label">选择大模型</div>
              <el-select
                v-model="store.provider"
                class="full-width"
                :disabled="store.sending"
                @change="onProviderChange"
              >
                <!-- 选项来自后端注册中心 GET /interview/providers，
                     以后新增模型，前端不用改代码就会多出这个选项 -->
                <el-option
                  v-for="item in providerOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>

              <div class="label mt-16">面试官模式</div>
              <el-radio-group v-model="store.agentMode" :disabled="store.isOngoing">
                <el-radio-button value="agent">Agent</el-radio-button>
                <el-radio-button value="classic">经典</el-radio-button>
              </el-radio-group>
              <div class="text-muted mode-hint">
                Agent：模型自己决定查简历 / 查知识库 / 记笔记，每一轮的调用过程都能点开看；
                经典：按固定轮次直接提问（改造前的行为）
              </div>

              <div class="label mt-16">面试模式</div>
              <el-radio-group v-model="mode" :disabled="store.sending">
                <el-radio-button value="text">文字面试</el-radio-button>
                <el-radio-button value="voice">语音面试</el-radio-button>
              </el-radio-group>

              <div class="label mt-16">AI 语音自动朗读</div>
              <el-switch
                v-model="store.autoPlay"
                active-text="开启"
                inactive-text="关闭"
              />

              <div class="action-area">
                <el-button
                  type="primary"
                  class="full-width"
                  size="large"
                  :loading="starting"
                  :disabled="!currentResumeId || store.isOngoing"
                  @click="handleStart"
                >
                  <el-icon><VideoPlay /></el-icon>
                  <span style="margin-left: 4px">开始面试</span>
                </el-button>

                <el-button
                  v-if="store.isOngoing"
                  type="danger"
                  plain
                  class="full-width mt-12"
                  size="large"
                  :loading="finishing"
                  @click="handleFinish"
                >
                  <el-icon><CircleClose /></el-icon>
                  <span style="margin-left: 4px">结束面试并生成报告</span>
                </el-button>

                <el-button
                  v-else-if="store.sessionId"
                  class="full-width mt-12"
                  size="large"
                  :disabled="finishing"
                  @click="handleReset"
                >
                  清空会话，重新开始
                </el-button>
              </div>
            </el-card>

            <!-- 历史记录 -->
            <el-card shadow="never" class="side-card">
              <template #header>
                <div class="flex-between">
                  <span>3. 历史面试记录</span>
                  <el-button text size="small" :loading="historyLoading" @click="loadHistory">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </template>

              <el-empty
                v-if="!history.length && !historyLoading"
                description="还没有面试记录"
                :image-size="70"
              />

              <div v-else class="history-list">
                <div
                  v-for="item in history"
                  :key="item.sessionId"
                  class="history-item"
                  @click="goReport(item.sessionId)"
                >
                  <div class="hi-top">
                    <span class="hi-id">{{ shortId(item.sessionId) }}</span>
                    <el-tag size="small" :type="historyStatus(item.status).type" effect="plain">
                      {{ historyStatus(item.status).text }}
                    </el-tag>
                  </div>
                  <div class="hi-bottom">
                    <span class="text-muted">{{ formatDateTime(item.createdTime) }}</span>
                    <span v-if="item.totalScore !== null && item.totalScore !== undefined" class="hi-score">
                      {{ item.totalScore }} 分
                    </span>
                    <span v-else class="text-muted">—</span>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </el-col>

        <!-- ============ 右栏：聊天区 ============ -->
        <el-col :xs="24" :sm="24" :md="15" :lg="16">
          <el-card shadow="never" class="chat-card">
            <template #header>
              <div class="flex-between chat-header">
                <div class="ch-left">
                  <span class="ch-title">面试对话</span>
                  <el-tag size="small" :type="statusInfo.type" effect="light">
                    {{ statusInfo.text }}
                  </el-tag>
                  <!-- 当前面试环节：后端按候选人已回答的轮数自动推进 -->
                  <el-tag v-if="store.stageName" size="small" type="warning" effect="plain">
                    {{ store.stageName }}
                  </el-tag>
                  <!-- 最近一轮 Agent 执行情况：0 步表示模型判断不需要调工具 -->
                  <el-tag
                    v-if="store.lastAgent && store.lastAgent.mode === 'AGENT'"
                    size="small"
                    type="info"
                    effect="plain"
                  >
                    Agent · {{ store.lastAgent.steps }} 步
                  </el-tag>
                  <el-tag
                    v-else-if="store.lastAgent && store.lastAgent.mode === 'FALLBACK'"
                    size="small"
                    type="warning"
                    effect="plain"
                  >
                    本轮 Agent 已降级
                  </el-tag>
                  <span v-if="store.sessionId" class="session-text" @click="copySessionId">
                    <el-icon><CopyDocument /></el-icon>
                    {{ store.sessionId }}
                  </span>
                </div>
                <div class="ch-right">
                  <el-tooltip content="重新朗读最后一条 AI 消息" placement="top">
                    <el-button text :disabled="!store.messages.length" @click="store.speakLatest()">
                      <el-icon><Headset /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-button text @click="stopAudio">
                    <el-icon><VideoPause /></el-icon>
                    <span style="margin-left: 3px">停止播放</span>
                  </el-button>
                </div>
              </div>
            </template>

            <!-- 消息列表 -->
            <div ref="listRef" class="chat-body">
              <el-empty
                v-if="!store.messages.length"
                :image-size="110"
                description="上传简历并开始面试，AI 面试官会先让你做自我介绍"
              />

              <template v-else>
                <ChatBubble
                  v-for="msg in store.messages"
                  :key="msg.id"
                  :message="msg"
                  @play="handlePlayMessage"
                />
                <TypingBubble v-if="store.sending" />
              </template>
            </div>

            <!-- 输入区 -->
            <div class="chat-footer">
              <template v-if="mode === 'text'">
                <el-input
                  v-model="inputText"
                  type="textarea"
                  :rows="3"
                  resize="none"
                  maxlength="2000"
                  show-word-limit
                  :disabled="!store.isOngoing"
                  :placeholder="
                    store.isOngoing
                      ? '请输入你的回答…（Enter 发送，Shift + Enter 换行）'
                      : '请先在左侧上传简历并开始面试'
                  "
                  @keydown.enter.exact.prevent="handleSendText"
                />
                <div class="footer-actions">
                  <span class="text-muted">
                    提示：回答越具体，AI 追问越深入；1 分钟最多 10 轮对话
                  </span>
                  <el-button
                    type="primary"
                    :loading="store.sending"
                    :disabled="!store.isOngoing || !inputText.trim()"
                    @click="handleSendText"
                  >
                    <el-icon><Promotion /></el-icon>
                    <span style="margin-left: 4px">发送</span>
                  </el-button>
                </div>
              </template>

              <template v-else>
                <VoiceRecorder
                  ref="recorderRef"
                  :submitting="store.sending"
                  :disabled="!store.isOngoing"
                  :max-seconds="120"
                  @submit="handleVoiceSubmit"
                />
                <div class="footer-actions">
                  <span class="text-muted">
                    语音面试流程：录音 → 自动转文字 → AI 回答 → 自动转语音播放（整个过程约 10 - 30 秒）
                  </span>
                </div>
              </template>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.interview-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.side-col {
  height: calc(100vh - 94px);
  overflow-y: auto;
  padding-right: 4px;
}

.side-card {
  margin-bottom: 16px;
}

.label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.mode-hint {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.6;
}

.full-width {
  width: 100%;
}

.upload-area :deep(.el-upload) {
  width: 100%;
}

.upload-area :deep(.el-upload-dragger) {
  padding: 20px 12px;
  border-radius: 8px;
  background: #fafcff;
}

.upload-icon {
  font-size: 34px;
  color: #c0c4cc;
  margin-bottom: 6px;
}

.upload-text {
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}

.option-row {
  display: flex;
  justify-content: space-between;
  gap: 14px;
}

.option-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.action-area {
  margin-top: 20px;
}

.history-list {
  max-height: 280px;
  overflow-y: auto;
}

.history-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.18s;
  background: #fff;
}

.history-item:hover {
  border-color: #c6e2ff;
  background: #fafcff;
}

.hi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.hi-id {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  color: #303133;
}

.hi-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.hi-score {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}

/* ---------------- 聊天区 ---------------- */
.chat-card {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 94px);
}

.chat-card :deep(.el-card__body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 0;
  overflow: hidden;
}

.chat-header {
  gap: 10px;
}

.ch-left {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.ch-title {
  font-weight: 600;
}

.session-text {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #909399;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
}

.session-text:hover {
  color: #409eff;
}

.ch-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  background: #fafbfc;
}

.chat-footer {
  border-top: 1px solid #ebeef5;
  padding: 12px 16px 14px;
  background: #fff;
}

.footer-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

/* ---- 简历智能解析结果 ---- */
.profile-box {
  margin-top: 14px;
  padding: 12px 14px;
  border: 1px solid #d9ecff;
  background: #f7fbff;
  border-radius: 8px;
}

.profile-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  margin-bottom: 8px;
}

.profile-summary {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.profile-projects {
  list-style: none;
  margin: 0;
  padding: 0;
  font-size: 12.5px;
  color: #606266;
}

.profile-projects li {
  padding: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 992px) {
  .side-col {
    height: auto;
    overflow: visible;
    padding-right: 0;
  }
  .chat-card {
    height: auto;
    min-height: 560px;
    margin-top: 16px;
  }
  .chat-body {
    max-height: 60vh;
  }
}
</style>
