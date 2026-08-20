# ============================================================
# learn-notes 同步回本机脚本（R35，Windows PowerShell 执行）
# 把服务器最新导出物拉回本机：
#   - md + insights.json → 本机 <仓库>/notes-export/（进 git，主恢复路径）
#   - db dump + storage 归档 → 仓库外 F:\deespeekharness\learn-notes-backup\
# 用法：powershell -File scripts/sync-to-local.ps1
# ============================================================

param(
    [string]$ServerHost = "127.0.0.1",
    [string]$ServerUser = "root",
    [string]$RemoteDir = "/opt/learn-notes/backup",
    [string]$LocalBackupDir = "F:\deespeekharness\learn-notes-backup",
    [string]$Scp = "scp",
    [string]$Ssh = "ssh"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$exportDir = Join-Path $repoRoot "notes-export"

Write-Host "[1/5] 获取服务器最新导出包列表..."
$latest = (& $Ssh "${ServerUser}@${ServerHost}" "ls -1t $RemoteDir/export | head -1").Trim()
if (-not $latest) { throw "服务器上没有导出包（先跑 backup.sh）" }
Write-Host "    最新导出包: $latest"

Write-Host "[2/5] 拉取导出包并解压到 notes-export/（先清空保证是全量快照）..."
$zip = Join-Path $env:TEMP $latest
& $Scp "${ServerUser}@${ServerHost}:$RemoteDir/export/$latest" $zip
if (Test-Path $exportDir) { Remove-Item $exportDir -Recurse -Force }
New-Item -ItemType Directory -Path $exportDir -Force | Out-Null
Expand-Archive -Path $zip -DestinationPath $exportDir -Force
Remove-Item $zip

Write-Host "[3/5] 拉取 db dump 与 storage 归档到仓库外目录..."
New-Item -ItemType Directory -Path $LocalBackupDir -Force | Out-Null
$dbLatest = (& $Ssh "${ServerUser}@${ServerHost}" "ls -1t $RemoteDir/db | head -1").Trim()
$stLatest = (& $Ssh "${ServerUser}@${ServerHost}" "ls -1t $RemoteDir/storage | head -1").Trim()
if ($dbLatest) { & $Scp "${ServerUser}@${ServerHost}:$RemoteDir/db/$dbLatest" (Join-Path $LocalBackupDir $dbLatest) }
if ($stLatest) { & $Scp "${ServerUser}@${ServerHost}:$RemoteDir/storage/$stLatest" (Join-Path $LocalBackupDir $stLatest) }

Write-Host "[4/5] git 提交 notes-export/（内容无变化则跳过）..."
$manifest = Get-Content (Join-Path $exportDir "manifest.json") -Raw | ConvertFrom-Json
Set-Location $repoRoot
& git add notes-export
$changed = (& git status --porcelain notes-export)
if ($changed) {
    & git commit -m "backup: notes snapshot $(Get-Date -Format 'yyyy-MM-dd')"
    Write-Host "    已提交"
} else {
    Write-Host "    内容无变化，跳过提交"
}

Write-Host "[5/5] 本次快照计数："
Write-Host "    分类 $(($manifest.counts.categories)) / 小方向 $(($manifest.counts.topics)) / 文档 $(($manifest.counts.docs)) / 见解 $(($manifest.counts.annotations)) / 图片 $(($manifest.counts.images))"
Write-Host "完成。"
