@echo off
chcp 936 >nul
title AI 面试官平台 - 前端启动
setlocal

rem ============================================================
rem  AI 面试官平台 - 前端一键启动脚本
rem  母本文件：UTF-8 + LF
rem  部署文件：由 scripts\to-desktop.ps1 转成 GBK + CRLF 后放到桌面
rem  注意：不要直接用编辑器改桌面那份，会破坏编码
rem ============================================================

set "PROJECT=D:\AI interviewer project\AI interviewer\web"
set "PORT=5173"
set "URL=http://127.0.0.1:%PORT%/"
set "BACKEND_PORT=8081"

echo ====================================================
echo            AI 面试官平台   前端启动
echo ====================================================
echo.

rem ---------- 1. 检查项目目录 ----------
if not exist "%PROJECT%\package.json" (
    echo [错误] 找不到前端项目：
    echo        %PROJECT%
    echo.
    echo        请确认项目位置，或修改本脚本开头的 PROJECT 变量。
    echo.
    pause
    exit /b 1
)

rem ---------- 2. 检查 npm ----------
where npm >nul 2>nul
if errorlevel 1 (
    echo [错误] 没有找到 npm 命令。
    echo        请先安装 Node.js，并确保它已加入系统 PATH。
    echo.
    pause
    exit /b 1
)

cd /d "%PROJECT%"

rem ---------- 3. 检查后端是否已启动 ----------
set BACKEND_UP=
for /f "tokens=*" %%i in ('netstat -ano ^| findstr ":%BACKEND_PORT%" ^| findstr "LISTENING"') do set BACKEND_UP=1

if not defined BACKEND_UP (
    echo [提示] 后端 %BACKEND_PORT% 端口没有在监听。
    echo        前端页面能打开，但登录、上传简历、面试接口都会失败。
    echo        请先把后端的 Spring Boot 项目跑起来。
    echo.
) else (
    echo [提示] 后端 %BACKEND_PORT% 端口已就绪，可以正常面试。
    echo.
)

rem ---------- 4. 依赖检查 ----------
if exist "%PROJECT%\node_modules\vite" (
    echo [1/2] 依赖已就绪，跳过安装。
) else (
    echo [1/2] 首次运行，正在安装依赖，大约需要 1 分钟...
    echo.
    call npm install --no-audit --no-fund
    if errorlevel 1 (
        echo.
        echo [错误] 依赖安装失败，请检查网络后重新运行本脚本。
        echo.
        pause
        exit /b 1
    )
)

rem ---------- 5. 启动前端 ----------
echo.
echo [2/2] 正在启动前端开发服务器...
echo.
echo        访问地址：%URL%
echo        浏览器会在几秒后自动打开
echo        关掉本窗口，或者在这里按 Ctrl + C，即可停止服务
echo.
echo ----------------------------------------------------
echo.

rem 延迟 6 秒再打开浏览器，避免页面比服务先到
start "" /min cmd /c "ping -n 6 127.0.0.1 >nul & rundll32 url.dll,FileProtocolHandler %URL%"

call npm run dev

echo.
echo ----------------------------------------------------
echo 前端服务已停止。
echo.
pause
