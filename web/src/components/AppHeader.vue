<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const displayName = computed(() => userStore.displayName)

function onCommand(command) {
  if (command === 'interview') {
    router.push('/interview')
    return
  }
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
      .then(() => {
        userStore.logout()
        ElMessage.success('已退出登录')
        router.replace('/login')
      })
      .catch(() => {})
  }
}
</script>

<template>
  <header class="app-header">
    <div class="brand" @click="router.push('/interview')">
      <div class="logo">
        <el-icon :size="18"><ChatDotRound /></el-icon>
      </div>
      <span class="title">AI 面试官平台</span>
      <el-tag size="small" effect="plain" class="direction-tag">Java 后端</el-tag>
    </div>

    <div class="right">
      <slot name="actions" />
      <el-dropdown trigger="click" @command="onCommand">
        <span class="user-info">
          <el-icon><UserFilled /></el-icon>
          <span class="name">{{ displayName }}</span>
          <el-icon class="arrow"><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="interview">
              <el-icon><HomeFilled /></el-icon> 面试主页
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  height: 58px;
  padding: 0 20px;
  background: #ffffff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 100;
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  user-select: none;
}

.logo {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.direction-tag {
  margin-left: 2px;
}

.right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: #606266;
  font-size: 14px;
  outline: none;
}

.user-info:hover {
  color: #409eff;
}

.user-info .name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow {
  font-size: 12px;
  color: #909399;
}
</style>
