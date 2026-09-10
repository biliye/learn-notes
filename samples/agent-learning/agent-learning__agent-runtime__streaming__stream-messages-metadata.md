---
path: [agent学习, Agent 运行, 流式输出]
slugs: [agent-learning, agent-runtime, streaming]
title: agent.stream 的 metadata 是什么
slug: stream-messages-metadata
tags: [LangChain, LangGraph, 流式输出, metadata]
summary: 说清 stream_mode="messages" 里第二个返回值 metadata 的含义与常用键，解释为什么有些 chunk 的 content 是空的、工具执行结果为什么会混在流里，并给出只打印模型回答的过滤写法。
order: 10
spec_version: v2
---

# agent.stream 的 metadata 是什么

用 `create_agent` 建好 agent 之后，用 `stream_mode="messages"` 流式输出，循环里拿到的是两个值：`token` 和 `metadata`。token 好理解，是模型吐出来的字；metadata 是干什么用的，这篇笔记讲清它。

## 一句话：它是"这个 token 从哪儿来"的说明卡片

`metadata` 是一个普通的 `dict`。关键是它**跟大模型没有关系**——不是模型返回的东西，而是 LangGraph 在流式传输过程中**附加上去的本地路由信息**。

```python
for token, metadata in agent.stream(
    {"messages": [{"role": "user", "content": "月亮的首都是哪里？"}]},
    stream_mode="messages",
):
    print(token.content, metadata["langgraph_node"])
```

实测（langchain 1.4.0）第一个 chunk 的 metadata 里这些键：

| 键 | 值的样子 | 含义 |
|---|---|---|
| `langgraph_node` | `"model"` | 哪个节点产出的，最常用的一个 |
| `langgraph_step` | `1` | 第几轮循环（agent 是一圈一圈跑的） |
| `langgraph_path` | `('__pregel_pull', 'model')` | 内部路径，基本用不上 |
| `langgraph_triggers` | `('branch:to:model',)` | 什么触发了这个节点 |
| `langgraph_checkpoint_ns` | `model:143c013e-...` | 检查点命名空间，做多轮对话时有用 |
| `ls_provider` | `"openai"` | 实际干活的 provider |
| `ls_model_name` | 模型名 | 哪个模型产的，多模型场景下区分用 |
| `ls_model_type` | `"chat"` | 模型类型 |
| `lc_versions` | `{'langchain-core': '1.6.2', ...}` | 版本，排查环境问题有用 |

只用一个模型、没接工具时，真正会用到的基本只有 `langgraph_node`。

## 为什么需要它：一个 agent 里的 token 不止来自模型

agent 不是"问一次答一次"，它内部是循环的：模型先决定要不要调工具，工具执行，结果喂回模型，模型再出最终回答。所以一条流里会混进不同来源的内容。

用带工具的 agent 实测，`stream_mode="messages"` 吐出来的完整序列是：

```text
AIMessageChunk   node=model   step=1  content=''
AIMessageChunk   node=model   step=1  content=''
ToolMessage      node=tools   step=2  content='月亮没有首都，它不是一个国家'
AIMessageChunk   node=model   step=3  content='月亮'
AIMessageChunk   node=model   step=3  content='没有'
AIMessageChunk   node=model   step=3  content='首都'
AIMessageChunk   node=model   step=3  content=''
```

看这张表能明白两件新手常困惑的事。

第一，**有些 chunk 的 `content` 是空字符串**。step=1 那两行就是，此时模型在表达"我要调工具"，工具调用的信息藏在 `token.tool_calls` 里而不是 `content` 里。不加判断直接 `print(token.content)`，屏幕上就会出现莫名其妙的空行。

第二，**工具的执行结果也混在这条流里**。它是 `ToolMessage` 而不是 `AIMessageChunk`，内容是一大坨工具返回值。所以只想看模型说的话，光判断 `content` 还不够。

## 实际用法：靠 metadata 过滤

`metadata` 最实用的地方就是过滤。如果只想打印模型说的字，把工具节点排除掉：

```python
for token, metadata in agent.stream(payload, stream_mode="messages"):
    if metadata["langgraph_node"] != "model":   # 跳过工具节点
        continue
    if token.content:                            # 跳过空内容（工具调用那一轮）
        print(token.content, end="", flush=True)
```

这两个判断各挡一类干扰：前者挡掉工具的执行结果，后者挡掉工具调用产生的空 chunk。很多教程里只写了 `if token.content`，那就把工具的执行结果也一起打印出来了。

以后如果接多个模型（比如便宜的模型干脏活、贵的模型出结论），靠 `metadata["ls_model_name"]` 就能分辨眼前这段字是哪个模型说的。

## 自检：确认你拿到的是哪一类

快速看一眼类型，比盯着输出猜快得多。

```python
for token, metadata in agent.stream(payload, stream_mode="messages"):
    print(type(token).__name__, metadata["langgraph_node"], repr(token.content[:20]))
    break
```

`AIMessageChunk` 配 `node=model` 是模型在说话，`ToolMessage` 配 `node=tools` 是工具在执行，一眼就能分清。

## 小结

`metadata` 是 LangGraph 附加的路由信息，不是模型给的，作用就是告诉你"这段内容出自哪个节点、第几步"。记住三件事：一条流里可能同时有模型的话和工具的结果；工具调用那一轮的 `content` 是空的；用 `langgraph_node` 加 `content` 两个判断就能只保留模型说的话。
