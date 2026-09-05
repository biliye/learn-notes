---
category: AI Agent
category_slug: ai-agent
topic: AI 概念底层拆解
topic_slug: concept-demystification
title: AI 概念图解：一张图看懂 Skill / MCP / RAG / Agent 智能体
slug: ai-agent-concepts-visual-guide
tags: [Agent, MCP, RAG, Skill, Function Calling, 图解, 可视化, 底层原理]
summary: 用四张图把 Agent 智能体的"喂上下文 + 调工具"主线画清楚：Agent 全景架构、概念一步步的堆叠顺序、从刚到柔的选型谱系，以及 Function Calling / MCP / SKILL 这三个常被搞混概念的区别。
order: 20
spec_version: v2
---

# AI 概念图解：一张图看懂 Skill / MCP / RAG / Agent 智能体

> 内容整理自 B 站视频《【闪客】一口气拆穿Skill/MCP/RAG/Agent/OpenClaw底层逻辑》（UP：飞天闪客，[原视频](https://www.bilibili.com/video/BV1ojfDBSEPv/)）。这四张图把"喂上下文 + 调工具"这条主线可视化，配合文字笔记一起看效果更好。

## 一、Agent 全景架构

![Agent 全景架构](/uploads/2026/09/e10812c5db25e951.png)

这张图是全文的主心骨：老板用自然语言把需求交给智能体，智能体通过 Function Calling（按 JSON 固定格式）跟只会文字接龙的大模型约定"要调哪个工具"，大模型自己不做事，智能体就是中间的传话筒。右侧的 MCP 约定智能体与外部工具服务（搜索、文件读取、脚本执行、PDF 转换）怎么连接，左下角的 SKILL 则提供"这件事怎么做"的说明书和脚本，按需加载、上下文隔离。

## 二、概念是怎么一步步堆出来的

![概念堆叠顺序](/uploads/2026/09/65b656a1c27dd43f.png)

这张图按顺序列出概念演进的每一步：大模型只会文字接龙；加一问一答变成对话；把背景信息叫 Prompt，把背景信息与最终指示合称 Context；把历史对话重放回上下文、伪装成多人对话，就是 Memory；把外部资料（含向量化检索）塞回上下文，就是 RAG。再往上加一段角色提示词和会调工具的中间层，就得到 Agent；随后才有约定格式的 Function Calling、连外部服务的 MCP、以及 prompt 加载器 SKILL 和低代码工作流。

## 三、选型谱系：从刚到柔

![刚柔谱系](/uploads/2026/09/62d040497b98549f.png)

从稳定到变化有一条谱系：LangChain 是纯编程硬编码，最稳定但最不灵活；Workflow 是低代码拖拽；SKILL 是写好的说明文档加脚本，既留灵活空间又不至于不可控；纯 Agent 最灵活，甚至能自己生成脚本来跑，但代价是可能失控。作者的建议是：把确定的分流逻辑交给程序，把模糊的分流逻辑交给大模型。

## 四、三个常被搞混的概念

![Function Calling 与 MCP 与 SKILL](/uploads/2026/09/99d1765ed82d3ef0.png)

Function Calling、MCP、SKILL 常被搞混，但它们不在一个维度。Function Calling 是大模型与智能体之间的约定，目的是让大模型按 JSON 固定格式描述要调哪个工具；MCP 是智能体与外部工具或服务之间的约定，像接口文档（tools/list、tools/call）；SKILL 用 SKILL.md 说明书加脚本告诉智能体怎么做事，本质是个 prompt 加载器。

## 配合文字笔记一起读

这四张图是配套理解。想要更完整的讲解、作者的原话与评论区争论，请读同分类下的《拆穿 Skill/MCP/RAG/Agent/OpenClaw 的底层逻辑》文字笔记。
