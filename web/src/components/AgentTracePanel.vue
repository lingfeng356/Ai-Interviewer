<script setup>
import { computed, ref } from 'vue'

/**
 * Agent 执行轨迹面板。
 *
 * 展示的是「可观测的工具调用链」：调了哪个工具、传了什么、拿回什么、多久、成功与否。
 * 注意不要说成"思考过程"——DeepSeek 的 chat 模型不返回思维链，这里只有工具调用事实。
 */
const props = defineProps({
  agent: { type: Object, required: true }
})

const expanded = ref(false)

const TOOL_LABELS = {
  searchResume: '检索简历',
  searchKnowledge: '查知识库',
  searchQuestionBank: '查题库',
  saveMemo: '记笔记'
}

const toolCalls = computed(() => (props.agent && props.agent.toolCalls) || [])
const count = computed(() => toolCalls.value.length)
const elapsed = computed(() => {
  const ms = (props.agent && props.agent.elapsedMs) || 0
  return (ms / 1000).toFixed(1)
})
const isFallback = computed(() => props.agent && props.agent.mode === 'FALLBACK')

const summary = computed(() => {
  if (count.value === 0) return `Agent · 未调用工具 · ${elapsed.value}s`
  return `Agent · ${count.value} 次工具调用 · ${elapsed.value}s`
})

function label(tool) {
  return TOOL_LABELS[tool] || tool
}
</script>

<template>
  <div class="agent-panel" :class="{ 'is-fallback': isFallback }">
    <div class="agent-head" @click="expanded = !expanded">
      <el-icon class="caret" :class="{ open: expanded }">
        <ArrowRight />
      </el-icon>
      <span class="summary">{{ summary }}</span>
      <el-tag v-if="isFallback" size="small" type="warning" effect="plain">
        本轮已降级
      </el-tag>
      <span class="hint">{{ expanded ? '收起' : '查看过程' }}</span>
    </div>

    <div v-if="expanded" class="agent-body">
      <div v-if="isFallback" class="fallback-note">
        本轮 Agent 调用失败，已自动回退到经典模式生成问题（不影响继续面试）。
      </div>

      <div v-if="!count" class="empty">本轮面试官没有调用工具，直接给出了问题。</div>

      <div v-for="step in toolCalls" :key="step.step" class="step">
        <div class="step-head">
          <span class="idx">{{ step.step }}</span>
          <span class="tool">{{ label(step.tool) }}</span>
          <span class="tool-raw">{{ step.tool }}</span>
          <span class="latency">{{ step.latencyMs }}ms</span>
          <el-tag v-if="step.success" size="small" type="success" effect="plain">成功</el-tag>
          <el-tag v-else size="small" type="danger" effect="plain">失败</el-tag>
        </div>
        <div class="kv"><span class="k">参数</span><span class="v">{{ step.args || '-' }}</span></div>
        <div class="kv"><span class="k">结果</span><span class="v">{{ step.result || '-' }}</span></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-panel {
  margin-top: 6px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafcff;
  overflow: hidden;
}

.agent-panel.is-fallback {
  border-color: #faecd8;
  background: #fffdf7;
}

.agent-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  font-size: 12px;
  color: #606266;
  user-select: none;
}

.agent-head:hover {
  background: #f2f7ff;
}

.caret {
  transition: transform 0.15s ease;
}

.caret.open {
  transform: rotate(90deg);
}

.summary {
  font-weight: 500;
}

.hint {
  margin-left: auto;
  color: #909399;
}

.agent-body {
  border-top: 1px dashed #ebeef5;
  padding: 8px 10px;
}

.fallback-note {
  font-size: 12px;
  color: #e6a23c;
  margin-bottom: 8px;
}

.empty {
  font-size: 12px;
  color: #909399;
}

.step {
  padding: 6px 0;
  border-bottom: 1px dashed #f0f2f5;
}

.step:last-child {
  border-bottom: none;
}

.step-head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.idx {
  width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  background: #ecf5ff;
  color: #409eff;
  font-size: 11px;
}

.tool {
  color: #303133;
  font-weight: 500;
}

.tool-raw {
  color: #c0c4cc;
  font-family: Consolas, Monaco, monospace;
  font-size: 11px;
}

.latency {
  margin-left: auto;
  color: #909399;
}

.kv {
  display: flex;
  gap: 8px;
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
}

.k {
  flex-shrink: 0;
  color: #909399;
}

.v {
  color: #606266;
  word-break: break-word;
  white-space: pre-wrap;
}
</style>
