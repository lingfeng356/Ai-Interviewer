/**
 * 音频工具
 * 1) playBase64Audio —— 播放后端返回的 base64 语音（AI 回复自动转语音）
 * 2) createWavRecorder —— 浏览器录音并编码成 16k / 单声道 / 16bit PCM WAV
 *
 * 为什么不用 MediaRecorder：浏览器 MediaRecorder 一般只能录 webm/opus，
 * 而后端把上传的文件名固定写成 .wav 再送去 DashScope 识别，
 * 所以这里用 Web Audio API 自己采集 PCM 并封装 WAV 头，兼容性最稳。
 */

/* ============================ 播放 ============================ */
let currentAudio = null
let currentStopHandler = null

export function stopAudio() {
  if (currentAudio) {
    try {
      currentAudio.pause()
      currentAudio.currentTime = 0
    } catch (e) {
      /* ignore */
    }
    currentAudio = null
  }
  if (currentStopHandler) {
    currentStopHandler()
    currentStopHandler = null
  }
}

/**
 * 播放 base64 音频
 * @param {string} base64 后端返回的 base64 字符串（可能已带 data: 前缀）
 * @param {object} options { mime, onStart, onEnd }
 */
export function playBase64Audio(base64, options = {}) {
  const { mime = 'audio/mp3', onStart, onEnd } = options
  if (!base64) return

  stopAudio()

  const src = base64.startsWith('data:')
    ? base64
    : `data:${mime};base64,${base64}`

  const audio = new Audio(src)
  currentAudio = audio

  const finish = () => {
    if (currentAudio === audio) currentAudio = null
    if (currentStopHandler === finish) currentStopHandler = null
    if (typeof onEnd === 'function') onEnd()
  }

  // 页面上删除气泡时用来中断
  currentStopHandler = finish

  audio.onended = finish
  audio.onerror = finish

  const playPromise = audio.play()
  if (playPromise && typeof playPromise.then === 'function') {
    playPromise
      .then(() => {
        if (typeof onStart === 'function') onStart()
      })
      .catch((err) => {
        console.warn('[audio] 自动播放被拦截：', err)
        finish()
      })
  } else if (typeof onStart === 'function') {
    onStart()
  }
}

/* ============================ 录音 ============================ */
function writeString(view, offset, str) {
  for (let i = 0; i < str.length; i++) {
    view.setUint8(offset + i, str.charCodeAt(i))
  }
}

function mergeChannels(chunks) {
  let length = 0
  chunks.forEach((c) => {
    length += c.length
  })
  const result = new Float32Array(length)
  let offset = 0
  chunks.forEach((c) => {
    result.set(c, offset)
    offset += c.length
  })
  return result
}

function downsample(buffer, inputRate, outputRate) {
  if (outputRate === inputRate) return buffer
  const sampleRateRatio = inputRate / outputRate
  const newLength = Math.round(buffer.length / sampleRateRatio)
  const result = new Float32Array(newLength)
  let offsetResult = 0
  let offsetBuffer = 0

  while (offsetResult < newLength) {
    const nextOffsetBuffer = Math.round((offsetResult + 1) * sampleRateRatio)
    let accum = 0
    let count = 0
    for (let i = offsetBuffer; i < nextOffsetBuffer && i < buffer.length; i++) {
      accum += buffer[i]
      count++
    }
    result[offsetResult] = count > 0 ? accum / count : 0
    offsetResult++
    offsetBuffer = nextOffsetBuffer
  }
  return result
}

function encodeWav(samples, sampleRate) {
  const bytesPerSample = 2
  const buffer = new ArrayBuffer(44 + samples.length * bytesPerSample)
  const view = new DataView(buffer)

  writeString(view, 0, 'RIFF')
  view.setUint32(4, 36 + samples.length * bytesPerSample, true)
  writeString(view, 8, 'WAVE')
  writeString(view, 12, 'fmt ')
  view.setUint32(16, 16, true) // PCM 子块大小
  view.setUint16(20, 1, true) // 格式：PCM
  view.setUint16(22, 1, true) // 声道数：1
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * bytesPerSample, true) // 字节率
  view.setUint16(32, bytesPerSample, true) // 块对齐
  view.setUint16(34, 16, true) // 位深
  writeString(view, 36, 'data')
  view.setUint32(40, samples.length * bytesPerSample, true)

  let offset = 44
  for (let i = 0; i < samples.length; i++, offset += bytesPerSample) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7fff, true)
  }

  return new Blob([view], { type: 'audio/wav' })
}

/**
 * 创建一个 WAV 录音器
 * @returns {Promise<{ stop: () => Promise<Blob>, cancel: () => void, duration: () => number }>}
 */
export async function createWavRecorder(targetSampleRate = 16000) {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error('当前浏览器不支持录音，请使用 Chrome / Edge')
  }

  const stream = await navigator.mediaDevices.getUserMedia({
    audio: {
      channelCount: 1,
      echoCancellation: true,
      noiseSuppression: true,
      autoGainControl: true
    }
  })

  const AudioCtx = window.AudioContext || window.webkitAudioContext
  const audioContext = new AudioCtx()
  if (audioContext.state === 'suspended') {
    await audioContext.resume()
  }

  const source = audioContext.createMediaStreamSource(stream)
  const processor = audioContext.createScriptProcessor(4096, 1, 1)
  const silence = audioContext.createGain()
  silence.gain.value = 0

  const chunks = []
  const startedAt = Date.now()

  processor.onaudioprocess = (event) => {
    chunks.push(new Float32Array(event.inputBuffer.getChannelData(0)))
  }

  source.connect(processor)
  processor.connect(silence)
  silence.connect(audioContext.destination)

  const release = () => {
    try {
      processor.disconnect()
      source.disconnect()
      silence.disconnect()
    } catch (e) {
      /* ignore */
    }
    stream.getTracks().forEach((t) => t.stop())
    if (audioContext.state !== 'closed') audioContext.close()
  }

  return {
    duration: () => Math.round((Date.now() - startedAt) / 1000),
    cancel: release,
    stop: async () => {
      release()
      const merged = mergeChannels(chunks)
      const samples = downsample(merged, audioContext.sampleRate, targetSampleRate)
      return encodeWav(samples, targetSampleRate)
    }
  }
}

/**
 * 把 Blob 的 base64 转成可直接播放的 URL（用于录音预览）
 */
export function blobToObjectUrl(blob) {
  return URL.createObjectURL(blob)
}
