---
path: [agent学习, 环境, .env]
slugs: [agent-learning, environment, dotenv]
title: .env 从零讲起：密钥放哪，框架怎么读到它
slug: dotenv-basics
tags: [环境变量, uv, LangChain, 新手]
summary: 从"为什么不能把 key 写进代码"讲起，说清 .env 的格式、Python 为什么不会自动读它、在 uv 项目里加载它的两种方式，以及 LangChain 里 provider 约定的环境变量名与中转站接入的报错对照。
order: 10
spec_version: v2
---

# .env 从零讲起：密钥放哪，框架怎么读到它

这篇笔记回答一个很具体的问题：项目里要用到大模型的 API key、数据库密码这类值，它们应该放在哪里、代码又是怎么读到它们的。后半段把 LangChain 接模型时最容易踩的几个坑也一并收进来。

## 为什么不能把 key 直接写进代码

大模型的 API key 是一串钥匙，你调模型就是拿它证明"是我在调"。这串 key 不能硬编码在代码里，有两个原因。

一是你迟早会把代码提交到 git，写死在代码里的 key 等于公开了。别人拿你的 key 可以刷你的额度、花你的钱，这是新手最常见的翻车方式。

二是别人 clone 你的项目时没有你的 key，代码里写死的那个值对他毫无意义，项目直接跑不起来。所以"只属于你、不该公开、且每台机器可能不同"的值，必须从代码里挪出去。

## .env 就是一个纯文本的键值表

挪出去的目标就是一个叫 `.env` 的文件，格式非常简单，一行一个键值对。

```text
OPENAI_API_KEY=sk-xxxxxxxxxxxxxxxx
MODEL=gpt-4o-mini
DEBUG=true
```

它**不是** Python 或 uv 的功能，也没有任何魔法。谁读它、什么时候读，完全取决于你用的工具，后面会讲。

配套还会有一个 `.env.example` 文件，键名和 `.env` 完全一样，但值留空或写成占位符。它**要**提交进 git，作用是告诉别人"这个项目需要配哪几项"。而 `.env` 自己要写进 `.gitignore`，永远不提交。

## Python 不会自动读它

这是最容易误解的一点：`.env` 文件放在那里只是一个普通文本文件，Python 进程不会主动去读它。你必须显式地做一件事，有二选一的两条路。

第一条是在代码里装并用 `python-dotenv`：

```python
import os
from dotenv import load_dotenv

load_dotenv()                          # 把 .env 里的键值填进进程环境变量
api_key = os.getenv("OPENAI_API_KEY")  # 之后才能取到值
```

第二条是不改代码，让 uv 在启动进程时把值注入进去：

```bash
uv run --env-file .env main.py
```

实测过这两条路的差别：同一个脚本，不加 `--env-file` 时 `os.getenv` 拿到的是 `None`，加上之后才拿到值。因为 `uv run` 默认**不会**加载 `.env`，别以为文件放那儿就生效了。

学习项目推荐第一条路。它把"加载"这件事写进了代码，不管你用什么方式运行都能生效；而第二条依赖"每次运行都记得加参数"，忘一次就静默失效。

## 在 uv 项目里的完整步骤

`.env` 本身不区分语言，但要让它在 uv 项目里真正生效，一共四步。

```bash
mkdir myproj && cd myproj
uv init .
uv add python-dotenv
uv run main.py
```

`uv init` 建骨架，`uv add` 装依赖并记进 `pyproject.toml`，`uv run` 用项目自己的环境跑脚本。`uv add python-dotenv` 这一步不能省：**langchain 之类的框架并不把 `python-dotenv` 当依赖**，不装就会在 `from dotenv import load_dotenv` 那里报 `ModuleNotFoundError`。

有个实测出来的坑值得单独写一行：`uv init` **不会**给你生成 `.gitignore`。所以第一件事是自己写好它，第一行就放 `.env`，否则第一次 `git add .` 就把密钥提交上去了。

## 键名决定你要不要自己读

到了 LangChain 这一步，会出现两种看起来完全不同、其实同源的写法。区别只有一个：**你用的键名是不是框架约定好的名字**。

| 模型类 | 会自动读的环境变量 |
|---|---|
| `ChatOpenAI` | `OPENAI_API_KEY`、`OPENAI_BASE_URL` |
| `ChatDeepSeek` | `DEEPSEEK_API_KEY`、`DEEPSEEK_API_BASE` |

用约定名（`OPENAI_*`、`DEEPSEEK_*`），框架自己就找到了，代码里什么都不用传。用你自己起的名字（比如 `DASHSCOPE_API_KEY`），框架不认识，就必须自己 `os.getenv` 读出来再传参。

```python
api_key = os.getenv("DASHSCOPE_API_KEY")   # 自起的名字，必须自己读
base_url = os.getenv("DASHSCOPE_BASE_URL")

model = init_chat_model(
    model="qwen-max",
    model_provider="openai",
    base_url=base_url,
    api_key=api_key,
)
```

顺带说清一个常被教程讲绕的点："非支持模型无法自动加载环境变量"这句话，说的不是 `.env` 不会自动加载，而是"键名不是约定名，所以框架读不到"。

## provider 是猜出来的

`init_chat_model` 不写 `model_provider` 时，LangChain 会**按模型名里的厂商词去猜**。实测：`model="deepseek-flash"` 会被猜成 DeepSeek，于是它去找 `langchain-deepseek` 这个包；装了就正常用 `ChatDeepSeek`，没装就报 `ImportError: Initializing ChatDeepSeek requires the langchain-deepseek package`。那个 ImportError 的含义是"缺包"，不是"你这么写不对"。

想确认它到底走了哪条路，加一行就够了。

```python
print(type(model).__name__)   # ChatOpenAI？ChatDeepSeek？它会直接告诉你
```

## 接中转站：改地址就行

中转站能用的原因不是 LangChain 集成了它，而是中转站主动说 OpenAI 的"方言"：同样的请求路径、同样的字段名、同样的鉴权头。所以 `langchain-openai` 这种按协议编程的客户端不需要任何改造，**只换地址**。

```python
model = init_chat_model(
    model="deepseek-chat",
    model_provider="openai",
    base_url="https://your-relay.com/v1",
    api_key="sk-xxx",
)
```

`base_url` 替换掉的是官方的 `https://api.openai.com/v1`，两件事必须注意。第一，**地址里的 `/v1` 通常不能省**，SDK 只会在你给的地址后面补 `/chat/completions`，不会替你补 `/v1`。第二，阿里通义走的是 `https://dashscope.aliyuncs.com/compatible-mode/v1`，那一整段路径都要照抄文档，少一段就是 404。

不写 `base_url` 时它默认走 OpenAI 官方，这时连中转站会得到 401。而 `model` 只是个字符串，会原样发给对面，所以中转站支持哪些模型名以它的文档为准。

## 报错对照表

下面这张表全部实测过，拿到报错先对号入座，比反复改代码快得多。

| 你看到的报错 | 真正的原因 |
|---|---|
| `Missing credentials. Please pass an api_key` | `.env` 根本没被加载，key 是 `None` |
| `OpenAIAuthenticationError: Error code: 401` | 加载成功了，只是 key 本身错、过期或复制漏字符 |
| `OpenAIModelNotFoundError: Error code: 404` | `base_url` 写错，通常是漏了 `/v1` 或 `compatible-mode` |
| `OpenAIConnectionError: Connection error.` | 域名写错或网络不通 |
| `No module named 'dotenv'` | 没装 `python-dotenv` |
| `No module named 'langchain_openai'` | 没装 `langchain-openai` |

其中最好用的一个判断技巧：看报错是 `Missing credentials` 还是 `401`。前者是"没读到"，去查 `load_dotenv` 和键名拼写；后者是"读到了但不对"，去查 key 本身。这一步能省掉一大半瞎试。

## 两个容易踩的细节

`load_dotenv()` 找 `.env` 的位置，是按**调用它的那个文件所在目录**逐级往上找，不是按当前工作目录。实测把它放在 `src/pkg/` 下调用，从项目根目录运行也能找到项目的 `.env`。但它取的是往上找到的**第一个** `.env`，多层目录里各放一份时容易读到意外的那个，这时用 `load_dotenv(Path(__file__).parent / ".env")` 指定路径最稳。

`load_dotenv()` 默认不覆盖已存在的环境变量。这意味着 Docker 等外部注入的真实环境变量优先于 `.env`，这是有意为之的好设计，不要改成覆盖。另外，`.env` 里的值一律是字符串，不要写 `export`，值里有空格要加引号，`DEBUG=true` 取出来是 `"true"` 而不是 `True`。

## 小结

`.env` 只是一个放"因人而异的值"的文本文件，读它的人是你的代码或你的运行命令。整条链路只需记住三件事：**Python 不会自动读它，要么 `load_dotenv()` 要么 `--env-file`；键名是框架约定名时不用自己读，自起的名字必须自己读；接中转站只需改 `base_url`，而地址要按文档抄全。**
