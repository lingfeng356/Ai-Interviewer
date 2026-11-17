<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getInterviewReport,
  getInterviewList,
  finishInterview,
  buildSseUrl
} from '@/api/interview'
import { formatDateTime, scoreColor, scoreLevel } from '@/utils/format'
import AppHeader from '@/components/AppHeader.vue'

const route = useRoute()
const router = useRouter()

const sessionId = String(route.params.sessionId || '')
const isFresh = route.query.fresh === '1'

/* ============================ 状态 ============================ */
const loading = ref(true)
const generating = ref(false)
const timedOut = ref(false)
const failed = ref(false)
const report = ref(null)
const sessionInfo = ref(null)
const elapsed = ref(0)
const attempts = ref(0)
const finishing = ref(false)

// SSE 推来的真实进度（后端评分消费者分阶段上报）
const stageText = ref('')
const percent = ref(0)

const POLL_INTERVAL = 3000 // 3 秒轮询一次
const MAX_ATTEMPTS = 40 // 最多轮询 40 次（约 2 分钟）

let pollTimer = null
let tickTimer = null
let eventSource = null

/* ============================ 计算属性 ============================ */
const score = computed(() => {
  const s = report.value && report.value.totalScore
  return s === null || s === undefined ? null : s
})

const isReady = computed(() => score.value !== null)

const strengths = computed(() => {
  const list = (report.value && report.value.strengths) || []
  return list.filter((s) => s && String(s).trim())
})

const weaknesses = computed(() => {
  const list = (report.value && report.value.weaknesses) || []
  return list.filter((s) => s && String(s).trim())
})

const sessionOngoing = computed(
  () => sessionInfo.value && sessionInfo.value.status === 'ONGOING'
)

/* ============================ 数据请求 ============================ */
function isReportReady(data) {
  return !!data && data.totalScore !== null && data.totalScore !== undefined
}

async function fetchReport() {
  try {
    const data = await getInterviewReport(sessionId)
    report.value = data
    if (isReportReady(data)) {
      return true
    }
    // 拿一下这场面试的状态，用于区分「还没结束」和「报告生成中」
    if (!sessionInfo.value) {
      await fetchSessionInfo()
    }
    return false
  } catch (e) {
    stopTimers()
    generating.value = false
    return false
  }
}

async function fetchSessionInfo() {
  try {
    const list = await getInterviewList()
    sessionInfo.value = (list || []).find((i) => i.sessionId === sessionId) || null
  } catch (e) {
    sessionInfo.value = null
  }
}

/* ============================ 轮询 ============================ */
function startPolling() {
  stopTimers()
  generating.value = true
  timedOut.value = false
  failed.value = false
  elapsed.value = 0
  attempts.value = 0
  stageText.value = ''
  percent.value = 0

  tickTimer = setInterval(() => {
    elapsed.value += 1
  }, 1000)

  pollTimer = setInterval(async () => {
    attempts.value += 1
    const ready = await fetchReport()
    if (ready) {
      stopTimers()
      generating.value = false
      ElMessage.success('面试报告已生成')
      return
    }
    if (attempts.value >= MAX_ATTEMPTS) {
      stopTimers()
      generating.value = false
      timedOut.value = true
    }
  }, POLL_INTERVAL)

  openSse()
}

function stopTimers() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
  closeSse()
}

/* ============================ SSE（报告好了立刻知道，不用等下个轮询） ============================ */
function openSse() {
  closeSse()
  if (typeof EventSource === 'undefined') return
  try {
    eventSource = new EventSource(buildSseUrl(sessionId))

    // 后端分阶段推送的命名事件（注意：命名事件不能用 onmessage 接）
    eventSource.addEventListener('PROGRESS', async (event) => {
      let payload = null
      try {
        payload = JSON.parse(event.data)
      } catch (e) {
        return
      }

      if (payload.message) stageText.value = payload.message
      if (typeof payload.progress === 'number') {
        percent.value = Math.min(Math.max(payload.progress, 0), 100)
      }

      if (payload.stage === 'DONE') {
        closeSse()
        const ready = await fetchReport()
        if (ready) {
          stopTimers()
          generating.value = false
          ElMessage.success('面试报告已生成')
        }
      } else if (payload.stage === 'FAILED') {
        // 生成失败也要立刻停掉轮询，不然页面会一直转到超时
        closeSse()
        stopTimers()
        generating.value = false
        failed.value = true
        ElMessage.error(payload.message || '报告生成失败')
      }
    })

    // 兼容旧的纯文本 DONE 推送
    eventSource.onmessage = async (event) => {
      if (String(event.data || '').includes('DONE')) {
        closeSse()
        const ready = await fetchReport()
        if (ready) {
          stopTimers()
          generating.value = false
          ElMessage.success('面试报告已生成')
        }
      }
    }

    // SSE 失败不影响主流程，反正有轮询兜底
    eventSource.onerror = () => closeSse()
  } catch (e) {
    closeSse()
  }
}

function closeSse() {
  if (eventSource) {
    try {
      eventSource.close()
    } catch (e) {
      /* ignore */
    }
    eventSource = null
  }
}

/* ============================ 操作 ============================ */
async function manualRefresh() {
  loading.value = true
  try {
    const ready = await fetchReport()
    if (ready) {
      ElMessage.success('报告已生成')
    } else if (sessionOngoing.value) {
      ElMessage.info('本场面试还在进行中，先去完成面试吧')
    } else {
      ElMessage.info('报告还在生成中，请稍候…')
      startPolling()
    }
  } finally {
    loading.value = false
  }
}

async function handleFinishNow() {
  finishing.value = true
  try {
    await finishInterview(sessionId)
    ElMessage.success('面试已结束，AI 正在生成报告…')
    sessionInfo.value = { sessionId, status: 'FINISHED' }
    startPolling()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    finishing.value = false
  }
}

function goInterview() {
  router.push('/interview')
}

function goHistory() {
  router.push('/interview')
}

/* ============================ 生命周期 ============================ */
onMounted(async () => {
  loading.value = true
  try {
    const ready = await fetchReport()
    if (!ready) {
      if (sessionOngoing.value) {
        generating.value = false
      } else {
        startPolling()
      }
    }
  } finally {
    loading.value = false
  }
})

onUnmounted(() => {
  stopTimers()
})
</script>

<template>
  <div class="report-page">
    <AppHeader>
      <template #actions>
        <el-button text @click="goInterview">
          <el-icon><HomeFilled /></el-icon>
          <span style="margin-left: 3px">面试主页</span>
        </el-button>
      </template>
    </AppHeader>

    <div class="page-wrap report-wrap">
      <!-- 顶部信息 -->
      <el-card shadow="never" class="head-card">
        <div class="head-row">
          <div>
            <h2 class="head-title">面试评估报告</h2>
            <div class="head-meta">
              <span class="mono">{{ sessionId }}</span>
              <el-tag
                v-if="sessionInfo"
                size="small"
                effect="plain"
                :type="sessionInfo.status === 'FINISHED' ? 'success' : 'warning'"
              >
                {{ sessionInfo.status === 'FINISHED' ? '已结束' : '进行中' }}
              </el-tag>
              <span v-if="report && report.createdTime" class="text-muted">
                生成时间：{{ formatDateTime(report.createdTime) }}
              </span>
            </div>
          </div>
          <div class="head-actions">
            <el-button :loading="loading" @click="manualRefresh">
              <el-icon><Refresh /></el-icon>
              <span style="margin-left: 3px">刷新</span>
            </el-button>
            <el-button @click="goHistory">
              <el-icon><List /></el-icon>
              <span style="margin-left: 3px">历史记录</span>
            </el-button>
          </div>
        </div>
      </el-card>

      <!-- 首次加载 -->
      <el-card v-if="loading && !report" shadow="never">
        <el-skeleton :rows="6" animated />
      </el-card>

      <!-- 面试还没结束 -->
      <el-card v-else-if="sessionOngoing" shadow="never" class="state-card">
        <el-result icon="info" title="本场面试还在进行中">
          <template #sub-title>
            <p>面试结束后 AI 才会生成评估报告。你可以回到面试页继续回答，或者直接结束本场面试。</p>
          </template>
          <template #extra>
            <el-button type="primary" @click="goInterview">回到面试页继续</el-button>
            <el-button type="warning" plain :loading="finishing" @click="handleFinishNow">
              结束面试并生成报告
            </el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 报告生成中：有 SSE 进度就显示真实阶段，没有再退化成"已等待 N 秒" -->
      <el-card v-else-if="generating" shadow="never" class="state-card">
        <el-result icon="loading" :title="stageText || 'AI 正在生成面试报告…'">
          <template #sub-title>
            <p v-if="percent > 0">实时进度 {{ percent }}%</p>
            <p>
              已等待 {{ elapsed }} 秒，每 {{ POLL_INTERVAL / 1000 }} 秒自动刷新一次
              （第 {{ attempts }} / {{ MAX_ATTEMPTS }} 次轮询）
            </p>
            <p class="text-muted">评分需要把整场对话交给大模型分析，通常 5 - 30 秒完成，请不要关闭页面</p>
          </template>
          <template #extra>
            <el-button type="primary" plain @click="manualRefresh">立即刷新</el-button>
          </template>
        </el-result>
        <el-progress
          class="mt-16"
          :percentage="percent > 0 ? percent : Math.min(Math.round((attempts / MAX_ATTEMPTS) * 100), 100)"
          :stroke-width="6"
          :show-text="false"
          striped
          striped-flow
        />
      </el-card>

      <!-- 生成失败（后端推 FAILED 事件） -->
      <el-card v-else-if="failed" shadow="never" class="state-card">
        <el-result icon="error" title="报告生成失败">
          <template #sub-title>
            <p>{{ stageText || '评分任务执行出错，请稍后重试' }}</p>
          </template>
          <template #extra>
            <el-button type="primary" @click="manualRefresh">重新获取</el-button>
            <el-button @click="goInterview">返回面试主页</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 轮询超时 -->
      <el-card v-else-if="timedOut && !isReady" shadow="never" class="state-card">
        <el-result icon="warning" title="报告生成时间较长">
          <template #sub-title>
            <p>可能是大模型响应较慢或评分任务排队中，可以稍后点击「刷新」再看。</p>
          </template>
          <template #extra>
            <el-button type="primary" @click="manualRefresh">再查一次</el-button>
            <el-button @click="goInterview">返回面试主页</el-button>
          </template>
        </el-result>
      </el-card>

      <!-- 报告内容 -->
      <template v-else-if="isReady">
        <el-row :gutter="16">
          <!-- 总分 -->
          <el-col :xs="24" :md="8">
            <el-card shadow="never" class="score-card">
              <template #header>综合得分</template>
              <div class="score-wrap">
                <el-progress
                  type="dashboard"
                  :percentage="score"
                  :width="160"
                  :stroke-width="12"
                  :color="scoreColor(score)"
                >
                  <template #default>
                    <div class="score-inner">
                      <span class="score-num" :style="{ color: scoreColor(score) }">
                        {{ score }}
                      </span>
                      <span class="score-total">/ 100</span>
                    </div>
                  </template>
                </el-progress>

                <div class="score-level" :style="{ color: scoreColor(score) }">
                  {{ scoreLevel(score) }}
                </div>

                <el-divider />

                <div class="score-foot">
                  <span class="text-muted">总分越高说明表现越好，60 分为及格线</span>
                </div>
              </div>
            </el-card>
          </el-col>

          <!-- 评价 -->
          <el-col :xs="24" :md="16">
            <el-card shadow="never" class="summary-card">
              <template #header>总体评价</template>
              <p class="summary-text">
                {{ report.summary || '暂无评价内容' }}
              </p>
            </el-card>

            <el-row :gutter="16" class="mt-16">
              <!-- 优点 -->
              <el-col :xs="24" :md="12">
                <el-card shadow="never" class="list-card">
                  <template #header>
                    <div class="card-head">
                      <el-icon class="icon-success"><CircleCheckFilled /></el-icon>
                      <span>优点</span>
                      <el-tag size="small" type="success" effect="plain">
                        {{ strengths.length }} 条
                      </el-tag>
                    </div>
                  </template>

                  <el-empty
                    v-if="!strengths.length"
                    description="暂无数据"
                    :image-size="60"
                  />
                  <ul v-else class="point-list">
                    <li v-for="(item, index) in strengths" :key="index">
                      <el-icon class="icon-success"><Select /></el-icon>
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </el-card>
              </el-col>

              <!-- 不足 -->
              <el-col :xs="24" :md="12">
                <el-card shadow="never" class="list-card">
                  <template #header>
                    <div class="card-head">
                      <el-icon class="icon-warning"><WarningFilled /></el-icon>
                      <span>不足</span>
                      <el-tag size="small" type="warning" effect="plain">
                        {{ weaknesses.length }} 条
                      </el-tag>
                    </div>
                  </template>

                  <el-empty
                    v-if="!weaknesses.length"
                    description="暂无数据"
                    :image-size="60"
                  />
                  <ul v-else class="point-list">
                    <li v-for="(item, index) in weaknesses" :key="index">
                      <el-icon class="icon-warning"><CloseBold /></el-icon>
                      <span>{{ item }}</span>
                    </li>
                  </ul>
                </el-card>
              </el-col>
            </el-row>
          </el-col>
        </el-row>

        <!-- 改进建议 -->
        <el-card shadow="never" class="mt-16 suggestion-card">
          <template #header>
            <div class="card-head">
              <el-icon class="icon-primary"><Opportunity /></el-icon>
              <span>改进建议</span>
            </div>
          </template>
          <div class="suggestion-box">
            {{ report.suggestion || '暂无建议内容' }}
          </div>
        </el-card>

        <div class="bottom-actions">
          <el-button type="primary" @click="goInterview">
            <el-icon><VideoPlay /></el-icon>
            <span style="margin-left: 4px">再面一场</span>
          </el-button>
          <el-button @click="goHistory">查看历史面试记录</el-button>
        </div>
      </template>

      <!-- 兜底 -->
      <el-card v-else shadow="never" class="state-card">
        <el-empty description="没有查到报告数据">
          <el-button type="primary" @click="manualRefresh">重新获取</el-button>
        </el-empty>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.report-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.report-wrap {
  max-width: 1180px;
}

.head-card {
  margin-bottom: 16px;
}

.head-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.head-title {
  margin: 0 0 8px;
  font-size: 19px;
  color: #303133;
}

.head-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.mono {
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  color: #606266;
  background: #f4f6f8;
  padding: 2px 8px;
  border-radius: 4px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.state-card {
  padding: 20px 0;
}

/* 分数 */
.score-card {
  height: 100%;
}

.score-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 6px;
}

.score-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  line-height: 1.2;
}

.score-num {
  font-size: 40px;
  font-weight: 700;
}

.score-total {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.score-level {
  margin-top: 10px;
  font-size: 15px;
  font-weight: 600;
}

.score-foot {
  text-align: center;
  padding: 0 10px;
}

/* 评价 */
.summary-card :deep(.el-card__header),
.list-card :deep(.el-card__header) {
  padding: 12px 16px;
}

.summary-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
}

.card-head {
  display: flex;
  align-items: center;
  gap: 7px;
  font-weight: 600;
}

.icon-success {
  color: #67c23a;
}
.icon-warning {
  color: #e6a23c;
}
.icon-primary {
  color: #409eff;
}

.point-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.point-list li {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  padding: 8px 0;
  font-size: 13.5px;
  line-height: 1.75;
  color: #303133;
  border-bottom: 1px dashed #f0f2f5;
}

.point-list li:last-child {
  border-bottom: none;
}

.point-list li .el-icon {
  margin-top: 3px;
  flex-shrink: 0;
}

/* 建议 */
.suggestion-box {
  background: #f7fbff;
  border: 1px solid #d9ecff;
  border-radius: 8px;
  padding: 14px 16px;
  font-size: 14px;
  line-height: 1.9;
  color: #303133;
  white-space: pre-wrap;
}

.bottom-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin: 22px 0 40px;
}
</style>
