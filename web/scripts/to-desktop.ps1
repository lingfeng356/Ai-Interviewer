# 把 scripts\start-frontend.bat（UTF-8 + LF 母本）转换成
# GBK + CRLF，输出到桌面，供双击运行。
#
# 为什么需要这个脚本：
#   1. .bat 里有中文，必须用 GBK（代码页 936）编码，否则 cmd 显示乱码，
#      甚至会把 REM 注释里的中文当成命令去执行；
#   2. cmd 的批处理解析器要求 CRLF 换行，LF 会把行切碎；
#   3. .bat 不能带 UTF-8 BOM，否则第一行 @echo off 会变成非法命令。
#   同时避免手改桌面那份文件——编辑器会按 UTF-8 读写，把 GBK 中文写坏。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File .\scripts\to-desktop.ps1
#   可选参数：-Source <母本路径> -Target <输出路径>

param(
    [string]$Source,
    [string]$Target
)

if (-not $Source) {
    $Source = Join-Path $PSScriptRoot 'start-frontend.bat'
}
if (-not $Target) {
    $Target = Join-Path ([Environment]::GetFolderPath('Desktop')) '启动前端.bat'
}

if (-not (Test-Path -LiteralPath $Source)) {
    Write-Error "找不到母本文件：$Source"
    exit 1
}

# 读母本（UTF-8 无 BOM）
$text = [System.IO.File]::ReadAllText($Source, [System.Text.Encoding]::UTF8)

# 换行统一成 CRLF：先全部归一成 LF，再统一替换
$text = $text.Replace("`r`n", "`n").Replace("`r", "`n").Replace("`n", "`r`n")

# 以 GBK 写出（无 BOM）
$gbk = [System.Text.Encoding]::GetEncoding(936)
[System.IO.File]::WriteAllText($Target, $text, $gbk)

Write-Host "已生成：$Target"
Write-Host ("编码：GBK(936)  换行：CRLF  大小：" + (Get-Item -LiteralPath $Target).Length + " 字节")
