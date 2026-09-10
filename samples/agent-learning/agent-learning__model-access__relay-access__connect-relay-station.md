---
path: [agent学习, 模型接入, 中转站]
slugs: [agent-learning, model-access, relay-access]
title: 接中转站：让 LangChain 调用它没集成的模型
slug: connect-relay-station
tags: [LangChain, 中转站, 模型接入, OpenAI 兼容]
summary: 说清"LangChain 没集成某模型"时到底该怎么办：绝大多数中转站与国产模型说的是 OpenAI 的协议方言，所以只需换 base_url；另附判断方法、真正不兼容时的三档方案，以及中转站最容易残缺的工具调用。
order: 10
spec_version: v2
---

# 接中转站：让 LangChain 调用它没集成的模型

很多模型都不在 LangChain 的集成列表里：国内的中转站、部分国产大模型、本地跑的 Ollama 和 vLLM。这篇笔记说清一件事——它们绝大多数能直接用，而且不需要等官方出插件。

## 能不能接，看协议不看集成

用 Java 的思维理解最顺：OpenAI 定义了一套**接口**——请求打到 `/v1/chat/completions`、字段名固定、鉴权头是 `Authorization: Bearer <key>`、返回的 JSON 形状固定。中转站做的是"实现这套接口"：收到 OpenAI 形状的请求，转发给真模型，再把结果按同样的形状吐回来。

而 `langchain-openai` 是**按接口编程**的。它不关心对面是谁，只要对面听得懂这套话就行。所以"接中转站"要做的事情不是找插件，而是**换地址**：协议没变，代码就一行都不用改。

## 换地址就行

```python
from langchain_openai import ChatOpenAI

llm = ChatOpenAI(
    model="deepseek-chat",                 # 中转站文档里给的模型名
    api_key="sk-xxx",                      # 中转站发给你的 key
    base_url="https://你的中转站域名/v1",    # 中转站给的地址
)
```

只有这三样跟连 OpenAI 官方时不一样，`ChatOpenAI` 这个类本身没变。用 `init_chat_model` 写法也一样：

```python
from langchain.chat_models import init_chat_model

llm = init_chat_model(
    model="qwen-max",
    model_provider="openai",       # 显式声明按 OpenAI 协议走，别让它猜
    base_url=base_url,
    api_key=api_key,
)
```

## base_url 的两个必查点

第一，**末尾那段路径不能省**。SDK 只会在你给的地址后面补 `/chat/completions`，不会替你补 `/v1`。所以漏了 `/v1`、或者接了阿里通义却漏了那条 `/compatible-mode/v1`，结果都是 404——报错看起来像"中转站挂了"，其实是你地址给短了。文档写什么就照抄什么。

第二，不写 `base_url` 时它默认走 OpenAI 官方，那连中转站自然是 401。而 `model` 只是个字符串，会原样发给对面，所以中转站支持哪些模型名以它的文档为准，不是 LangChain 说了算。

## 键名约定：决定你要不要自己读环境变量

| 模型类 | 会自动读的环境变量 |
|---|---|
| `ChatOpenAI` | `OPENAI_API_KEY`、`OPENAI_BASE_URL` |
| `ChatDeepSeek` | `DEEPSEEK_API_KEY`、`DEEPSEEK_API_BASE` |

用这些**约定名**，框架自己就找到了，代码里什么都不用传。用自己起的名字（比如 `DASHSCOPE_API_KEY`），框架不认识，必须自己 `os.getenv` 读出来再传参。

`init_chat_model` 不写 `model_provider` 时还会**按模型名里的厂商词去猜**。实测 `model="deepseek-flash"` 会被猜成 DeepSeek，于是它去找 `langchain-deepseek` 包：装了就正常用 `ChatDeepSeek`，没装就报 `ImportError: Initializing ChatDeepSeek requires the langchain-deepseek package`。那个报错的含义是"缺包"，不是"这么写不对"。

想确认它到底走了哪条路，加一行就够了。

```python
print(type(llm).__name__)   # ChatOpenAI？ChatDeepSeek？它会直接告诉你
```

## 判断一个模型能不能这么接

看它的文档里有没有三个信号：出现"兼容 OpenAI 接口"或 `base_url` 字样；示例代码是 `from openai import OpenAI`；模型名是个普通字符串。三个都中，直接用 `ChatOpenAI` 就行。

## 真的不兼容时：三档方案

按成本从低到高排。先找它有没有"OpenAI 兼容模式"，很多国产大模型两条协议都提供，比如通义的 compatible-mode，这条成本最低。

不行就换对应的官方包：`langchain-anthropic`、`langchain-google-genai` 这类，它们通常也支持 `base_url` 指向中转。

都没有，才是"LangChain 真的没集成"的情况。这时自己写一个 `BaseChatModel` 子类，实现 `_generate` 和 `_llm_type` 两处即可，`invoke`、`batch`、重试、回调这些框架会自动套在上面。

```python
from langchain_core.language_models.chat_models import BaseChatModel
from langchain_core.messages import AIMessage
from langchain_core.outputs import ChatGeneration, ChatResult

class MyRelayChat(BaseChatModel):
    @property
    def _llm_type(self):
        return "my-relay"

    def _generate(self, messages, stop=None, run_manager=None, **kwargs):
        text = call_my_api(messages)        # 按对方协议发 HTTP，自己实现
        return ChatResult(generations=[ChatGeneration(message=AIMessage(content=text))])
```

要流式再加一个 `_stream` 方法。这样包出来的模型同样能塞进 agent 和链里，因为大家按的都是同一个接口。这段是骨架示意，具体字段名以你所用版本的官方文档为准。

## 中转站特有的三个坑

**工具调用最容易残缺。** LangChain 的 agent 全靠它工作，中转站如果不支持请求里的 `tools` 参数，agent 会直接报错；更隐蔽的情况是模型永远不调工具，你会以为是自己的提示词写得烂。学习期先用普通对话把链路验证通，再上 agent，能把排查范围缩小一半。

**流式、usage 统计、结构化输出支持程度参差。** 这三样在中转站上表现不稳定，典型症状是"偶尔抽风"。

**中转站能看到你发出的所有 prompt 和 key。** 别用它跑敏感数据，也不要用主力 key。

## 一条判断技巧：先分清 401 还是 404

拿到报错先看类型。`401` 是"读到了 key 但不对"，说明链路已经通了，只是凭据问题，去查 key；`404` 是路径或模型名不对，去查 `base_url` 有没有抄全、模型名是否被支持；`Connection error` 才是域名或网络的问题。这一步能省掉一大半瞎试。
