# ============================================================
# 批量投稿脚本（Windows PowerShell 版）
# 用法：powershell -File scripts/import-docs.ps1 [-Dir samples] [-ServerUrl http://127.0.0.1:8088] [-ApiToken x]
# ============================================================

param(
    [string]$Dir = "samples",
    [string]$ServerUrl = "http://127.0.0.1:8088",
    [string]$ApiToken = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$dir = Join-Path $repoRoot $Dir
if (-not $ApiToken) { $ApiToken = Read-Host "请输入 API Token" }

$fail = $false
Get-ChildItem $dir -Filter "*.md" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -Encoding UTF8
    $body = @{ filename = $_.Name; content = $content; onConflict = "NEW_VERSION" }
    try {
        $resp = Invoke-RestMethod -Method POST -Uri "$ServerUrl/api/import/doc" `
            -Headers @{ "X-Api-Token" = $ApiToken } -ContentType "application/json" `
            -Body ($body | ConvertTo-Json -Depth 6)
        $d = $resp.data
        Write-Host "✓ $($_.Name) -> $($d.resolvedBy) v$($d.version) warnings=$($d.warnings.Count) reanchor[a=$($d.reanchor.active) s=$($d.reanchor.stale) o=$($d.reanchor.orphan)]"
        if ($d.resolvedBy -eq "INBOX" -or $d.warnings.Count -gt 0) {
            Write-Host "  ⚠ 需要修正：$($d | ConvertTo-Json -Depth 3)"
            $fail = $true
        }
    } catch {
        Write-Host "[FAIL] $($_.Name) $_"
        $fail = $true
    }
}

if ($fail) { throw "存在失败项，请修正后重投" }
Write-Host "全部投稿成功"
