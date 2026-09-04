# 网页 AI 字幕路线（Chrome + web-access）启用方法

B 站 AI 字幕经常只在公开接口给出 `ai-zh` 但 `subtitle_url` 为空。真地址要带登录态的 WBI 接口
`x/player/wbi/v2`。bili-note 的解法：让"已登录 B 站且正开着视频页"的 Chrome 页面自己请求该接口
（`fetch_browser_ai_subtitles.py`），不读取 cookie。

本机实现（web-access 兼容 CDP 代理）：`C:\Users\123\.cache\web-access\`

```powershell
# 1) 起独立 Chrome（独立 profile，登录态与日常 Chrome 隔离）+ 3456 端口桥
powershell -ExecutionPolicy Bypass -File "$env:USERPROFILE\.cache\web-access\start.ps1"
#    在打开的 Chrome 窗口里登录 B 站一次（之后记住登录态）

# 2) 环境自检应显示：
#    Browser AI subtitles (Chrome + web-access): OK   web-access: reachable, targets>=1
py "<bili-note>\scripts\check_environment.py"

# 3) 打开目标视频页（或手动在 Chrome 里打开）
curl "http://127.0.0.1:3456/new?url=https://www.bilibili.com/video/BVxxxxx/"

# 4) 取 target id（url 含 /video/ 的那条）
curl http://127.0.0.1:3456/targets

# 5) 跑 AI 字幕抓取 / 一键流程带 browser 模式
py "<bili-note>\scripts\fetch_browser_ai_subtitles.py" --target <targetId> --out <dir>
# 或 run_bili_note.py 加 --subtitle-mode browser --browser-target <targetId>
```

登录探测（未登录 code=-101 / `isLogin:false`）：

```bash
curl -X POST "http://127.0.0.1:3456/eval?target=<id>" \
  --data "(async()=>{const r=await fetch('https://api.bilibili.com/x/web-interface/nav',{credentials:'include'});const j=await r.json();return {code:j.code,isLogin:!!(j.data&&j.data.isLogin)};})()"
```

收尾用 `stop.ps1`（只停该独立 profile 的 Chrome 与桥）。桥只绑回环 127.0.0.1。
