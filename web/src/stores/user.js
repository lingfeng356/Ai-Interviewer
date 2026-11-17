import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as userApi from '@/api/user'
import {
  getToken,
  setToken,
  getUser,
  setUser,
  clearAuth
} from '@/utils/storage'

/**
 * 用户 store：token 存 localStorage，刷新页面不丢登录态
 * 注意：后端 UserController 只有 register / login 两个接口，没有 userinfo，
 * 所以昵称这些信息在登录时前端自己记下来。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref(getToken())
  const userInfo = ref(getUser() || {})

  const isLogin = computed(() => !!token.value)
  const displayName = computed(
    () => userInfo.value.nickname || userInfo.value.username || '未登录'
  )

  async function doRegister(payload) {
    return userApi.register(payload)
  }

  async function doLogin({ username, password, nickname }) {
    const data = await userApi.login({ username, password })
    token.value = data
    setToken(data)

    const profile = { username, nickname: nickname || username }
    userInfo.value = profile
    setUser(profile)
    return data
  }

  function logout() {
    token.value = ''
    userInfo.value = {}
    clearAuth()
  }

  return {
    token,
    userInfo,
    isLogin,
    displayName,
    doLogin,
    doRegister,
    logout
  }
})
