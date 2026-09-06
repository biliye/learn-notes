---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Claw 交互层与 MokioClaw 项目实战
slug: claw-product-mokioclaw-project
tags: [OpenClaw, TUI, Vibe Coding, MokioClaw, Checkpoint, 审批]
summary: 工程化做完，最后一公里是产品化：OpenClaw 以 36 万 star 成为 GitHub 第一单体项目，靠的不是新技术而是交互层。本篇记录 Claw 层的产品思维，以及 MokioClaw 从最小 ReAct 循环到审批、检查点、链路追踪、TUI 界面的六步进化，附完整能力栈总结与 UP 主三句寄语。
order: 18
spec_version: v2
---

# Claw 交互层与 MokioClaw 项目实战

> 整理自 B 站视频《【2026/Agent】一期讲透》第 12–14、19–20 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），数据与定位补充自 UP 的 notion 笔记，项目结构与技术栈来自 GitHub 仓库 Wood-Q/MokioAgent（master 分支为项目本体，theory 分支为教学代码与 108 页《Agent指南》PPT）。前置：系列第六、七篇。

## OpenClaw：交互层的产品化宣言

2026 年 OpenClaw 爆火，"龙虾"一词开始广泛传播。UP 主 notion 笔记记录的数据是：GitHub 仓库 361k stars、73.6k forks，成为 GitHub star 第一单体项目（视频录制时已涨到 371K）。但 UP 主的判断很犀利：OpenClaw 本身没有任何技术贡献，不像 Context/Harness Engineering 提出了可用的思想。

它最大的贡献是告诉大家：现在的 Agent 时代早就不再是程序员工程师的范围了。类比开发网站——数据库、Redis、HTTP 框架这些底层搭好之后，下一步是做成网页或 App 交付交互，你不可能把后端逻辑直接给用户用。Agent 也一样：后端就是用 Harness + Context 工程搭起来的整系统，前端就是交互层，两者叠在一起才构成一个 OpenClaw。

它揭示的趋势是：未来 Agent 开发不能只盯着"能力"，还必须盯着"可用性"。谁把交互层做得更简单明了，谁就更接近真正的产品。UP 主顺势延伸出产品思维：技术再强也不能直接代表你的社会能力，要会把自己"包装成产品"——拿得出手的项目、别人一眼能看懂的成绩，把他人认知你的难度降到最低。

## Vibe Coding 的正确姿势

项目篇演示了 MokioClaw 如何从最小原型一步步进化，而比代码更重要的是工作方法：vibe coding 之前必须先做设计，否则任务流程会偏到十万八千里。第一步需求设计，最简模板是搞清楚三件事：input 是什么、output 是什么、others（额外指标，比如必须遵守的流程）。

第二步是 Agent 系统设计，固定从四个维度考虑：loop 设计（最重要，循环图的执行逻辑、何时调哪个工具、何时流转到哪个节点）、tool 设计（接入哪些工具、怎么优化）、state 设计（哪些信息放进 state 全局记录和流转）、workspace 设计（Agent 在工作目录生成哪些东西，包括 to-do.md、notepad.md 这类产物）。每次架构升级，都是在这四个维度上做文章。

MokioClaw 的技术栈选型在仓库 README 里写得很明白：LLM 调用用 langchain 加 langchain-openai，工具绑定与消息管理开箱即用；工作流引擎用 langgraph，状态图加条件路由天然适合 Plan→Execute→Verify 循环；CLI 框架用 typer；TUI 界面用 textual（视频里"Python 版 React Ink"指的就是它，支持异步事件）；搜索用 tavily-python。仓库本体按模块分层：tools/（文件读写、grep、bash、notepad、todo、web_search）、graph/（workflow、nodes、state、memory）、core/（agent、approval、checkpoint、trace、session）、agents/（code_agent、search_agent）、cli/（含 tui/ 界面）、prompts/（按阶段演进的提示词）。

## 六步进化路线

项目篇的六步进化，每一步都对应正片的一个概念。第一步 ReAct 基座：用 create_agent 搭最小循环，工具参考 Claude Code 源码设计（写文件、搜索、读文件、编辑、batch 五件套），state 只放 messages，演示任务是生成贪吃蛇游戏 HTML。

第二步 Plan&Execute：把单 Agent 拆成 Planner、Actor、Verifier 三角色，从 create_agent 改用 LangGraph 自由编排，state 增加 task、to-do、检查结果三类字段并定义专门的 Todo、VerifyResult 类。演示任务改成必须按 TDD 流程（先写测试用例再写代码再跑测试）执行的生命游戏，中途 Verifier 发现 pytest 过不了自动打回 Replanner 重规划。

第三步 Multi-Agent：把 Actor 拆成 code agent 和 search agent 两个专家，search 工具用 Tavily 实现（给 Agent 提供搜索引擎接口），专家之间通过 handoff 交接，用 Agent as Tool 方式调用。演示任务是"先上网搜鹅腿阿姨的新闻，再写一个介绍事件流程的 HTML"，可以看到系统先调 search agent 拿资料，再调 code agent 写页面。

第四步 Context Engineering：实现三项措施——to-do.md 强制明文落盘（todo_tool 直接读写 TODO.md 并渲染 markdown 复选框）、notepad.md 实时记录关键信息、压缩机制（新增 monitor 节点在每轮 planner 前估算 token，超过上限——默认 400000、可用 MOKIO_CONTEXT_TOKEN_LIMIT 环境变量调整——就导向 compressor 节点用小模型总结）。代码里还做了分层记忆：规则记忆（项目必须的工作约束）、工作记忆（当前上下文窗口）、压缩记忆（HISTORY_SUMMARY.md 历史归档）三层分类。演示任务是搭一个完整的 Flask 后端管理系统，最终 to-do.md 正常生成，monitor 每轮都在检查上下文长度。

第五步 Harness：引入三项措施。人类暂缓审批——任一节点调 batch 工具时自动判断命令安全等级（源码里是一张正则风险清单：包安装、网络下载、长驻服务等命中即拦截），危险命令弹窗等人类输 Y/N，还支持 auto 自动批准与 deny 直接拒绝两种模式，批准过的命令记入白名单；checkpoint——light 模式只记录"执行到哪一步"的任务信息加 git 快照，strict 模式全量保存事件流与图状态，存放在 .mokioclaw/checkpoints/ 下（checkpoint.json、events.jsonl、state.json、RECOVERY.md 恢复说明），恢复时前者相当于重启一个读过进度的新 Agent，后者是直接把原 Agent 从断点扶起来续跑；完整 trace 链路——全程日志落盘为 summary.json 与 timeline.md（时间线只保留头部 40 条、尾部 80 条事件，可观测也要控成本）。演示里正好遇到模型额度用完自动中断，用 resume 命令从 checkpoint 恢复续跑，验证了容灾能力。

第六步交互层：反而最简单，选一个 TUI 框架即可。Claude Code、Gemini CLI 这类产品的前端用的是 React Ink（TypeScript 生态），MokioClaw 是 Python 项目，对应选型就是 textual（README 技术栈表里明确写了：现代终端 UI，支持异步事件）。告诉 AI"用这个库给我做一个 TUI 界面"就能仿出一个 Claude Code 式面板：cli/tui/ 目录下有多轮对话 session、审批弹窗（approval.py）、事件摘要与格式化输出，右侧状态栏展示信息。当前项目处于第 6 阶段第一步——在 MultiAgent、Context Engineering、Harness Engineering 基础上新增 Textual TUI 本地交互层，接飞书 API 是下一步。UP 主 notion 里扩展层规划的天气、飞书、日常 API、skills、MCP、HITL 都从这里接入。

最终形态的架构图 README 里画得很清楚：外层 LangGraph 只剩 supervisor 与验收循环——用户任务先经 intent_router 分流（普通聊天走 chat_responder 直接回答），任务进 planner，supervisor 用 toolcall 方式交接 CallSearchAgentTool 与 CallCodeAgentTool 两个子 Agent，回来后过 context_monitor，超限就进 context_compressor，然后 verifier 验收，pass 走 final，fail 且次数未超上限就打回 planner。前几篇讲的 handoff、monitor、compressor、verifier 在这里全部串成了一张图。

![能力栈总结](/uploads/2026/09/881fb1e667bc318d.png)

## 总结与寄语

回顾整个发展历程：最初的 Agent 只是一个 chatbot，ToolCall 打破缸中之脑的禁锢，赋予模型触及现实的双手；Agent Loop 让它从做一步到做一件事，拥有了自主纠错与分工协作的双腿；Context Engineering 优化了 Agent 的大脑，让它有跑完万里长征且不漂移的脑力；Harness Engineering 用传统工程思想武装系统，这匹烈马在驾驭之下变得安全稳定，可投入生产级使用；最后加上 Claw 交互层，工程才转化为能交付用户的产品。

UP 主的三句寄语值得单独记录。第一，AI 时代新概念层出不穷，但概念背后的知识才重要——Skill、OpenClaw 这些东西爆火一定有它的道理，摒弃傲慢第一时间去学，才能不落伍于时代。第二，传统工程思想依然有用：学会排错、动手、技术方案背后的思想而不是死记知识，才能成为合格的开发者，而不是 AI 的附属工具。第三，学会产品思维：技术只是工具，产品面向用户，学会洞悉需求、打造产品、自我包装。最后一句送给你：在 AI 技术火爆、充满机遇的现在，去创造，不要只做时代的旁观者。
