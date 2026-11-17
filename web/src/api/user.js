import request from './request'

/**
 * 注册  POST /user/register
 * body: { username, password, nickname }
 * data: null
 */
export function register(data) {
  return request.post('/user/register', data)
}

/**
 * 登录  POST /user/login
 * body: { username, password }
 * data: token 字符串（Sa-Token 的 Token）
 */
export function login(data) {
  return request.post('/user/login', data)
}
