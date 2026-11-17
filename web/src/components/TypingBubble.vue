<script setup>
// AI 思考中的占位气泡
</script>

<template>
  <div class="bubble-row">
    <el-avatar :size="34" class="avatar">AI</el-avatar>
    <div class="bubble-body">
      <div class="meta"><span class="name">面试官</span></div>
      <div class="bubble typing">
        <span class="dot" />
        <span class="dot" />
        <span class="dot" />
      </div>
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

.avatar {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
  background: #ecf5ff;
  color: #409eff;
}

.meta {
  font-size: 12px;
  color: #606266;
  margin-bottom: 5px;
}

.typing {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 14px 16px;
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  border-top-left-radius: 3px;
  width: fit-content;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: bounce 1.2s infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.18s;
}
.dot:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes bounce {
  0%,
  60%,
  100% {
    transform: translateY(0);
    opacity: 0.5;
  }
  30% {
    transform: translateY(-5px);
    opacity: 1;
  }
}
</style>
