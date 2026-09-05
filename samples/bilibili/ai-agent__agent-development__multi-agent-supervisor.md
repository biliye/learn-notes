---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Multi-Agent 分工协作：Supervisor 与专家团队
slug: multi-agent-supervisor
tags: [Multi-Agent, Supervisor, Router, Agent as Tool, LangGraph, 上下文隔离]
summary: 单体 Agent 是会被巨型 system prompt 撑爆的"全干工程师"。Multi-Agent 把任务分给专家：Supervisor 只管路由，每个专家配专属 prompt，实现上下文的物理隔离。详解 Agent as Tool 与 LangGraph Node 两种实现，以及 Routing、Swarm、Orchestration 的层级关系。
order: 15
spec_version: v2
---

# Multi-Agent 分工协作：Supervisor 与专家团队

> 整理自 B 站视频《【2026/Agent】一期讲透》第 9 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），层级概念补充自 UP 的 notion 笔记。前置：系列第四篇《Agent Loop 三范式》。

## 痛点：全干工程师的大脑串线

前面所有范式都是单体 Agent：一个"全干工程师"顶着几千字的巨型 system prompt，被要求同时会前端、后端、文档、设计、管理。给真人这样安排都会脑子宕机效率骤降，何况模型——它会把前端后端的知识混在一起，长任务里左脑互搏、大脑串线。UP 主 notion 笔记的原话是："单 Agent 的能力是有极限的！"所以采用多个 Agent 互相合作，才能解决更复杂、更宏观、需要分工的问题。

## 解法：路由器加专家，物理隔离上下文

具体做法是把 Planner 升级为 router（路由器，也叫 Supervisor）：它直接面向用户、自己没有工具，只负责判断每个任务该派给哪个专家。关键在于每个专家 Agent 配一段约千字的专属 prompt，而不是一张万能大表——专家执行任务时注意力百分之百专注在自己的职责上，前端专家完全不需要知道后端知识。

这实现了物理级别的隔离，防止了上下文互相污染，某种程度上就是一种 Context Engineering。输出质量、准确率、幻觉控制能力都会显著提升。后续无论 OpenClaw 还是 Claude Code，底层都是多个 sub-agent 在协作。

notion 笔记里还点出了这一层的复杂度坐标：Routing 解决"任务派给谁"，Swarm 解决"一群 Agent 如何互相交接协作"，复杂度再往上就是工程化问题——当内置的编排满足不了需求时，就需要用 LangGraph 自行编排整个 Agent 的工作流和信息流（Orchestration / Workflow）。Multi-Agent 不是终点，而是通往工程化的门槛。

## 两种实现方式

![Multi-Agent 两种实现](/uploads/2026/09/fed632c7f631d8c6.png)

### 方式一：Agent as Tool（推荐）

观察 Supervisor"派活—收结果—再派活"的循环，它和 ReAct 里"模型决定调哪个工具—收观察—再决定"的结构如出一辙，那么干脆把每个专家 Agent 包装成一个工具：@tool 加函数描述（"执行文件整理任务"/"生成代码"），交给 create_agent 创建的 Supervisor 绑定即可。

UP 主更推荐这种方式：思路优雅、实现简洁，还能工具套工具——把多个 Supervisor 再包装成工具挂到更上层的 Supervisor 上做二次路由，像树状结构一样不断扩展。

### 方式二：LangGraph Node

supervisor、file_agent、code_agent 各作为一个节点，state 里除了 task 还要存 next_agent 路由字段——prompt 里要求 Supervisor 只输出一个词：file_agent / code_agent / finish——以及两个专家的执行报告。条件边根据这个词决定流向：专家从 state 里取出 task 执行，完成后汇报给 supervisor，直到 supervisor 输出 finish 走向 END。

```python
def route_next(state) -> str:
    return {"file_agent": "file_agent",
            "code_agent": "code_agent"}.get(state["next_agent"], END)
```

两种方式怎么选：要简洁优雅、快速搭出层级就选 Agent as Tool；要深度定制——比如在 state 里做上下文工程处理、精细控制每个字段——就用 LangGraph Node，它的 state 可编排性更强。

## 小结与串场

Multi-Agent 通过分工与隔离提升了单任务质量，但所有范式共享的软肋依然在：上下文窗口有限，性能随窗口增大而下降，模型会遗忘、会漂移。如何管住这块"有限的大脑"，是全系列最重要的工程课题——见系列第六篇《Context Engineering》。
