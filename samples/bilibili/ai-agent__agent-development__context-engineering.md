---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Context Engineering：管住有限的大脑
slug: context-engineering
tags: [Context Engineering, 上下文工程, 压缩, RAG, CLAUDE.md, Multi-Agent]
summary: 上下文管理决定 Agent 功能的下限。从 Shopify CEO 与 Karpathy 的推文起源讲起，梳理遗忘漂移、窗口超载、输出歧义、静态死板四大痛点，Write/Select/Compress/Isolate 四大思想及其具体手段（CLAUDE.md、auto-compact、RAG 选工具、Swarm 隔离等），最后用一个六阶段任务串起八大方法。
order: 16
spec_version: v2
---

# Context Engineering：管住有限的大脑

> 整理自 B 站视频《【2026/Agent】一期讲透》第 10 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），四大思想的具体手段大量补充自 UP 的 notion 笔记，落地参数来自 MokioClaw 项目源码（Wood-Q/MokioAgent）。这是全系列的两个工程核心之一。

## 起源：一条引爆关注的推文

"上下文工程（Context Engineering）"的术语概念最早可以追溯到 2025 年 6 月 19 日：Shopify CEO Tobi Lutke 在 X 上发推，认为该术语比"提示词工程（Prompt Engineering）"更贴切地描述了向大语言模型提供全面背景信息的技能。随后 Andrej Karpathy 立即转发并附上"+1"，表示自己也更喜欢"上下文工程"这个说法——这条转发截至 2025 年 7 月中旬已被阅读 210 万次，彻底引爆了业界关注。

在此之前，大家只盯着输入端的 prompt 修饰，但 Agent 执行链路里有大量中间输入输出——工具返回、模型思考、调用请求——放任不管只调 prompt 是不对的。上下文工程被定义为"对上下文的管理"，它决定了 Agent 功能的下限。

## 四大痛点

传统做法会积累出四大痛点。一是遗忘漂移：上下文拉长后模型忘了前面的要求，即常说的幻觉。二是窗口超载：窗口是有限的，超过 200k token 这类上限会被直接截断甚至报错。三是输出歧义：要求输出 JSON，模型因为前两个问题输出了数组，下游解析直接崩溃。四是静态死板：在巨大 system prompt 里把流程排死，中间一步出错模型就摆烂。根本原因有两条：上下文窗口有限，且性能随窗口增大而显著下降。

## 四大思想与具体手段

### Write：写到窗口之外

把当前会话的关键内容即时保存到上下文窗口之外，以便后续使用。具体方式有三种：一是项目规则文件化——CLAUDE.md 存全局规则与工程约束，PLANNING.md 存架构、模块边界与设计意图，TASK.md 或 TODO.md 存当前任务、状态与下一步；二是记事板——把中间结果或行动计划写入文件或内存状态，避免上下文截断；三是长期记忆——利用 LLM 自动生成整理回忆，ChatGPT、Cursor、Windsurf 都支持此功能。

### Select：按需拉取

真正需要使用时，才从外部存储把最相关的内容拉进窗口，避免无意义堆积。记事板方面，开发者可以控制哪些字段暴露给 LLM、提供哪些读取工具。长期记忆方面，小规模记忆可以全量拉入（如 CLAUDE.md 或静态规则文件），大规模记忆则通过 embeddings 或知识图做语义检索，挑选合适的回忆作为 few-shot 示例或事实记忆。工具多且描述相似时，用 RAG 挑选最相关的工具能显著提升选择准确率；知识检索同理，可配合 RAG、AST 分块、知识图、重排等技术提高相关性与精度。

### Compress：浓缩与剪裁

对已经进入窗口的内容浓缩或裁剪，只保留对当前和未来任务最必要的内容。两种方式：Context Summarization（上下文总结）——大量交互后自动总结上下文，大幅减少 token 消耗又保留关键信息，Claude Code 的 auto-compact 在达到窗口 95% 时会自动总结整个对话轨迹；Context Trimming（上下文剪裁）——用规则剪掉老旧消息，或异步调用另一个 LLM 来剪裁不必要内容。

### Isolate：隔离聚焦

把不同任务或内容逻辑上分开管理，让窗口聚焦当前子任务。三种方式：多代理（Multi-agent）——把复杂任务拆给拥有独立上下文窗口的专属子 Agent，OpenAI Swarm 和 Anthropic 的多代理实践都发现在并行探索时，隔离上下文比单体 Agent 更高效；沙箱环境——如 HuggingFace CodeAgent 把工具调用运行在隔离 Sandbox 中，不把大体量内容直接送入 LLM，而是抽取重要结果后才提供上下文；状态对象隔离——在 Runtime State 里定义 messages、tool_outputs 等多个字段，只把需要给 LLM 的部分暴露出去，其余隔离保存。

## 六阶段实战：八大方法落地

示例任务是"新建一个获取天气数据的 Python 模块 + 编写单元测试 + 生成 markdown 格式的 API 文档"，按六个阶段对照传统做法与上下文工程做法。

启动阶段：传统做法载入几千字永远不变的静态 system prompt，几十分钟后窗口堆满噪音，模型漂移忘了约束。工程做法是动态拼接 prompt：把要求拆成模块——base 模块（语言与测试框架约束，各阶段常驻）、code 模块（编码风格，编码阶段拼上）、test 模块（测试要求，测试阶段拼上）——既动态加载，又让模型在任何阶段都能看到最初要求。

编码阶段：传统做法让模型用自然语言写计划，几万字之后照样漂移。工程做法是强制模型按 JSON 输出 to-do，转成持久的任务看板，每次调用模型前都把 to-do 喂到最顶层——模型永远记得进度，完成一项打个勾专注下一项。同时做动态选择工具：监测到模型进入 src 目录写代码时，只暴露三个代码工具并附上目录规则，既省上下文又防止乱调 web search 搅乱工程。

报错与压缩阶段：报错的 stack trace 一上来几百上千行，原封不动塞进历史会瞬间撑爆窗口。工程做法是让模型把错误猜想单独记到 notepad 文件持久化，遇到同类报错再把摘要喂回来。执行几十轮上下文告急时，压缩机制登场：设置钩子，上下文用到一半就触发压缩函数——删掉陈旧日志、用小模型把整个上下文总结，压回十分之一，如此往复实现无限续航。

切换与收尾阶段：写完代码突然让模型写文档，它可能还沉浸在代码角色里。工程做法是作用域切换：监测到进入 docs 目录就把 code 模块抽出来换上文档规则，并在看板上勾掉代码部分。完成阶段：不重开窗口就继续下一个任务，上个任务的几十万 token 垃圾会继续污染会话。工程做法是输出标准完工报告写入长期数据库，然后把 memory 彻底清空——不重启 Agent 就能干净地开下一个任务。

PPT 里把痛点与解法整理成了一张对照表，复习按它背：遗忘漂移（任务拉长到第 10 轮忘了最初目标）→ Notepad/Todo 外部记忆、分层记忆与工程约束；窗口超载（垃圾信息越多推理越差）→ 压缩机制（Compaction）、动态选择工具；静态死板（几千字 System Prompt 把 role 和能力定死）→ 动态 Prompt 组装、路径作用域规则；输出歧义（随机生成导致格式混乱）→ 结构化输出（Schema）。

### MokioClaw 里的真实实现

这套思想在 MokioClaw 项目源码里已经落地，读代码能看到几个工程细节。分层记忆由运行时统一装配：RULES_LAYER 存固定规则（只能在 workspace 内工作、TODO.md 是计划状态、NOTEPAD.md 是持久笔记、HISTORY_SUMMARY.md 是压缩历史），工作记忆字段各有字符上限（如 research_notes 1600 字、notepad 1800 字、history_summary 2200 字），超限即截断，从源头防止单字段撑爆上下文。

压缩机制的触发参数也可配：上下文 token 上限默认 400000，用环境变量 MOKIO_CONTEXT_TOKEN_LIMIT 调整，monitor 节点每轮估算 token，达到上限就走压缩。整个编排图是 planner → context_monitor →（条件路由）context_compressor 或 verifier 或 planner，verifier 完了还要回 monitor 复查——压缩不是一次性的，而是嵌在主循环里的常驻环节。入口图还配了 intent_router：普通聊天走 chat_responder 直接回答，正经任务才进 planner 重图，聊天上下文不污染任务图。

一句话总结本篇核心：筛选出有效的 context 保留在上下文里，尽可能剔除无效的 context——这是一切 Agent 工程化的基础中的基础。而即使上下文管好了，系统仍可能被一条危险命令毁掉，最后一道防线见系列第七篇《Harness Engineering》。
