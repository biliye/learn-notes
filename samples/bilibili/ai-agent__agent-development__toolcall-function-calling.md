---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: ToolCall：从文本触及现实
slug: toolcall-function-calling
tags: [Function Calling, ToolCall, 结构化输出, LangChain, tool_calls]
summary: Agent 诞生的起点：模型曾是无法与现实交互的缸中之脑，Function Calling 用"结构化输出 + 本地代码翻译执行"打破禁锢。从手写协议解析讲到 @tool、bind_tools 与 tool_calls 字段，理解 OpenAI 为什么要在训练层面强制 JSON 输出。
order: 13
spec_version: v2
---

# ToolCall：从文本触及现实

> 整理自 B 站视频《【2026/Agent】一期讲透》第 3、4 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)）。前置：系列第二篇《LangChain 与 LangGraph 地基》。UP 主 notion 笔记对这一节的概括是：ToolCall 的本质就是字符串处理——对模型输出进行处理。

## 痛点：缸中之脑

2023 年 ChatGPT 横空出世时，它上知天文下知地理，但问它北京天气怎么样，只能道歉说"我只是一个语言模型，无法获取实时数据"。它能交流却不能与现实世界交互，充其量是一个大号字典：可以查知识，不能解决现实问题。这就是 OpenAI 研发 Function Calling 的出发点。

## 本质定义：结构化输出 + 本地翻译执行

再次强调第一性原理：Agent 的一切处理，本质上都是针对模型"输入文本、输出文本"的操作。Function Calling 的本质因此是：让模型结构化地输出"我要调哪个工具、带什么参数"这段文本，再由本地代码翻译并执行。模型从头到尾只输出文本，真正的动作发生在你的代码里。

这个过程完全可以手动实现一个玩具协议：约定模型用 tag 包裹工具名和参数，比如输出 `<tool>search</tool><arg>北京天气</arg>`。我们用正则从字符串里提取工具名，在提前定义好的工具列表里找到对应函数，把参数传进去执行——无论工具内部是调谷歌还是调 Tavily，具体逻辑都发生在本地代码里。

代码上分三步递进。第一步纯文本操作：给定模型输出 `get_weather:beijing` 这样的字符串，用 split 拆出工具名和参数，查找并调用函数，体会"解析、寻找工具、执行"的最小闭环。第二步自己制定协议：在 system prompt 里要求模型按 tag 格式输出，用正则提取后执行。

## 幻觉问题与 OpenAI 的解法

靠 prompt 约定格式有致命风险：模型有幻觉，万一多打一个引号、少写半个括号、换个格式，写死的解析代码就直接崩溃，对工程项目是灾难性后果。OpenAI 的解法是从底层下手：用海量"按 JSON 格式输出工具调用"的训练数据把模型训练成严谨的理科生。

当你开启 Function Calling 模式时，模型底层输出的概率分布会被强制干预，保证输出纯净的 JSON。这就是为什么生产环境里工具调用的格式可靠性远高于"让模型自觉"——协议没有停留在 prompt 层面，而是被训练进了模型。

## 现代做法：@tool 与 bind_tools

```python
from langchain.chat_models import init_chat_model
from langchain_core.tools import tool

@tool
def get_weather(city: str) -> str:
    """Get the weather for a city."""
    return f"{city} 25 度"

llm = init_chat_model("deepseek-v4", ...)   # 任意 OpenAI 兼容模型
llm_with_tools = llm.bind_tools([get_weather])
resp = llm_with_tools.invoke(messages)
print(resp.tool_calls)   # [{'name': 'get_weather', 'args': {'city': '北京'}, 'id': 'call_...'}]
```

用 @tool 装饰器把函数变成工具，函数的 docstring 注释会被自动解析成工具描述喂给模型——模型正是靠这段描述才知道何时该调它、参数有哪些。bind_tools 把工具绑定到模型上，模型回复的 message 里就带 tool_calls 属性，包含 name、args、id 三个字段。注册多个工具时模型会返回一个 tool_calls 数组，比如同时给它 get_weather 和 get_time，再问"北京天气和时间"，一次就能拿回两个调用。

看底层 API 的原始返回值，里面本来就有 function_call / tool_calls 字段——LangChain 只是把这些字段提取出来封装成了消息类。框架没有魔法，依然符合第一性原理：一切处理都是对模型输入输出文本的操作。

## 小结与串场

ToolCall 打破了缸中之脑的禁锢，赋予模型触及现实的双手，但它一次只能完成一步动作：能搜索，却没法在搜索结果上继续整理，完成不了多步任务。让模型"从做一步到做一件事"，靠的是下一 Agent Loop 的循环，见系列第四篇。
