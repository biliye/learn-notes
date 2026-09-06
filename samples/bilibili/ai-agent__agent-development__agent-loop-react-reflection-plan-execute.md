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

> 整理自 B 站视频《【2026/Agent】一期讲透》第 5–8 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），代码细节补充自仓库 Wood-Q/MokioAgent 的 theory 分支（`2. AgentLoop/` 目录）。前置：系列第三篇《ToolCall》。UP 主 notion 笔记里把这三者归为 Agent Loop 的逐级进化：循环 → 反思 → 规划。PPT 的总结：ReAct 是思考、行动、反馈的重复；Reflection 在中间加一个批判 Role；Plan&Execute 再加一个 SMART 模型先做计划。

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

演示任务是"检查 inbox、把 a.txt 移到 archive、报告目录变化"，配 list_files 和 move_files 两个工具。模型五轮思考分别是：列目录发现 a.txt、执行移动、确认 archive 里有了文件、确认 inbox 已空、判定完成不再调用。仓库里 `01_while_loop.py` 的真实写法有三个细节值得学：外层用 `for turn in range(1, 8)` 兜底最多 7 轮，防止意外死循环；每轮把 response 先 append 进 messages 再解析；工具结果用 ToolMessage 包装并带上 tool_call_id，和发起调用的 assistant 消息一一对应。而 LangChain 里不用自己写循环：create_agent 传入模型、工具、system prompt，返回的就是一张编译好的 ReAct 循环图，直接 invoke 即可（注意需同时安装 langchain 和 langgraph 两个包）。

create_agent 的调用在仓库 `02_create_agent.py` 里就三步，注意它要的是三个具名参数：

```python
from langchain.agents import create_agent

agent = create_agent(
    model=load_llm(),                     # 未绑工具的裸模型即可
    tools=[list_files, move_file],        # 工具绑定由 create_agent 内部完成
    system_prompt=SYSTEM_PROMPT,          # 注意参数名是 system_prompt，不是 prompt
)
result = agent.invoke({"messages": [{"role": "user", "content": task}]})

for message in result["messages"]:        # 消息流里能看到完整的思考-调用-观察序列
    if getattr(message, "tool_calls", None):
        print(message.tool_calls)
    elif getattr(message, "content", ""):
        print(message.content)
```

对比手写 while 循环，create_agent 帮你省掉了三件事：循环控制与终止判断、tool_calls 与 ToolMessage 的配对、以及每轮消息的追加顺序。它返回的就是一张编译好的 LangGraph 图（视频里需要补装 langgraph 包的原因就在这），invoke 的入参和出参都是 messages 字典。视频演示时踩过的坑也值得记：参数名打成 prompt 会直接报错，正确的是 system_prompt。

不过这种"什么都塞进上下文"的 state 管理非常粗暴，正是后来 Context Engineering 要优化的对象。

## Reflection：吾日三省吾身

![三大 Loop 范式演进](/uploads/2026/09/b477931b009807e3.png)

ReAct 像一个急于表现的实习生：代码飞快写完看都不看一眼就交给你，跑起来处处报错——它只顾"做完"，不看"做对没有"。解法是在思考、行动、反馈之上多引入一个 critic（批判家）节点：执行模型觉得自己完成后，先把原始任务和已完成的信息交给 critic 对比，由它决定是打回重做还是放行输出。

这一节的重头戏是用 LangGraph 完整走一遍"四步走"编排。定义节点：agent 节点调模型，tools 节点解析并执行 tool_calls 把结果写回 state；条件函数 should_continue 检查最后一条消息有没有 tool_calls，有返回 "tools"，没有返回 END。然后加节点、连边（START→agent、agent 条件边→tools 或 END、tools→agent）、compile 后 invoke。仓库 `02_5_langgraph_react.py` 把 ReAct 循环从 while 改写成显式图，节点长这样：

```python
class AgentState(TypedDict):
    messages: list[BaseMessage]      # 整个图只有这一个 state 字段

def agent_node(state: AgentState) -> AgentState:
    response = tool_llm.invoke([SystemMessage(content=SYSTEM_PROMPT), *state["messages"]])
    return {"messages": [*state["messages"], response]}

def tools_node(state: AgentState) -> AgentState:
    new_messages = list(state["messages"])
    for tool_call in state["messages"][-1].tool_calls:
        result = tool_map[tool_call["name"]].invoke(tool_call["args"])
        new_messages.append(ToolMessage(content=str(result),
                                        name=tool_call["name"],
                                        tool_call_id=tool_call["id"]))
    return {"messages": new_messages}

graph = StateGraph(AgentState)
graph.add_node("agent", agent_node)
graph.add_node("tools", tools_node)
graph.add_edge(START, "agent")
graph.add_conditional_edges("agent", should_continue)   # 无 tool_calls 则 END
graph.add_edge("tools", "agent")
graph.compile().invoke({"messages": [HumanMessage(content=task)]})
```

和 while 版逐项对照：循环变量换成了"图的状态流转"，`while True` 换成 agent→tools 的环形边，`if not resp.tool_calls: break` 换成条件边走向 END。收益是流程变成了可声明、可画图、可插入新节点的结构——后面 Reflection 只需要在图上动刀，不用重写循环体。

```python
def should_continue(state: AgentState) -> str:
    last = state["messages"][-1]
    return "tools" if getattr(last, "tool_calls", None) else END
```

Reflection 图的信息流：state 从 START 到 agent，模型输出 tool_calls 流向 tools 执行，工具结果返回给 reflection 节点复盘，reflection 给出下一步指引（如"请继续执行移动"）再流回 agent，循环直到任务完成走向 END。仓库 `03_langgraph_reflection.py` 的实现有两个巧思：state 里专门加了 `reflection: str` 字段，agent 节点每轮把这段 note 拼进 system prompt 优先参考；reflection 节点只取最近 4 条消息生成一句话建议，控制成本。运行时用 `config={"recursion_limit": 12}` 防死循环。实际演示里模型能力太强，简单任务触发不了打回——想看到 reflection 生效，要构造足够复杂的任务。

## Plan & Execute：先谋而后动

有了反思能力后，剩下的致命问题是短视和遗忘：任务拉长到几十个节点，上下文一长，模型就把前面的目标忘掉了。Plan & Execute 的口诀是"先谋而后动"：在循环之前先引入一个 Planner 分析任务、制定 to-do 清单，把清单逐项交给 Executor 循环执行，执行完交给 Replanner 核对——有遗漏或做错就打回重新规划，全部正确才输出答案。

![Agent Loop 三范式全景：Plan&Execute 完整图](/uploads/2026/09/a786a22e2d4a559e.png)

[在新标签页打开交互版架构图 ↗](/diagrams/agent-loop-paradigms.html)

把三个范式叠在一张图上看：主流程从用户任务出发，先经 Planner 规划器产出 to-do 清单（Plan&Execute 新增），再进入 Agent 与 Tools 的 ReAct 循环；工具结果不直接喂回模型，而是先流过 Reflection 批判家复盘（Reflection 新增），指引流回后继续循环，直到返回里不再出现 tool_calls，才沿条件边走向最终回答。交互版支持缩放、明暗主题与关系追踪，想逐条链路看数据流向可以点开对照。

相比 Reflection 只多了一个 planner 节点，但 state 的设计变丰富了：除了 messages，还要存 task（原始任务）、plan（步骤列表）、reflection（指引）。图里也因此有了三个模型节点：planner、agent（executor）、reflection。仓库 `04_langgraph_plan_execute.py` 里 planner 的实现值得看：

```python
def parse_plan(text: str) -> list[str]:
    steps = []
    for line in text.splitlines():
        line = re.sub(r"^\s*[-*\d.、)]+\s*", "", line).strip()  # 剥掉 1. / - / 、等序号
        if line:
            steps.append(line)
    return steps or ["检查 inbox", "移动 a.txt 到 archive", "查看整理后的目录"]

def planner_node(state: AgentState) -> AgentState:
    response = base_llm.invoke([SystemMessage(content=PLANNER_PROMPT),
                                HumanMessage(content=state["task"])])
    plan = parse_plan(str(response.content))
    plan_text = "\n".join(f"{i}. {s}" for i, s in enumerate(plan, start=1))
    return {**state, "plan": plan,
            "messages": [HumanMessage(content=f"用户任务：{state['task']}\n\n计划：\n{plan_text}")]}
```

两个工程习惯藏在细节里：`parse_plan` 的正则剥掉行首的序号和符号，不管模型输出 `1.`、`-` 还是顿号编号都能归一化，还配了一个兜底默认计划防止空计划卡死后续节点；计划以编号文本拼进第一条 HumanMessage 一起下发，executor 从此每轮都"看得见"计划。连边顺序是 START→planner→agent→条件边→tools/END、tools→reflection→agent，运行时 recursion_limit 放宽到 50，因为步数变多了。

这个范式的价值在长任务上才真正体现：当循环达到几百上千轮，state 里的 plan 和 task 能实时提醒模型"不要忘记最初的使命"。后面 Context Engineering 的 to-do 机制正是把这个思想工程化。而单体 Agent 再往上走会遇到能力天花板，如何让多个 Agent 分工协作，见系列第五篇。
