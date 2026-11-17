/**
 * 大整数安全的 JSON 解析
 *
 * 问题背景：
 * 后端 MyBatis-Plus 默认用雪花算法生成主键，`resumeId` 是 19 位 Long，
 * 在 JSON 里是一段「裸数字」。而 JS 的 Number 只能精确表示 2^53 - 1（约 16 位），
 * 所以 JSON.parse('{"resumeId":1856123456789012345}') 拿到的是 1856123456789012300 这种
 * 已经被四舍五入过的值 —— 精度在解析那一刻就丢了，再把这个 id 传回后端自然对不上。
 *
 * 解决思路：
 * 在真正 JSON.parse 之前先扫一遍文本，把「必然丢精度的整数」加引号变成字符串，
 * 这样 id 会被原样保留（'1856123456789012345'），其余字段（小数、科学计数法、字符串）完全不动。
 *
 * 前端单侧修复，不需要后端配合；后端如果把 Long 统一序列化成 String 会更彻底（见 request.js 注释）。
 */

const CHAR_QUOTE = 34 // "
const CHAR_BACKSLASH = 92 // \
const CHAR_PLUS = 43 // +
const CHAR_MINUS = 45 // -
const CHAR_DOT = 46 // .
const CHAR_0 = 48 // 0
const CHAR_9 = 57 // 9
const CHAR_UPPER_E = 69 // E
const CHAR_LOWER_E = 101 // e

function isDigit(code) {
  return code >= CHAR_0 && code <= CHAR_9
}

/** 小数 / 科学计数法的尾部字符（1.5e-3 之类），遇到就整体跳过 */
function isNumberTail(code) {
  return (
    isDigit(code) ||
    code === CHAR_PLUS ||
    code === CHAR_MINUS ||
    code === CHAR_DOT ||
    code === CHAR_UPPER_E ||
    code === CHAR_LOWER_E
  )
}

/**
 * 判断一个「不带小数点、不带指数」的整数字面量是否会丢精度
 * - 超出 Number 安全整数范围 → 必丢
 * - 转成 Number 再转回字符串和原文不一致 → 必丢
 * 返回 true 表示必须加引号按字符串处理。
 */
export function isUnsafeIntegerLiteral(literal) {
  const num = Number(literal)
  return !Number.isSafeInteger(num) || String(num) !== literal
}

/**
 * 扫描 JSON 文本，给会丢精度的整数加上双引号（其余字符原样保留）。
 * 之所以要自己扫而不是用正则：正则分不清「字符串里的数字」和「真正的数字」。
 * 已用转义规则正确跳过字符串字面量，所以 `"a": "1856123456789012345"` 不会被误加引号。
 */
export function quoteUnsafeIntegers(text) {
  const len = text.length
  const parts = []
  let last = 0
  let i = 0

  while (i < len) {
    const code = text.charCodeAt(i)

    // ---- 字符串字面量：整段跳过（处理 \\ 和 \" 转义）----
    if (code === CHAR_QUOTE) {
      i++
      while (i < len) {
        const c = text.charCodeAt(i)
        if (c === CHAR_BACKSLASH) {
          i += 2
          continue
        }
        i++
        if (c === CHAR_QUOTE) break
      }
      continue
    }

    // ---- 可能是数字字面量的起点 ----
    if (code === CHAR_MINUS || isDigit(code)) {
      const start = i
      if (code === CHAR_MINUS) i++
      const digitsStart = i
      while (i < len && isDigit(text.charCodeAt(i))) i++

      // 独立的 '-' 之类不是合法数字，交给 JSON.parse 去报错
      if (i === digitsStart) continue

      const next = i < len ? text.charCodeAt(i) : -1
      // 小数 / 科学计数法：保持原样
      if (next === CHAR_DOT || next === CHAR_UPPER_E || next === CHAR_LOWER_E) {
        i++
        while (i < len && isNumberTail(text.charCodeAt(i))) i++
        continue
      }

      const literal = text.slice(start, i)
      if (isUnsafeIntegerLiteral(literal)) {
        parts.push(text.slice(last, start), '"', literal, '"')
        last = i
      }
      continue
    }

    i++
  }

  // 没改过就直接返回原文本，避免多余的内存拷贝
  if (!parts.length) return text
  parts.push(text.slice(last))
  return parts.join('')
}

/**
 * 替代 JSON.parse 的大整数安全版本
 * 非法 JSON 会照常抛出 SyntaxError，由调用方决定怎么兜底。
 */
export function parseJsonPreserveBigInt(text) {
  if (typeof text !== 'string') return text
  return JSON.parse(quoteUnsafeIntegers(text))
}

/**
 * 把任意 id（number / string / null）规范成字符串
 * 统一类型后，缓存里的 id 和接口返回的 id 用 === 比较才不会翻车。
 * 已丢精度的 Number（超过安全整数范围）无法还原，返回 ''，由调用方决定是否丢弃。
 */
export function toIdString(value) {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'number') {
    if (!Number.isFinite(value) || !Number.isSafeInteger(value)) return ''
    return String(value)
  }
  return String(value)
}
