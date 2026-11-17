<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeTab = ref('login')
const loading = ref(false)

const loginFormRef = ref(null)
const registerFormRef = ref(null)

const loginForm = reactive({
  username: '',
  password: ''
})

const registerForm = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: ''
})

const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3 - 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度为 3 - 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const submitText = computed(() =>
  activeTab.value === 'login' ? '登 录' : '注 册'
)

function goAfterLogin() {
  const redirect = route.query.redirect
  router.replace(typeof redirect === 'string' && redirect ? redirect : '/interview')
}

async function handleLogin() {
  if (!loginFormRef.value) return
  try {
    await loginFormRef.value.validate()
  } catch (e) {
    return
  }

  loading.value = true
  try {
    await userStore.doLogin({
      username: loginForm.username.trim(),
      password: loginForm.password
    })
    ElMessage.success('登录成功')
    goAfterLogin()
  } catch (e) {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerFormRef.value) return
  try {
    await registerFormRef.value.validate()
  } catch (e) {
    return
  }

  loading.value = true
  try {
    await userStore.doRegister({
      username: registerForm.username.trim(),
      password: registerForm.password,
      nickname: registerForm.nickname.trim() || registerForm.username.trim()
    })
    ElMessage.success('注册成功，请登录')

    // 注册成功后自动填到登录表单
    loginForm.username = registerForm.username.trim()
    loginForm.password = ''
    activeTab.value = 'login'

    registerFormRef.value.resetFields()
  } catch (e) {
    /* ignore */
  } finally {
    loading.value = false
  }
}

function handleSubmit() {
  if (activeTab.value === 'login') {
    handleLogin()
  } else {
    handleRegister()
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <!-- 左侧介绍 -->
      <div class="intro">
        <div class="brand">
          <div class="logo">
            <el-icon :size="22"><ChatDotRound /></el-icon>
          </div>
          <div>
            <h1>AI 面试官平台</h1>
            <p class="sub">上传简历，和 AI 面试官来一场真实的技术面试</p>
          </div>
        </div>

        <ul class="feature-list">
          <li>
            <el-icon><Document /></el-icon>
            <span>上传 PDF 简历，AI 读懂你的项目经历</span>
          </li>
          <li>
            <el-icon><Microphone /></el-icon>
            <span>文字 / 语音两种面试模式，像真人对话一样</span>
          </li>
          <li>
            <el-icon><VideoPlay /></el-icon>
            <span>AI 回答自动转语音，面试官"开口"提问</span>
          </li>
          <li>
            <el-icon><DataAnalysis /></el-icon>
            <span>面试结束自动生成评分报告与改进建议</span>
          </li>
        </ul>
      </div>

      <!-- 右侧表单 -->
      <div class="form-area">
        <el-tabs v-model="activeTab" stretch>
          <el-tab-pane label="登录" name="login">
            <el-form
              ref="loginFormRef"
              :model="loginForm"
              :rules="loginRules"
              label-position="top"
              size="large"
              @keyup.enter="handleLogin"
            >
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="loginForm.username"
                  placeholder="请输入用户名"
                  clearable
                >
                  <template #prefix>
                    <el-icon><User /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="loginForm.password"
                  type="password"
                  placeholder="请输入密码"
                  show-password
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-button
                type="primary"
                class="submit-btn"
                :loading="loading"
                @click="handleLogin"
              >
                登 录
              </el-button>

              <div class="tip">
                <el-icon><InfoFilled /></el-icon>
                <span>连续登录失败会触发限流（1 分钟最多 5 次），请稍后再试</span>
              </div>
            </el-form>
          </el-tab-pane>

          <el-tab-pane label="注册" name="register">
            <el-form
              ref="registerFormRef"
              :model="registerForm"
              :rules="registerRules"
              label-position="top"
              size="large"
              @keyup.enter="handleRegister"
            >
              <el-form-item label="用户名" prop="username">
                <el-input
                  v-model="registerForm.username"
                  placeholder="3 - 20 个字符"
                  clearable
                >
                  <template #prefix>
                    <el-icon><User /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="昵称（选填）" prop="nickname">
                <el-input
                  v-model="registerForm.nickname"
                  placeholder="不填则默认与用户名相同"
                  clearable
                >
                  <template #prefix>
                    <el-icon><Postcard /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="registerForm.password"
                  type="password"
                  placeholder="至少 6 位"
                  show-password
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input
                  v-model="registerForm.confirmPassword"
                  type="password"
                  placeholder="请再次输入密码"
                  show-password
                >
                  <template #prefix>
                    <el-icon><Lock /></el-icon>
                  </template>
                </el-input>
              </el-form-item>

              <el-button
                type="primary"
                class="submit-btn"
                :loading="loading"
                @click="handleRegister"
              >
                注 册
              </el-button>
            </el-form>
          </el-tab-pane>
        </el-tabs>
      </div>
    </div>

    <p class="footer">AI Interviewer · 基于大模型的模拟面试平台</p>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #f5f7fa 0%, #ecf5ff 55%, #e8f4ff 100%);
  padding: 30px 18px;
}

.login-box {
  width: 100%;
  max-width: 940px;
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #ebeef5;
  box-shadow: 0 8px 30px rgba(64, 158, 255, 0.08);
  overflow: hidden;
}

.intro {
  padding: 44px 38px;
  background: linear-gradient(160deg, #f0f7ff 0%, #fafcff 100%);
  border-right: 1px solid #ebeef5;
}

.brand {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 34px;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.intro h1 {
  margin: 0;
  font-size: 20px;
  color: #303133;
}

.intro .sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}

.feature-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feature-list li {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
  border-bottom: 1px dashed #e4e7ed;
}

.feature-list li:last-child {
  border-bottom: none;
}

.feature-list .el-icon {
  color: #409eff;
  margin-top: 3px;
  flex-shrink: 0;
}

.form-area {
  padding: 36px 40px;
}

.submit-btn {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 2px;
}

.tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 14px;
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
}

.tip .el-icon {
  color: #e6a23c;
  flex-shrink: 0;
}

.footer {
  margin-top: 22px;
  font-size: 12px;
  color: #a8abb2;
}

@media (max-width: 820px) {
  .login-box {
    grid-template-columns: 1fr;
  }
  .intro {
    display: none;
  }
  .form-area {
    padding: 28px 22px;
  }
}
</style>
