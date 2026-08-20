# ============================================================
# learn-notes 从 notes-export/ 重建（Windows PowerShell 版）
# 只用 notes-export/（md + insights.json + uploads + manifest.json），不用 dump。
# 用法：powershell -File scripts/restore-from-export.ps1 -ServerUrl http://127.0.0.1:8088 -ApiToken <token>
# ============================================================

param(
    [string]$ServerUrl = "http://127.0.0.1:8088",
    [string]$ApiToken = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$export = Join-Path $repoRoot "notes-export"
if (-not $ApiToken) {
    $ApiToken = Read-Host "请输入 API Token"
}
if (-not (Test-Path (Join-Path $export "manifest.json"))) {
    throw "找不到 $export\manifest.json，请先跑 sync-to-local.ps1"
}

function Invoke-Api($method, $path, $body = $null) {
    $headers = @{ "X-Api-Token" = $ApiToken }
    $args = @{ Method = $method; Headers = $headers; Uri = "$ServerUrl$path" }
    if ($body) {
        $args.Body = ($body | ConvertTo-Json -Depth 10 -Compress)
        $args.ContentType = "application/json"
    }
    return Invoke-RestMethod @args
}

Write-Host "[1/4] 按 manifest 重建分类..."
$manifest = Get-Content (Join-Path $export "manifest.json") -Raw | ConvertFrom-Json
$tree = Invoke-Api "GET" "/api/catalog/tree"
foreach ($cat in $manifest.categories) {
    $node = $tree | Where-Object { $_.slug -eq $cat.slug } | Select-Object -First 1
    if (-not $node) {
        $node = Invoke-Api "POST" "/api/catalog" @{ parentId = 0; name = $cat.name; slug = $cat.slug; remark = $cat.remark; sortOrder = $cat.sortOrder }
    } elseif ($cat.remark) {
        Invoke-Api "PUT" "/api/catalog/$($node.id)" @{ remark = $cat.remark }
    }
    foreach ($topic in $cat.topics) {
        $exists = $node.children | Where-Object { $_.slug -eq $topic.slug }
        if (-not $exists) {
            Invoke-Api "POST" "/api/catalog" @{ parentId = $node.id; name = $topic.name; slug = $topic.slug; remark = $topic.remark; sortOrder = $topic.sortOrder }
        }
    }
    Write-Host "  ✓ 分类 $($cat.name)"
}

Write-Host "[2/4] 导入文档..."
$mdFiles = Get-ChildItem $export -Recurse -Filter "*.md"
foreach ($f in $mdFiles) {
    $rel = $f.FullName.Substring($export.Length + 1).Replace("\", "/")
    $content = Get-Content $f.FullName -Raw -Encoding UTF8
    $body = @{ filename = $f.Name; content = $content; onConflict = "NEW_VERSION" }
    $r = Invoke-Api "POST" "/api/import/doc" $body
    Write-Host "  ✓ $rel -> $($r.resolvedBy) v$($r.version) warnings=$($r.warnings.Count)"
}

Write-Host "[3/4] 回灌见解..."
$jsonFiles = Get-ChildItem $export -Recurse -Filter "*.insights.json"
foreach ($f in $jsonFiles) {
    $rel = $f.FullName.Substring($export.Length + 1).Replace("\", "/")
    $parts = $rel.Split("/")
    $insights = Get-Content $f.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $body = @{ categorySlug = $parts[0]; topicSlug = $parts[1]; docSlug = $parts[2].Replace(".insights.json", ""); insights = @($insights) }
    $r = Invoke-Api "POST" "/api/import/insights" $body
    Write-Host "  ✓ $rel -> created=$($r.created) skipped=$($r.skipped) stale=$($r.stale) orphan=$($r.orphan)"
}

Write-Host "[4/4] 复制 uploads 到 storage/uploads..."
$uploads = Join-Path $export "uploads"
if (Test-Path $uploads) {
    $target = Join-Path $repoRoot "storage\uploads"
    New-Item -ItemType Directory -Path $target -Force | Out-Null
    Copy-Item -Path (Join-Path $uploads "*") -Destination $target -Recurse -Force
    Write-Host "  uploads 已复制"
}

Write-Host ""
Write-Host "==== 期望 vs 实际 ===="
$actualTree = Invoke-Api "GET" "/api/catalog/tree"
$userCats = @($actualTree | Where-Object { $_.slug -ne "inbox" })
$userTopics = 0; foreach ($c in $userCats) { $userTopics += @($c.children).Count }
$page = Invoke-Api "GET" "/api/docs?size=100"
$annCount = 0
foreach ($d in $page.items) {
    $detail = Invoke-Api "GET" "/api/docs/$($d.id)"
    $annCount += @($detail.annotations).Count
}
$actual = @{ categories = $userCats.Count; topics = $userTopics; docs = @($page.items).Count; annotations = $annCount }
$ok = $true
foreach ($k in @("categories", "topics", "docs", "annotations")) {
    $m = $actual[$k] -eq $manifest.counts.$k
    $ok = $ok -and $m
    Write-Host ("{0,-14} 期望 {1,5} 实际 {2,5} {3}" -f $k, $manifest.counts.$k, $actual[$k], $(if ($m) { "✓" } else { "✗ 不一致!" }))
}
if (-not $ok) { throw "计数不一致，恢复未完全成功" }
Write-Host "[OK] 恢复成功"
