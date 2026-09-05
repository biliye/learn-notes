---
category: AI Agent
category_slug: ai-agent
topic: Agent 开发
topic_slug: agent-development
title: Harness Engineering：给烈马套上缰绳
slug: harness-engineering
tags: [Harness Engineering, Human-in-the-loop, Checkpoint, 沙箱, 可观测性, CI]
summary: 上下文管好了，系统还可能被一条危险命令毁掉。Harness Engineering 为整个运行时保驾护航：从 OpenAI 2026 年工程文章的 Codex 实验讲起，梳理它与 Context Engineering 的互补关系，以及 Human-in-the-loop、Checkpoint、环境隔离、可观测四类保障在六阶段高危任务中的落地。
order: 17
spec_version: v2
---

# Harness Engineering：给烈马套上缰绳

> 整理自 B 站视频《【2026/Agent】一期讲透》第 11 分P（[原视频](https://www.bilibili.com/video/BV1dw526tEMA/)），起源与定位部分补充自 UP 的 notion 笔记。这是全系列的两个工程核心之二。

## 起源：Agent 工程进入生产阶段

Harness Engineering 不是一个古老的学术名词，它更像是 Agent 工程进入生产阶段后，对"运行时工程"这一整组问题的重新命名。UP 主 notion 笔记里记录了标志性事件：OpenAI 在 2026 年 2 月的官方工程文章里介绍了一个内部实验——从空仓库开始，用 Codex 生成应用逻辑、测试、CI、文档、可观测性和内部工具，估算只用了手写方式约十分之一的时间；五个月后仓库规模达到约百万行代码，由一个很小的工程团队驱动。

更关键的是他们指出：真正变化的不是"模型会不会写代码"，而是工程师的职责——不再主要是手写代码，而是设计环境、指定意图、构建反馈回路，让 agent 能可靠地工作。

## 与 Context Engineering 的互补关系

这和 Anthropic 对上下文工程的说法是互补的。Anthropic 说，随着 agent 进入多轮、长时程任务，问题不再只是 prompt，而是整个上下文状态的管理；OpenAI 则把另一半补全了：即使上下文做对了，若没有稳定的运行壳，agent 依然很难在真实系统里持续产出。

两句定义对照着背：Context Engineering 解决的是**模型此刻看到了什么**；Harness Engineering 解决的是**模型此刻在什么环境里工作、能做什么、怎么被约束、怎么恢复、怎么被观察**。前者管大脑的输入，后者管系统的 runtime。

不考虑 harness 的 Agent 像一匹很能跑但没有驯服的烈马：它可能莫名其妙输出一条删除命令而你没有任何监管，服务器直接爆炸；可能偷偷生成垃圾文件蚕食磁盘；跑到一半断电全丢，重跑又是十四天；API 额度是收费的，它不知道收敛，疯狂调用把你的钱刷爆。模型自己处理不了这些系统级问题。

## 四类保障：传统工程思想的回归

传统软件工程看四个关键指标：高可用、高安全、可观测，外加高性能。高性能由 Context Engineering 负责，剩下三样交给 Harness Engineering，解法全是传统工程思想照搬过来。

安全可控靠 human in the loop：给操作分级，达到危险等级的命令必须交人类裁决，就像传统系统里的权限控制与审批流。高可靠靠检查点与断点重跑：像训练模型存 checkpoint 一样定时保存执行记录，宕机后从检查点加载续跑。环境隔离靠沙箱、工作区和 sub-agent：前者隔离系统环境防污染，后者隔离上下文窗口保专注。可观测靠日志、metrics、trace 建立反馈闭环——不仅人能看，把这些喂给模型，模型也能看着"心电图"自我诊断。

## 六阶段实战：一个高危任务怎么驾驭

示例任务是高危的"重构核心数据库迁移脚本，本地测试跑通后合并部署到服务器"——它涉及数据库与服务器这类系统级核心应用。按六个情形对比。

启动阶段：没 harness 的 Agent 直接在你宿主机当前目录开干、随手改全局 Python 环境变量；有 harness 则启动时拦截，建一个临时 worktree 或 docker 沙箱容器，Agent 只能在隔离罩里折腾，任务完成后你的环境一尘不染。

编码阶段：prompt 里反复强调"别改生产配置"也不能保证概率为零；harness 的做法是设置权限边界——rm -rf 这类命令压根不暴露给模型，甚至在 read_file 的函数体里写 if 目标是生产配置文件就直接 return，从代码层面物理限制模型的行为路径。

高危操作：Agent 写完脚本直接 run_shell，本地数据库可能灰飞烟灭。harness 把工具分级为普通工具和敏感工具，触发敏感调用时系统弹窗"Agent 想执行某某命令，是否允许"，只有人类亲自敲下回车它才执行——枪不会自己走火，主动权永远在人手里。Claude Code 和 Codex 的审批弹窗就是这个机制。

意外中断：跑到第 45 步 API 超时、合上笔记本、停电，进程断掉全部白干。harness 每走完一个节点就持久化一个 checkpoint，第二天敲一个 resume，系统读取检查点状态原地满血复活。遇到报错：harness 因为搜集了全链路的 trace、metrics、log，把这些喂给模型，模型就能看着心电图自我诊断，而不是等你人工排查几万行日志。

完成交付：看着不错但你不知道代码有没有隐藏性能坑。harness 在交付前设强制 hook，触发 lint 和 CI 自动化检查，测试覆盖不达标直接打回重写。注意这些是外部代码做的严格逻辑审查，而不是把测试也交给 AI——AI 自己也有出错概率。

## Skill：把能力变成可复用资产

UP 主 notion 笔记在这一节还补了一句：常见做法里包括 Skill——把能力变成可复用资产。工程化完善之后，最终一定是产品化，触及非技术人员。产品化的最后一公里——交互层，见系列第八篇《Claw 交互层与 MokioClaw 项目实战》。

一句话总结本篇：Harness Engineering 没有发明新东西，本质是把过去开发中沉淀的各种工程手段套到 AI 身上，给它外部套上一个可靠的笼子。传统工程思想在任何开发过程中都不会过时。
