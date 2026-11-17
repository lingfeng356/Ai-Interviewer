<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { createWavRecorder } from '@/utils/audio'

const props = defineProps({
  // 上传中（发送按钮 loading / 禁用）
  submitting: { type: Boolean, default: false },
  // 面试未开始等情况下禁用
  disabled: { type: Boolean, default: false },
  // 最长录音秒数
  maxSeconds: { type: Number, default: 120 }
})

const emit = defineEmits(['submit'])

const recording = ref(false)
const seconds = ref(0)
const audioBlob = ref(null)
const previewUrl = ref('')
const previewing = ref(false)

let recorder = null
let timer = null
let previewAudio = null

const hasRecord = computed(() => !!audioBlob.value)

const formatted = computed(() => {
  const m = String(Math.floor(seconds.value / 60)).padStart(2, '0')
  const s = String(seconds.value % 60).padStart(2, '0')
  return `${m}:${s}`
})

const canSubmit = computed(() => hasRecord.value && !props.submitting && !props.disabled)

function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function stopPreview() {
  if (previewAudio) {
    previewAudio.pause()
    previewAudio = null
    previewing.value = false
  }
}

function clearRecord() {
  stopPreview()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
  }
  audioBlob.value = null
  seconds.value = 0
}

async function start() {
  if (props.disabled || props.submitting || recording.value) return

  try {
    recorder = await createWavRecorder(16000)
  } catch (e) {
    ElMessage.error(e.message || '无法访问麦克风，请检查浏览器权限')
    recorder = null
    return
  }

  clearRecord()
  recording.value = true

  timer = setInterval(() => {
    seconds.value += 1
    if (seconds.value >= props.maxSeconds) {
      ElMessage.warning(`已达到最长录音时长 ${props.maxSeconds} 秒，自动停止`)
      stop()
    }
  }, 1000)
}

async function stop() {
  if (!recording.value || !recorder) return

  recording.value = false
  stopTimer()

  const duration = seconds.value
  try {
    const blob = await recorder.stop()
    recorder = null
    seconds.value = duration
    if (!blob || !blob.size) {
      ElMessage.warning('没有录到声音，请重试')
      return
    }
    audioBlob.value = blob
    previewUrl.value = URL.createObjectURL(blob)
  } catch (e) {
    ElMessage.error('录音处理失败，请重试')
    recorder = null
  }
}

function cancel() {
  if (recorder) {
    recorder.cancel()
    recorder = null
  }
  recording.value = false
  stopTimer()
  clearRecord()
}

function reset() {
  clearRecord()
}

function togglePreview() {
  if (!previewUrl.value) return
  if (previewing.value) {
    stopPreview()
    return
  }
  previewAudio = new Audio(previewUrl.value)
  previewAudio.onended = () => {
    previewing.value = false
    previewAudio = null
  }
  previewing.value = true
  previewAudio.play().catch(() => {
    previewing.value = false
  })
}

function submit() {
  if (!canSubmit.value) return
  emit('submit', audioBlob.value, seconds.value)
}

onUnmounted(() => {
  stopTimer()
  stopPreview()
  if (recorder) {
    recorder.cancel()
    recorder = null
  }
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})

defineExpose({ reset, clearRecord })
</script>

<template>
  <div class="voice-recorder">
    <!-- 待录制 -->
    <div v-if="!recording && !hasRecord" class="row">
      <el-button
        type="primary"
        :disabled="disabled || submitting"
        @click="start"
      >
        <el-icon class="mr-4"><Microphone /></el-icon>
        开始录音
      </el-button>
      <span class="text-muted">
        点击开始录音，讲完点「停止并发送」，最长 {{ maxSeconds }} 秒
      </span>
    </div>

    <!-- 录制中 -->
    <div v-else-if="recording" class="row">
      <span class="rec-dot" />
      <span class="timer">{{ formatted }}</span>
      <span class="text-muted">正在录音，请对着麦克风回答…</span>
      <div class="spacer" />
      <el-button type="danger" @click="stop">停止录音</el-button>
      <el-button text @click="cancel">取消</el-button>
    </div>

    <!-- 录制完成 -->
    <div v-else class="row">
      <el-button text :disabled="submitting" @click="togglePreview">
        <el-icon class="mr-4">
          <VideoPause v-if="previewing" />
          <VideoPlay v-else />
        </el-icon>
        {{ previewing ? '暂停试听' : '试听' }}
      </el-button>
      <span class="text-muted">已录制 {{ formatted }}，确认后发送给面试官</span>
      <div class="spacer" />
      <el-button text :disabled="submitting" @click="reset">重录</el-button>
      <el-button type="primary" :loading="submitting" :disabled="disabled" @click="submit">
        停止并发送
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.voice-recorder {
  width: 100%;
}

.row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.spacer {
  flex: 1;
}

.mr-4 {
  margin-right: 4px;
}

.rec-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f56c6c;
  animation: blink 1s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.2;
  }
}

.timer {
  font-family: Consolas, Monaco, monospace;
  font-size: 15px;
  color: #f56c6c;
  font-weight: 600;
}
</style>
