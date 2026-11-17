# AI 面试官平台 · 前端

Vue 3（Composition API + `<script setup>`）+ Element Plus + Axios + Vue Router + Pinia，纯前端三件套，**未修改任何后端代码**。

后端项目：`D:\AI interviewer project\AI interviewer`（Spring Boot，端口 **8081**，Sa-Token 鉴权）

---

## 一、快速开始

```bash
cd "D:\AI interviewer project\AI interviewer\web"
npm install
npm run dev        # http://127.0.0.1:5173
```

先启动后端（8081），再打开前端地址。打包：`npm run build`（产物在 `dist/`）。

### 为什么不用配 CORS

后端 `SaTokenConfig` 只配了拦截器，**没有配任何跨域**，所以前端统一走 Vite 代理：

```
前端请求 /api/user/login  →  Vite 代理  →  http://localhost:8081/user/login
```

代理配置在 `vite.config.js` 的 `server.proxy`，已关闭超时（SSE 需要长连接）。

> 如果要部署到生产环境（nginx / 静态托管），有两种做法：
> 1. nginx 里把 `/api` 反代到 `http://后端:8081`；
> 2. 或者在后端加一个 CORS 配置（`WebMvcConfigurer#addCorsMappings`），再把 `src/api/request.js` 里的 `baseURL` 换成后端完整地址。

---

### 桌面一键启动脚本

不想每次开终端敲命令的话，桌面上已经放了一个 **`启动前端.bat`**，双击即可：

它会依次做 4 件事：
1. 检查项目目录和 `npm` 是否存在（不存在会明确提示并停住）；
2. 探测后端 `8081` 是否在监听（**没启动会给出提示**，但前端照样能开）；
3. `node_modules\vite` 不存在时自动 `npm install`；
4. 启动 `npm run dev`，并在 6 秒后自动打开 http://127.0.0.1:5173/ 。

> 关掉窗口或按 Ctrl + C 即可停止服务。
> 如果 5173 被占用，Vite 会自动改用 5174，此时以终端里打印的地址为准。

### 脚本的维护方式（重要）

脚本源码在 `scripts/`，**桌面那份是生成物，不要直接编辑桌面文件**：

| 文件 | 作用 |
| --- | --- |
| `scripts/start-frontend.bat` | 母本，UTF-8 + LF，方便编辑和 diff |
| `scripts/to-desktop.ps1` | 转换器：母本 → **GBK + CRLF**，输出到桌面 |

改完之后重新生成一次：

```powershell
powershell -ExecutionPolicy Bypass -File ".\scripts\to-desktop.ps1"
```

**为什么要这么绕**：`.bat` 里有中文，必须用 GBK（代码页 936）编码，否则 cmd 显示乱码、甚至会把 `rem` 注释里的中文当成命令执行；cmd 的批处理解析器还要求 CRLF 换行，LF 会把行切碎；同时 `.bat` 绝不能带 UTF-8 BOM，否则第一行的 `@echo off` 会变成非法命令。用编辑器直接改桌面那份 GBK 文件，会被按 UTF-8 读写从而写坏中文。

---

## 二、目录结构

```
web/
├── index.html                     # 入口 HTML
├── package.json
├── vite.config.js                 # 别名 + /api 代理到 8081
├── jsconfig.json                  # @ 别名提示
├── scripts/
│   ├── start-frontend.bat         # 启动脚本母本（UTF-8 + LF）
│   └── to-desktop.ps1             # 转成 GBK + CRLF 输出到桌面
└── src/
    ├── main.js                    # 挂载 Vue / Pinia / Router / Element Plus（中文语言包 + 全量图标）
    ├── App.vue                    # 路由出口
    ├── api/
    │   ├── request.js             # ★ axios 实例：请求拦截器加 satoken，响应拦截器统一处理 code/401
    │   ├── user.js                # 登录 / 注册
    │   ├── resume.js              # 简历上传（带进度）
    │   ├── interview.js           # 开始 / 回答 / 结束 / 报告 / 列表 / SSE 地址
    │   └── voice.js               # 语音面试（录音上传）
    ├── router/
    │   └── index.js               # 路由 + 登录守卫
    ├── stores/
    │   ├── user.js                # token / 用户信息
    │   └── interview.js           # ★ 会话状态、消息列表、发送逻辑
    ├── utils/
    │   ├── storage.js             # localStorage：token / 用户 / 已上传简历缓存
    │   ├── audio.js               # base64 语音播放 + WAV 录音编码
    │   └── format.js              # 时间格式化、分数颜色、uuid
    ├── components/
    │   ├── AppHeader.vue          # 顶部导航（含退出登录）
    │   ├── ChatBubble.vue         # 聊天气泡（AI 消息带播放语音）
    │   ├── TypingBubble.vue       # AI 思考中动画
    │   └── VoiceRecorder.vue      # 录音 / 试听 / 重录 / 发送
    ├── styles/
    │   └── index.css              # 全局浅色主题、卡片、滚动条
    └── views/
        ├── LoginView.vue          # 登录 / 注册
        ├── InterviewView.vue      # ★ 面试主页
        └── ReportView.vue         # ★ 报告页（轮询 + SSE）
```

---

## 三、后端接口清单（从前端视角）

全部接口返回统一结构：`{ code, message, data }`，`code === 200` 才是成功。

| 功能 | 方法 | URL | 参数 | 返回 data |
| --- | --- | --- | --- | --- |
| 注册 | POST | `/user/register` | body `{username, password, nickname}` | `null` |
| 登录 | POST | `/user/login` | body `{username, password}` | **token 字符串** |
| 上传简历 | POST | `/resume/upload` | form-data `file`（仅 PDF） | `{resumeId, fileName, filePath}` |
| 开始面试 | POST | `/interview/start` | query `resumeId`、`provider`、`requestId` | `{sessionId, message}` |
| 回答 | POST | `/interview/reply` | body `{sessionId, answer}` | `{sessionId, message, aiAudio}` |
| 语音面试 | POST | `/interview/voice` | form-data `sessionId`、`audio` | `{userText, aiText, aiAudio}` |
| 结束面试 | POST | `/interview/finish` | query `sessionId` | `{sessionId, summary}`（异步评分） |
| 查报告 | GET | `/interview/report` | query `sessionId` | `{sessionId, totalScore, summary, strengths[], weaknesses[], suggestion, createdTime}` |
| 面试列表 | GET | `/interview/list` | — | `[{sessionId, status, totalScore, createdTime}]` |
| 报告完成推送 | GET | `/interview/sse` | query `sessionId` | SSE，生成完推一条 `DONE` |

**鉴权规则**（读自 `SaTokenConfig`）：

- 拦截 `/interview/**`、`/resume/**`、`/rag/**`，放行 `/user/**` 和 `/interview/sse`；
- 请求头名称是 **`satoken`**（`application.yml` 里 `sa-token.token-name`），不是 `Authorization`；
- `provider` 可选值：`deepseek`（默认）、`ollama`。

**AI 语音字段**：`aiAudio` 是 base64 编码的 **mp3**（后端走 DashScope cosyvoice，`format=mp3`），前端拼成 `data:audio/mp3;base64,xxx` 直接播放。

---

## 四、核心实现说明

### 1. 认证（`src/api/request.js`）

```js
// 请求拦截器
config.headers['satoken'] = localStorage.getItem('ai_interviewer_token')
```

响应拦截器做了三件事：

1. `code !== 200` → `ElMessage.error(message)` 并 reject；
2. `code === 200` → **自动解包**，业务代码里直接拿 `data`，不用写 `.data.data`；
3. 登录失效 → 清 token + 跳 `/login?redirect=原地址`。

> ⚠️ 有个坑：Sa-Token 未登录时抛的 `NotLoginException` 会被后端 `GlobalExceptionHandler` 的
> `@ExceptionHandler(RuntimeException.class)` 捕获，所以返回的是 **`code: 500` + HTTP 200**，
> 而不是 401。因此前端除了判断 HTTP 401/403，还额外用关键词（token / 未登录 / 顶下线 …）
> 兜底识别登录失效，见 `isAuthFailed()`。

登录后没有 `userinfo` 接口（`UserController` 只有注册和登录），所以用户名/昵称在登录时由前端写入 localStorage，顶栏直接读本地。

### 2. 报告「生成中」→ 轮询（`src/views/ReportView.vue`）

后端 `finish` 只是把会话置为 `FINISHED` 并丢一条消息到 RabbitMQ，**立即返回**「报告生成中，请稍后查询」；报告由消费者异步生成后落库。

所以报告页的逻辑是：

1. 请求 `/interview/report`，`totalScore` 为 `null` 就是还没生成好；
2. 每 **3 秒**轮询一次，最多 **40 次**（约 2 分钟），页面上展示已等待秒数和轮询进度条；
3. 同时挂一条 **SSE**（`/interview/sse`），收到 `DONE` 立刻刷新，不用等下一个轮询周期；SSE 挂了也不影响，轮询兜底；
4. 超过 40 次仍未拿到 → 提示「生成时间较长」，给出「再查一次」按钮；
5. 组件卸载时清理定时器和 EventSource。

另外，如果这场面试状态还是 `ONGOING`（比如从历史记录点进来），说明「还没结束」而不是「生成中」，页面会提示并提供「结束面试并生成报告」按钮。

### 3. 聊天时 AI 消息自动转语音

- 文字面试：`/interview/reply` 的返回里直接带 `aiAudio`，拿到就 `playBase64Audio()` 播放；
- 语音面试：`/interview/voice` 一次性返回 `userText`（识别结果）、`aiText`、`aiAudio`；
- 气泡下方有「播放语音」按钮，聊天头部有「重新朗读最后一条」和「停止播放」；
- 顶栏配置区有「AI 语音自动朗读」开关（`store.autoPlay`）。

**关于浏览器自动播放策略**：播放动作都由用户点击（发送/录音）触发，所以不会被浏览器拦截；万一被拦截，`playBase64Audio` 内部有 catch，只在控制台 warn，不会报错打断流程。

### 4. 语音录音：为什么自己编码 WAV

后端 `VoiceServiceImpl.speechToText` 是：

```java
File tempFile = File.createTempFile("asr-", ".wav");
audio.transferTo(tempFile);
new Recognition().call(param, tempFile);
```

它把上传内容**固定写成 `.wav`** 再交给 DashScope 识别，所以前端上传的必须是真正的 wav（浏览器 `MediaRecorder` 通常只能录 webm/opus，直接传大概率识别失败）。

因此 `src/utils/audio.js` 用 **Web Audio API** 自己采集：`getUserMedia` → `ScriptProcessorNode` 拿 PCM → 降采样到 **16k 单声道** → 手动写 44 字节 WAV 头 → 得到标准 `audio/wav` Blob，上传时文件名固定 `answer.wav`。兼容性最好，不需要任何第三方库。

> 顺带一提：`ScriptProcessorNode` 已被标记废弃但所有主流浏览器仍支持；如果以后要更规范，可换成 `AudioWorklet`。

### 5. 简历列表（前端缓存）

后端没有「查询我的简历列表」接口，`ResumeServiceImpl` 只在 MySQL 落库并返回 `resumeId`。所以已上传的简历由前端缓存在 `localStorage`（按用户名隔离，最多 20 条、按 `resumeId` 去重），面试页可以直接复用历史 `resumeId` 开始新面试。

### 6. 开始面试的幂等

`/interview/start` 支持可选的 `requestId`：后端会用 Redisson 加锁 + Redis 记 `requestId → sessionId`（1 小时），重复请求返回同一场面试。前端每次点「开始面试」用 `crypto.randomUUID()` 生成 `requestId`，**双击或失败重试都不会创建两场面试**。

---

## 五、页面说明

### 登录 / 注册（`/login`）

左侧介绍 + 右侧 `el-tabs`（登录 / 注册），表单校验：用户名 3-20 位、密码 ≥ 6 位、注册时二次确认密码。注册成功后自动切到登录页并回填用户名。

> 后端 `@RateLimit(key="login", window=60, limit=5)`：**1 分钟最多 5 次登录**，超了会返回错误提示。

### 面试主页（`/interview`）

三栏式左侧 + 右侧聊天区：

- **左栏**：① 上传简历（拖拽 / 点击，显示上传进度，可切换已上传的简历）② 面试配置（选模型 deepseek/ollama、文字/语音模式、AI 自动朗读开关、开始/结束按钮）③ 历史面试记录（状态、时间、分数，点击直接进报告页）；
- **右栏**：聊天对话。AI 气泡白底带语音播放按钮，用户气泡浅蓝；顶部显示会话 id（点击复制）、状态标签、「重新朗读」「停止播放」；底部文字模式是 textarea（**Enter 发送，Shift + Enter 换行**），语音模式是录音控件。

交互约束：未选简历不能开始；面试进行中不能改模型/换简历；未开始面试输入框禁用；`/interview/reply` 有 10 次/分钟限流，超了会弹后端返回的错误信息。

### 报告页（`/report/:sessionId`）

顶部信息条（会话 id / 状态 / 生成时间 / 刷新）→ 生成中用 `el-result` + 进度条展示轮询情况 → 报告出来后：左侧总分仪表盘（按分数变色：≥85 绿、≥60 橙、<60 红）、右侧总体评价 + 优点 / 不足列表 + 改进建议，底部「再面一场」「查看历史面试记录」。

---

## 六、已知限制 / 待办

1. `strengths`、`weaknesses` 在数据库里是逗号拼接的字符串，后端按 `,` split 成数组返回；如果 AI 输出里本身带逗号，会被拆成多条 —— 属于后端数据格式问题，前端只做展示。
2. 知识库接口（`/rag/**`：`load` / `search` / `ask` / `retrieve`）后端已实现但没有做页面，前端只在 `api/interview.js` 里预留了 `ragAsk()`；如果需要，可以加一个「知识库调试」页。
3. SSE 仅用于加速报告刷新，即使后端未启动 RabbitMQ / SSE 不可用，轮询也能让报告正常显示。
4. 面试中的消息列表没有做历史回填接口（后端只把对话存在 Redis，没有查询接口），所以**刷新页面后当前会话的对话气泡会丢失**，但会话 id 和历史记录仍在，可以继续通过报告页查看结果。
