# ============================================================
# learn-notes 从 notes-export/ 重建（Windows PowerShell 版）
# 只用 notes-export/（md + insights.json + uploads + manifest.json），不用 dump。
# V4 起 manifest 为任意深度递归目录树，文档 md front-matter 带 path/slugs。
# 用法：powershell -File scripts/restore-from-export.ps1 -ServerUrl http://127.0.0.1:8088 -ApiToken <token>
# ============================================================

param(
    [string]$ServerUrl = "http://127.0.0.1:8088",
    [string]$ApiToken = "",
    [string]$AdminUser = "",
    [string]$AdminPassword = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$export = Join-Path $repoRoot "notes-export"
if (-not $ApiToken) {
    $ApiToken = Read-Host "请输入 API Token"
}
if (-not $AdminUser) { $AdminUser = $env:APP_ADMIN_USERNAME }
if (-not $AdminPassword) { $AdminPassword = $env:APP_ADMIN_PASSWORD }
if (-not (Test-Path (Join-Path $export "manifest.json"))) {
    throw "找不到 $export\manifest.json，请先跑 sync-to-local.ps1"
}

# 先登录拿 JWT：目录/文档/见解接口需要 Bearer（X-Api-Token 只对 /api/import/** 与 /api/export/all 生效）
$jwt = ""
if ($AdminUser) {
    $loginBody = [System.Text.Encoding]::UTF8.GetBytes(
        (@{ username = $AdminUser; password = $AdminPassword } | ConvertTo-Json -Depth 3))
    try {
        $login = Invoke-RestMethod -Method POST -Uri "$ServerUrl/api/auth/login" `
            -ContentType "application/json; charset=utf-8" -Body $loginBody
        $jwt = $login.data.token
    } catch {
        throw "登录失败（需要 APP_ADMIN_USERNAME/APP_ADMIN_PASSWORD）：$_"
    }
}

function Invoke-Api($method, $path, $body = $null) {
    $headers = @{ }
    if ($jwt) { $headers["Authorization"] = "Bearer $jwt" }
    $args = @{ Method = $method; Headers = $headers; Uri = "$ServerUrl$path" }
    if ($body) {
        # 显式 UTF-8 字节，避免 PS 5.1 对中文按 ANSI 编码导致 JSON 解析失败
        $json = ($body | ConvertTo-Json -Depth 6)
        $args.Body = [System.Text.Encoding]::UTF8.GetBytes($json)
        $args.ContentType = "application/json; charset=utf-8"
    }
    $resp = Invoke-RestMethod @args
    # 解包统一响应体 {code,msg,data}
    return $resp.data
}

Write-Host "[1/4] 按 manifest 重建目录树（任意深度）..."
$manifest = [string](Get-Content (Join-Path $export "manifest.json") -Raw -Encoding UTF8) | ConvertFrom-Json
$tree = Invoke-Api "GET" "/api/catalog/tree"

# 把现有树拍平成 {id,parentId,slug} 便于按父目录幂等查重
$nodesIndex = New-Object System.Collections.ArrayList
function Index-Nodes($nodes, $parentId) {
    foreach ($n in $nodes) {
        [void]$nodesIndex.Add(@{ id = $n.id; parentId = $parentId; slug = $n.slug })
        Index-Nodes $n.children $n.id
    }
}
Index-Nodes $tree 0

function Find-NodeId($slug, $parentId) {
    $hit = $nodesIndex | Where-Object { $_.slug -eq $slug -and $_.parentId -eq $parentId } | Select-Object -First 1
    if ($hit) { return $hit.id }
    return $null
}

function Restore-Catalog($meta, $parentId) {
    $id = Find-NodeId $meta.slug $parentId
    if (-not $id) {
        $createBody = @{ parentId = $parentId; name = $meta.name; slug = $meta.slug; remark = $meta.remark; sortOrder = $meta.sortOrder }
        if ($parentId -eq 0 -and $meta.maxLevel) { $createBody.maxLevel = $meta.maxLevel }
        $created = Invoke-Api "POST" "/api/catalog" $createBody
        $id = $created.id
        [void]$nodesIndex.Add(@{ id = $id; parentId = $parentId; slug = $meta.slug })
    } elseif ($meta.remark) {
        Invoke-Api "PUT" "/api/catalog/$id" @{ remark = $meta.remark }
    }
    if ($meta.children) {
        foreach ($child in $meta.children) {
            Restore-Catalog $child $id
        }
    }
    return $id
}

foreach ($cat in $manifest.categories) {
    Restore-Catalog $cat 0
    Write-Host "  ✓ 分类 $($cat.name)"
}

Write-Host "[2/4] 导入文档..."
$mdFiles = Get-ChildItem $export -Recurse -Filter "*.md"
foreach ($f in $mdFiles) {
    $rel = $f.FullName.Substring($export.Length + 1).Replace("\", "/")
    $content = [string](Get-Content $f.FullName -Raw -Encoding UTF8)
    $body = @{ filename = $f.Name; content = $content; onConflict = "NEW_VERSION" }
    $r = Invoke-Api "POST" "/api/import/doc" $body
    Write-Host "  ✓ $rel -> $($r.resolvedBy) v$($r.version) warnings=$($r.warnings.Count)"
}

Write-Host "[3/4] 回灌见解..."
$jsonFiles = Get-ChildItem $export -Recurse -Filter "*.insights.json"
foreach ($f in $jsonFiles) {
    $rel = $f.FullName.Substring($export.Length + 1).Replace("\", "/")
    $parts = $rel.Split("/")
    # slugPath = 除文件名外整条目录链 slug（大类 → … → 叶目录）
    $dirs = $parts[0..($parts.Length - 2)]
    $docSlug = $parts[$parts.Length - 1].Replace(".insights.json", "")
    $insights = [string](Get-Content $f.FullName -Raw -Encoding UTF8) | ConvertFrom-Json
    $body = @{ slugPath = @($dirs); docSlug = $docSlug; insights = @($insights) }
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

function Count-Subtree($node) {
    $count = 0
    if ($node.children) {
        foreach ($c in $node.children) { $count += 1 + (Count-Subtree $c) }
    }
    return $count
}
$actualDirs = 0
foreach ($c in $userCats) { $actualDirs += Count-Subtree $c }

$allDocs = @()
$pageNo = 1
while ($true) {
    $page = Invoke-Api "GET" "/api/docs?size=100&page=$pageNo"
    if (@($page.items).Count -eq 0) { break }
    $allDocs += @($page.items)
    if ($allDocs.Count -ge $page.total) { break }
    $pageNo++
}
$annCount = 0
foreach ($d in $allDocs) {
    $detail = Invoke-Api "GET" "/api/docs/$($d.id)"
    $annCount += @($detail.annotations).Count
}
$actual = @{ categories = $userCats.Count; dirs = $actualDirs; docs = @($allDocs).Count; annotations = $annCount }
$ok = $true
foreach ($k in @("categories", "dirs", "docs", "annotations")) {
    $m = $actual[$k] -eq $manifest.counts.$k
    $ok = $ok -and $m
    Write-Host ("{0,-14} 期望 {1,5} 实际 {2,5} {3}" -f $k, $manifest.counts.$k, $actual[$k], $(if ($m) { "✓" } else { "✗ 不一致!" }))
}
if (-not $ok) { throw "计数不一致，恢复未完全成功" }
Write-Host "[OK] 恢复成功"
