---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Agent 全景与演进地图：系列导读
slug: agent-evolution-map
tags: [Agent, Function Calling, Context Engineering, Harness Engineering, OpenClaw, 演进史]
summary: Agent 系列笔记的导读篇：沿"Function Calling、Manus、Context Engineering、Harness Engineering、OpenClaw"五年演进时间线，说明每个概念解决什么痛点；给出 MokioClaw 项目三层定位与全系列八篇笔记的阅读地图，并附字幕校正清单。
order: 11
spec_version: v2
---

# Agent 全景与演进地图：系列导读

> 本篇是《一期讲透 Agent》系列笔记的导读，整理自 B 站视频《【2026/Agent】一期讲透！理论+代码从ToolCall到Harness、Claw》（UP：木乔_Mokio，约 171 分钟 / 20 分P，[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），并结合 UP 的 notion 笔记《从ToolCall开始组装自己的Claw》补充。全系列共八篇，按 Agent 技术演进顺序拆分，本篇给地图与复习路线。

## 学完本系列你能获得什么

- 沿五年演进时间线理解 Agent 每个概念诞生的动机：ToolCall 让模型触及现实，Agent Loop 让模型学会做事，Context Engineering 让模型记得住事，Harness Engineering 让系统驾驭得住，Claw 交互层让产品卖得出去。
- 用最小代码复现 ToolCall、ReAct、Reflection、Plan&Execute、Multi-Agent 五个台阶，分清 LangChain 组件与 LangGraph 编排（node、edge、state、conditional edge）各自的角色。
- 掌握两个工程核心：Context Engineering 的四大思想与具体落地手段，Harness Engineering 的系统级保障方法，并能对照传统工程概念记忆。
- 看懂一个 Claude Code 式项目如何从最小 ReAct 循环六步进化到带审批、检查点、链路追踪和 TUI 界面的完整产品。

## 五年演进时间线

![Agent 技术演进时间线](/uploads/2026/09/a7e30730888e9798.png)

2023 年 OpenAI 发布 Function Calling，模型第一次可以调用工具、接触外界，打破了"缸中之脑"的禁锢。2025 年 3 月 Manus 正式发布，标志着 AI Agent 元年，Agent 概念爆火走入大众视野。2025 年 6 月 Shopify CEO 提出上下文工程，Karpathy 转发附议，业界开始系统性地关注上下文处理。2026 年 2 月 Harness Engineering 被提出，把工程视野从上下文扩展到围绕 Agent 的整系统约束与反馈。同年年初 OpenClaw 爆火，成为史上增长最快的开源项目之一，标志着 Agent 从概念与技术走向成熟产品。

这条主线可以背成一句话：让模型触及现实（ToolCall）→ 学会做事（Agent Loop）→ 记得住事（Context Engineering）→ 驾驭得住（Harness Engineering）→ 卖得出去（Claw 交互层）。每引入一个新概念，都是为了解决上一层范式的致命短板，这也是全系列八篇笔记的排序依据。

## MokioClaw 项目定位

UP 主在 notion 里给同步构建的项目 MokioClaw 下了明确定位：一个终端优先、代码编辑优先，但可扩展到日常 CLI 任务和轻量生活服务的 Mini Claw。它的三层定位分别是：核心层负责代码编辑与 repo 操作；通用层负责文件处理、命令执行、报告生成；扩展层再接天气、飞书、日常 API、skills、MCP 与 HITL（human in the loop，人在回路审批）。这个三层结构就是全系列工程方法的最终落点，项目篇会逐层搭建。

## 课程结构与前置知识

课程分为四部分：理论讲解（白板推导 ToolCall、ReAct、Context Engineering、Harness 等概念）、代码实现（用最小代码把概念落地）、同步项目（自底向上做一个自己的 Claude Code / OpenClaw 式产品）、学习资料推荐。前置知识包括 LangChain 基础概念、Python 基础和 UV 等现代工具链；完全零基础建议先看概念扫盲类视频再回来。

## 系列阅读地图

本系列八篇笔记建议按顺序读：第一篇就是本篇导读，给出全景与时间线。第二篇《LangChain 与 LangGraph 地基》补齐框架概念。第三篇《ToolCall：从文本触及现实》讲 Agent 的起点。第四篇《Agent Loop 三范式》覆盖 ReAct、Reflection、Plan&Execute 三个循环架构。第五篇《Multi-Agent 分工协作》讲 Supervisor 与专家分工。第六篇《Context Engineering》与第七篇《Harness Engineering》是全系列的两个工程核心，建议精读。第八篇《Claw 交互层与 MokioClaw 项目实战》把工程收束成产品。每篇独立可读，也可作为复习时的分模块速查卡。

## 复习自测

读完全系列后可以自问自答这几个问题检验掌握程度。一，Function Calling 的本质是什么？为什么 OpenAI 要在训练层面强制 JSON 输出？二，ReAct 循环的终止条件怎么写？它把模型变成了什么？三，Reflection 和 Plan&Execute 各在图上加了一个什么节点，state 分别多了什么？四，Multi-Agent 两种实现的切换依据是什么？五，Context Engineering 四大思想对应哪四大痛点，每种思想有哪些具体手段？六，Harness Engineering 的四类保障分别对应传统工程里的哪些做法，它与 Context Engineering 的分工边界是什么？七，项目篇六步进化中，每一步在 loop、tool、state、workspace 四个维度上各改了什么？

## 来源与局限

全系列笔记依据 20 个分P的网页 AI 字幕（共 4342 行、约 6 万字）整理，并补充了 UP 的 notion 笔记中已公开的正文。字幕为 B 站 AI 识别，高频错听已按上下文与行业通用名校正：long unchain/狼群→LangChain，long graph/long craft→LangGraph，脱口/脱cos/TOCOS→tool call（tool_calls 字段），防圈口令/方圈coin→function calling，minus/MANUS斯→Manus，COFFY→Karpathy，cloud点M1点MD→CLAUDE.md，A镜/A镜腾→Agent，MONTAGENT/猫贴检测→Multi-Agent，contact/connect engineering→Context Engineering，HARNE3经虐/harness军略→Harness Engineering，憨豆/涵道F→handoff，TABI/TAVII→Tavily，试体外界面→TUI 界面。画面白板与代码细节以字幕转述为准，PPT 与完整代码以 UP 的 GitHub 仓库 Wood-Q/MokioAgent（theory 分支）和 notion 笔记为准。
