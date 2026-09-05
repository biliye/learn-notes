---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Agent Loop 三范式：ReAct、Reflection 与 Plan&Execute
slug: agent-loop-react-reflection-plan-execute
tags: [ReAct, Reflection, Plan&Execute, Agent Loop, LangGraph, 条件边]
summary: Agent Loop 让模型"从做一步到做一件事"：ReAct 用 while 循环把思考与行动串起来，把模型变成状态机；Reflection 加一个批判家节点治"做完不看对错"；Plan&Execute 加 Planner 先谋而后动，治长任务的短视与遗忘。附 LangGraph 四步编排与最小代码。
order: 14
spec_version: v2
---

# Agent Loop 三范式：ReAct、Reflection 与 Plan&Execute

> 整理自 B 站视频《【2026/Agent】一期讲透》第 5–8 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)）。前置：系列第三篇《ToolCall》。UP 主 notion 笔记里把这三者归为 Agent Loop 的逐级进化：循环 → 反思 → 规划。

## ReAct：思考与行动的循环

![ReAct 循环](/uploads/2026/09/3a292431aab6eed6.png)

ToolCall 赋予了模型双手，但它只能完成最简单的单步动作。解决思路是 loop（循环）：把工具的结果反过来喂给模型，让它继续下一步，动作的串联最终完成整个任务。最经典的范式就是 ReAct：R 是 Reasoning（推理思考），Act 是 Acting（行动）。

流程是：问题交给模型 → 模型思考并决定调用工具 → 工具执行，结果作为 Observation（观察）反馈 → 模型再思考：任务没完成就继续调工具，完成了就输出 Answer。它从根本上把大模型变成了一个状态机：模型内部维护一个消息列表，把思考和工具结果都追加进去，带着这个 state 在循环里反复执行。

实现思路非常朴素：写一个 while 循环，每轮把模型回复追加进 messages 数组，有工具调用就执行并把结果也塞回去。终止条件是检查返回里的 tool_calls 字段——不存在或为空时，判定模型不再需要调工具，跳出循环输出最终回答。

```python
messages = [SYSTEM, ("user", "把 inbox 里的 a.txt 移到 archive，并报告目录变化")]
while True:
    resp = llm_with_tools.invoke(messages)
    messages.append(resp)
    if not resp.tool_calls:          # 终止条件：不再调工具
        print(resp.content); break
    for tc in resp.tool_calls:
        result = globals()[tc["name"]].invoke(tc["args"])
        messages.append(ToolMessage(result, tool_call_id=tc["id"]))
```

演示任务是"检查 inbox、把 a.txt 移到 archive、报告目录变化"，配 list_files 和 move_files 两个工具。模型五轮思考分别是：列目录发现 a.txt、执行移动、确认 archive 里有了文件、确认 inbox 已空、判定完成不再调用。而 LangChain 里不用自己写循环：create_agent 传入模型、工具、system prompt，返回的就是一张编译好的 ReAct 循环图，直接 invoke 即可（注意需同时安装 langchain 和 langgraph 两个包）。

不过这种"什么都塞进上下文"的 state 管理非常粗暴，正是后来 Context Engineering 要优化的对象。

## Reflection：吾日三省吾身

![三大 Loop 范式演进](/uploads/2026/09/b477931b009807e3.png)

ReAct 像一个急于表现的实习生：代码飞快写完看都不看一眼就交给你，跑起来处处报错——它只顾"做完"，不看"做对没有"。解法是在思考、行动、反馈之上多引入一个 critic（批判家）节点：执行模型觉得自己完成后，先把原始任务和已完成的信息交给 critic 对比，由它决定是打回重做还是放行输出。

这一节的重头戏是用 LangGraph 完整走一遍"四步走"编排。定义节点：agent 节点调模型，tools 节点解析并执行 tool_calls 把结果写回 state；条件函数 should_continue 检查最后一条消息有没有 tool_calls，有返回 "tools"，没有返回 END。然后加节点、连边（START→agent、agent 条件边→tools 或 END、tools→agent）、compile 后 invoke。

```python
def should_continue(state: AgentState) -> str:
    last = state["messages"][-1]
    return "tools" if getattr(last, "tool_calls", None) else END
```

Reflection 图的信息流：state 从 START 到 agent，模型输出 tool_calls 流向 tools 执行，工具结果返回给 reflection 节点复盘，reflection 给出下一步指引（如"请继续执行移动"）再流回 agent，循环直到任务完成走向 END。代码里把循环上限设为 12 次防死循环。实际演示里模型能力太强，简单任务触发不了打回——想看到 reflection 生效，要构造足够复杂的任务。

## Plan & Execute：先谋而后动

有了反思能力后，剩下的致命问题是短视和遗忘：任务拉长到几十个节点，上下文一长，模型就把前面的目标忘掉了。Plan & Execute 的口诀是"先谋而后动"：在循环之前先引入一个 Planner 分析任务、制定 to-do 清单，把清单逐项交给 Executor 循环执行，执行完交给 Replanner 核对——有遗漏或做错就打回重新规划，全部正确才输出答案。

相比 Reflection 只多了一个 planner 节点，但 state 的设计变丰富了：除了 messages，还要存 plan（计划）、task（当前待办）、past_steps（反思信息）。图里也因此有了三个模型节点：planner、agent（executor）、replanner。演示中 planner 生成"1 检查 inbox、2 移动文件、3 检查目录"三步计划放入 state，executor 按步执行，replanner 核对无误后放行。

这个范式的价值在长任务上才真正体现：当循环达到几百上千轮，state 里的 plan 和 task 能实时提醒模型"不要忘记最初的使命"。后面 Context Engineering 的 to-do 机制正是把这个思想工程化。而单体 Agent 再往上走会遇到能力天花板，如何让多个 Agent 分工协作，见系列第五篇。
