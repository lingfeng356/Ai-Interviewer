<script setup>
import { computed } from 'vue'
import AgentTracePanel from './AgentTracePanel.vue'

const props = defineProps({
  message: { type: Object, required: true }
})

const emit = defineEmits(['play'])

const isAi = computed(() => props.message.role === 'ai')
const isVoice = computed(() => props.message.from === 'voice')
// 只有 Agent 模式下的 AI 消息才带轨迹
const hasAgent = computed(() => !!(props.message.agent && props.message.agent.mode))
</script>

<template>
  <div class="bubble-row" :class="isAi ? 'row-ai' : 'row-user'">
    <el-avatar :size="34" class="avatar" :class="isAi ? 'avatar-ai' : 'avatar-user'">
      {{ isAi ? 'AI' : '我' }}
    </el-avatar>

    <div class="bubble-body">
      <div class="meta">
        <span class="name">{{ isAi ? '面试官' : '我' }}</span>
        <span class="time">{{ message.time }}</span>
        <el-tag v-if="isVoice" size="small" type="info" effect="plain">语音</el-tag>
      </div>

      <div class="bubble" :class="isAi ? 'bubble-ai' : 'bubble-user'">
        <span class="content">{{ message.content }}</span>
      </div>

      <div v-if="isAi" class="actions">
        <el-button
          v-if="message.audio"
          size="small"
          text
          type="primary"
          @click="emit('play', message)"
        >
          <el-icon><VideoPlay /></el-icon>
          <span style="margin-left: 3px">播放语音</span>
        </el-button>
        <span v-else class="text-muted">暂无语音</span>
      </div>

      <!-- Agent 执行轨迹：默认折叠，点开看本轮调用了哪些工具 -->
      <AgentTracePanel v-if="isAi && hasAgent" :agent="message.agent" />
    </div>
  </div>
</template>

<style scoped>
.bubble-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  align-items: flex-start;
}

.row-user {
  flex-direction: row-reverse;
}

.avatar {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
}

.avatar-ai {
  background: #ecf5ff;
  color: #409eff;
}

.avatar-user {
  background: #f0f2f5;
  color: #606266;
}

.bubble-body {
  max-width: 76%;
  display: flex;
  flex-direction: column;
}

.row-user .bubble-body {
  align-items: flex-end;
}

.meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 5px;
  font-size: 12px;
  color: #909399;
}

.row-user .meta {
  flex-direction: row-reverse;
}

.meta .name {
  color: #606266;
  font-weight: 500;
}

.bubble {
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
  white-space: pre-wrap;
}

.bubble-ai {
  background: #ffffff;
  border: 1px solid #ebeef5;
  color: #303133;
  border-top-left-radius: 3px;
}

.bubble-user {
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  color: #303133;
  border-top-right-radius: 3px;
}

.actions {
  margin-top: 4px;
  height: 24px;
  display: flex;
  align-items: center;
}
</style>
