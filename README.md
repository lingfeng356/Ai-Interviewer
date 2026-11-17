# AI 面试官 Agent

一个「AI 模拟面试 + 自动评估」的全栈项目：上传 PDF 简历，AI 面试官按四个环节提问，候选人文字或语音作答，结束后异步生成评分报告。

面试官不是"套壳聊天"：它在每一轮里可以**自己决定**是否检索简历、查技术知识库、记录对候选人的观察，然后才给出下一个问题 —— 这些工具调用过程在页面上可以逐条展开查看。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 后端 | Spring Boot 3.2.5 / JDK 21 / Spring AI 1.0 / MyBatis-Plus / Sa-Token / Redisson |
| 大模型 | DeepSeek（对话 + 工具调用）、Ollama（本地模型，可关闭） |
| 存储 | MySQL（业务）、Redis（对话历史 / 锁 / 笔记 / 进度）、PostgreSQL + pgvector（知识库） |
| 异步 | RabbitMQ（评分任务） |
| 语音 | 阿里云 DashScope（paraformer ASR + cosyvoice TTS） |
| 前端 | Vue 3.5 + Vite 5 + Element Plus + Pinia + Vue Router（`web/` 子目录） |

## 目录

```
.
├── src/main/java/com/lingfeng/interviewer/
│   ├── agent/          # 面试官 Agent：编排、prompt、工具、轨迹记录
│   ├── controller/     # HTTP 入口
│   ├── service/        # 业务逻辑（InterviewServiceImpl 是核心）
│   ├── rabbitMQ/       # 评分任务的生产者 / 消费者
│   ├── sse/            # 报告进度推送
│   ├── config/         # 模型 Bean、LLM 注册中心、MQ、向量库
│   └── common/         # 统一返回、异常、限流、面试阶段枚举
├── web/                # 前端（Vue 3 + Vite）
├── deploy/             # Docker Compose / Nginx / systemd / 建表脚本
├── docs/               # 设计文档
└── src/main/resources/ # 配置 + 限流 Lua 脚本
```

## 面试流程

1. 注册 / 登录（Sa-Token，BCrypt 密码）
2. 上传 PDF 简历 → Tika 抽文本 → 大模型抽取结构化画像（技能 / 项目 / 学历）
3. 开始面试，AI 生成开场白
4. 逐轮问答，四个环节由「已回答轮数」推进：自我介绍 → 项目经历 → 技术基础 → 反问
5. 结束面试 → 投递 RabbitMQ → 消费者汇总对话 + 面试笔记 → 结构化评分 → 落库
6. 报告页通过 SSE + 轮询拿结果

## Agent 做了什么

每一轮回答，模型拿到 3 个工具：

| 工具 | 作用 |
| --- | --- |
| `searchResume(keyword)` | 在简历原文里按关键词检索片段，避免每轮都把几千字塞进 prompt |
| `searchKnowledge(question)` | 查技术知识库，用来判断候选人答得对不对 |
| `saveMemo(type, note)` | 记录观察（优点 / 不足 / 疑点），注入后续轮次，并在评分时交给评估模型 |

护栏：

- 单轮工具调用上限 5 次、总时长 30 秒，超限后工具返回「请直接提问」而不是抛异常；
- 工具内部**不抛异常**，失败返回可读文本，让模型自己换策略；
- Agent 执行失败自动降级为「经典模式」（固定轮次提问），面试不会中断；
- 开场时可选 `mode=agent|classic`，随时可切回改造前的行为。

> 说明：这里记录的是**可观测的工具调用链**（工具名、参数、结果摘要、耗时），不是模型的隐式思维链 —— DeepSeek 的 chat 模型不返回 CoT。

## 本地启动

```bash
# 1. 中间件（MySQL / PostgreSQL+pgvector / Redis / RabbitMQ）
cd deploy && docker compose up -d

# 2. 后端（8081）
./mvnw spring-boot:run        # Windows: mvnw.cmd spring-boot:run

# 3. 前端（5173）
cd web && npm install && npm run dev
```

前端通过 Vite 代理把 `/api` 转发到 `http://localhost:8081`，后端不需要配 CORS。

配置：

- 本地开发把密钥写进 `src/main/resources/application-local.yml`（已被 `.gitignore` 忽略）；
- 生产用环境变量注入（见 `deploy/app.env.example` 与 `deploy/application-prod.yml`）。

## 测试

```bash
./mvnw test          # 目前覆盖 Agent 的 prompt / 工具预算 / 简历检索
```

## 文档

- [Agent 化改造方案](docs/AGENT_DESIGN.md)：设计取舍、护栏、迭代计划、面试话术
