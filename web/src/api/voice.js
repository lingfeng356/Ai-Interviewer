import request from './request'

/**
 * 语音面试接口（一次性走完 ASR -> AI -> TTS）
 * POST /interview/voice
 * form-data: sessionId, audio（音频文件）
 * data: { userText, aiText, aiAudio }  —— aiAudio 是 base64(mp3)
 *
 * 说明：后端会把上传的音频写成临时 .wav 文件再送去识别，
 * 所以前端录音统一编码成 16k / 单声道 / PCM WAV 上传，兼容性最好。
 */
export function voiceInterview(sessionId, audioBlob, fileName = 'answer.wav') {
  const formData = new FormData()
  formData.append('sessionId', sessionId)
  formData.append('audio', audioBlob, fileName)

  return request.post('/interview/voice', formData, { timeout: 180000 })
}
