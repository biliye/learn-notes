---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: LangChain 与 LangGraph 地基：积木盒与编排台
slug: langchain-langgraph-basics
tags: [LangChain, LangGraph, Message, State, 编排, Agent]
summary: 补齐 Agent 开发的框架地基：用一张概念表速通 LangChain 十一个组件（Model、Prompt、Message、Tool、Chain、Retriever、Memory、Agent 等），理解 message 三段结构与 LangGraph 的 node、edge、state、compile、invoke，弄清"积木盒"与"编排台"的分工。
order: 12
spec_version: v2
---

# LangChain 与 LangGraph 地基：积木盒与编排台

> 整理自 B 站视频《【2026/Agent】一期讲透》第 2 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），概念表补充自 UP 的 notion 笔记《从ToolCall开始组装自己的Claw》。前置建议：先浏览一遍 LangChain 官方文档的 Quickstart，本文只做速通。

## 一切的起点：一条第一性原理

理解 Agent 的所有后续内容，只需记住一句话：一切的后续，本质都是对模型输入输出的文本进行操作。剥掉所有包装，大模型就是一个输入字符串、输出字符串的 API。提示词、工具调用、上下文管理、审批拦截，全都是在这对输入输出上做文章。这条原理来自视频开篇，也是 UP 主 notion 笔记里放在"Agent 科普内容"下的第一行。

## LangChain 组件速通表

LangChain 是做 LLM 应用的组件工具箱，帮你把模型、提示词、工具、消息等拼起来——它不是 Agent 本身，更像搭 Agent 的积木盒。下表整理自 UP 的 notion 笔记，每个概念配一句好记的比喻。

| 组件 | 简单解释 | 一句话理解 |
| --- | --- | --- |
| LangChain | LLM 应用组件工具箱 | 搭 Agent 的积木盒 |
| Model | 接入的大模型本体（OpenAI、Anthropic、Ollama 等） | 负责思考生成，不会自动完成工程流程 |
| Prompt | 指令模板，决定模型怎么理解任务、按什么格式输出 | 给模型写操作说明书 |
| Message | 统一管理 system、user、assistant 消息格式 | 模型看到的上下文载体 |
| Tool | 可调用函数，有明确输入输出，模型决定何时调用 | 模型伸向现实世界的手 |
| Tool Calling | 让模型输出"要调哪个工具、传什么参数" | 从聊天走向执行 |
| Chain | 把多个步骤串成固定流水线 | 提示词 → 模型 → 解析输出 |
| Output Parser | 把文本输出整理成结构化结果 | 把胡言乱语变成程序能读的格式 |
| Retriever | 专门帮模型找资料，常见于 RAG | 负责找信息，不负责下判断 |
| Memory / Chat History | 多轮对话保留必要上下文 | 没有历史，模型每轮都像失忆 |
| Agent | 能基于上下文决定要不要调工具、怎么推进的执行体 | 模型开始自己决定下一步 |

其中 message 结构值得单独强调：LangChain 用统一的格式管理上下文，每条消息包含 role、content、metadata 三个字段。请求以 user 或 system 角色写入，模型返回以 assistant 角色追加，多轮对话就是在这条消息列表上不断追加，所有上下文管理都严格遵循这个格式。

仓库 theory 分支的每个脚本都遵守同一套模型初始化约定，值得照抄到自己的项目里：

```python
def load_llm() -> ChatOpenAI:
    load_dotenv(Path(__file__).resolve().parents[1] / ".env")
    return ChatOpenAI(
        model=os.getenv("MODEL", "qwen3.6-flash"),
        base_url=os.getenv("BASE_URL"),
        api_key=os.getenv("API_KEY"),
        temperature=0,
    )

# 调用时按 message 格式组织输入：
response = llm.invoke([SystemMessage(content=SYSTEM_PROMPT),
                       HumanMessage(content=user_prompt)])
```

四个要点：模型名、base_url、api_key 全部走 .env 环境变量，代码里不落任何密钥；`temperature=0` 让工具调用与路由判断尽量确定（Agent 场景要的是稳定不是创意）；load_llm 封装成一个函数，脚本间复用零成本；输入严格按 `[SystemMessage, HumanMessage, ...]` 的 message 列表组织，这就是"所有操作都是对输入输出文本做处理"在代码层的样子。

## LangGraph：专门做编排的框架

create_agent 创建的 Agent 是单步执行的，要处理长任务、循环、多步骤执行，就需要 LangGraph 来编排。PPT 里对它的定位是：LangGraph 不是多一个模型，而是多了一套流程控制系统。图由 node（节点）和 edge（边）组成：节点是一个"接收 state、返回 state"的函数，边定义节点之间的信息流向，需要条件判断时用 conditional edge（条件边）。

PPT 概念表给每个术语配了工位比喻，复习时很好背：State 是整个图共享的状态数据，节点都可读写——它是 Agent 的工作记忆和任务面板；Node 是图里的一个步骤，一个节点通常只干一件事，像流程图里的一个工位；Edge 决定下一步往哪走；Conditional Edge 是带条件的边，会根据当前结果决定下一跳，就是 if/else 版的流程图；Graph 把 Agent 从"想法"变成"可运行流程图"；Compile 在真正运行前把图做一次检查和封装，像把流程图装配成真正能跑的程序。

核心概念是 state：整个图共享的数据类型，所有节点都可读写。模型节点把"接下来调哪个工具"写进 state，工具节点从 state 里读出来执行，再把结果写回去，state 就这样在图里流转，起到管理上下文的作用。后面 Reflection、Plan&Execute 的架构升级，本质都是往 state 里增加字段、往图里增加节点。

```python
from langgraph.graph import StateGraph, START, END

class AgentState(TypedDict):
    messages: Annotated[list, add_messages]

g = StateGraph(AgentState)      # 1. 建图（声明 state 类型）
g.add_node("agent", agent_node) # 2. 加节点
g.add_node("tools", tool_node)
g.add_edge(START, "agent")      # 3. 连边（含条件边）
g.add_conditional_edges("agent", should_continue, ["tools", END])
g.add_edge("tools", "agent")
graph = g.compile()             # 4. 编译成可运行图
result = graph.invoke({"messages": [("user", task)]})
```

编排口诀是"四步走"：定义节点、把节点加进图、连边（普通边加条件边）、compile 后 invoke。无论是后面多复杂的 Agent 架构，信息编排都是用这一套概念实现的。需要自行编排整个 Agent 的工作流和信息流时——内置编排满足不了需求的时候——用的也正是这套 LangGraph 编排能力。

## 小结与串场

用一句话分清两个框架：LangChain 是积木盒，提供模型、提示词、工具、消息这些标准件；LangGraph 是编排台，决定这些积木按什么流程、什么状态流转。接下来第一块真正的积木是 ToolCall——模型伸向现实世界的手，见系列第三篇。

![LangChain 积木盒与 LangGraph 编排台](/uploads/2026/09/09ab1c1cc2e3b113.png)

[在新标签页打开交互版架构图 ↗](/diagrams/langchain-langgraph-basics.html)

一张图分清两个框架：左边的"积木盒"里，Prompt 与 Memory 喂给 Message 消息列表，Model 基于它输出文本并发出 tool_calls，Chain + Output Parser 把输出整理成结构化结果交给 Agent；右边的"编排台"里，Agent 的步骤注册为图节点，节点读写共享的 State，条件边决定下一跳，最后由 Graph compile → invoke 变成可运行流程。交互版可按"积木盒组件""编排台四步走""State 流转"三个视图聚焦对照。
