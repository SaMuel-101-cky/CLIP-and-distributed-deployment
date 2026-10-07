# CLIP 图文系统使用指南

本文说明当前项目的启动方式、图片目录约定，以及本地推理和远程卸载模式的区别。

## 先理解当前前端的定位

前端页面读取的图片目录是：

```text
D:\CLIP\photo_resources\test
```

你应当先把准备测试的 `jpg`、`jpeg`、`png`、`webp` 或 `bmp` 图片复制到该目录。页面加载后会列出这些图片；可以选择一张或多张，输入一行或多行文本，再发起一次 CLIP 运行。

浏览器不能直接安全地读取电脑任意路径，因此当前页面采用“把测试图片放到固定目录，再从页面选择”的方式。它不是 Java 正式业务系统的上传页面，也不需要在页面里填写 Windows 路径。

> 当前前端仍是**性能 benchmark 控制台**：它会执行 CLIP 推理并显示样本数、平均耗时、P95 和成功率，但目前不会把每张图片对应的文本分数、排序或缩略图结果显示出来。也就是说，它可以用于比较本地与远程卸载的性能，尚不是完整的图文检索结果页。

## 启动前的准备

1. 确认模型权重在 `D:\CLIP\CLIP_model\ViT-L-14.pt`。
2. 将测试图片放入 `D:\CLIP\photo_resources\test`。
3. 使用本机 Python 环境：`D:\Anaconda\envs\CLIP\python.exe`。
4. 前端依赖只需首次安装一次：在 `CLIP_frontend` 中执行 `npm install`。

## 模式一：全部本地推理

这是最适合第一次验证的模式，不需要 Java、MySQL 或 AutoDL。

先启动模型服务：

```powershell
cd D:\CLIP\CLIP_model
Remove-Item Env:\SERVER_IP -ErrorAction SilentlyContinue
Remove-Item Env:\OFFLOAD_TOKEN -ErrorAction SilentlyContinue
D:\Anaconda\envs\CLIP\python.exe client.py
```

模型服务成功启动后，会监听本机 `http://127.0.0.1:5000`。然后另开一个 PowerShell 启动前端：

```powershell
cd D:\CLIP\CLIP_frontend
npm run dev
```

浏览器打开 Vite 输出的地址，通常是：

```text
http://127.0.0.1:5173
```

页面操作：

1. `PRESET` 选择 `LOCAL_ALL`。
2. 在 `IMAGES · photo_resources/test` 中选中一张或多张图片。
3. 在 `TEXT QUERIES` 中输入文本；一行一条，例如 `a cat`、`a dog`。
4. `AUTODL HOST` 留空。
5. 点击 `RUN BENCHMARK`。

若浏览器开发终端出现 `http proxy error: /api/images`，先检查模型服务窗口是否仍在运行；这代表前端无法连接本机 `5000` 端口，并不代表图片目录本身有问题。

## 模式二：部分组件卸载到 AutoDL

该模式需要两个模型进程：本机运行 `client.py`，AutoDL 运行 `server.py`。前端仍只需要运行一次。

### 1. 在 AutoDL 上启动远程模型服务

把 `CLIP_model`、模型权重和依赖准备在 AutoDL，设置一个随机且非空的共享令牌：

```bash
cd /path/to/CLIP_model
export OFFLOAD_TOKEN='请替换为随机长字符串'
export SERVER_PORT='5000'
python server.py
```

AutoDL 的安全组、防火墙或端口映射必须允许本机访问该端口。不要公开 Java、MySQL、Chroma 或 Prometheus 指标端口。

### 2. 在本机启动带远程配置的模型服务

将 `<AUTODL_HOST>` 替换为你可以从本机访问的 AutoDL 域名或 IP；令牌必须与远程服务完全一致。

```powershell
cd D:\CLIP\CLIP_model
$env:SERVER_IP='<AUTODL_HOST>'
$env:SERVER_PORT='5000'
$env:OFFLOAD_TOKEN='与 AutoDL 相同的随机长字符串'
D:\Anaconda\envs\CLIP\python.exe client.py
```

再照“模式一”启动前端。

### 3. 在页面选择卸载策略

| 页面预设 | 图像编码 | 文本编码 | 用途 |
| --- | --- | --- | --- |
| `LOCAL_ALL` | 本机 | 本机 | 本机基线，AutoDL 地址留空。 |
| `REMOTE_ALL` | AutoDL | AutoDL | 比较完整远程编码的端到端开销。 |
| `TEXT_LOCAL_IMAGE_REMOTE` | AutoDL | 本机 | 测量将图片编码部分卸载到远程的效果。 |
| `TEXT_REMOTE_IMAGE_LOCAL` | 本机 | AutoDL | 测量将文本编码部分卸载到远程的效果。 |

远程模式下，在 `AUTODL HOST` 输入同一个 `<AUTODL_HOST>`。留空时会使用本机 `SERVER_IP` 环境变量作为默认远程地址。

## Java 与 MySQL 什么时候需要启动

对当前 benchmark 前端而言，不需要启动 Java 或 MySQL：页面直接调用本机模型服务的 `/api/*` 路由，运行记录只保留在当前 Python 进程内。

只有在你测试正式业务流程时才需要启动它们，例如：Java 的图片上传、用户登录、分类任务、以文搜图任务、结果持久化和 Chroma embedding 回写。正式业务链路由 Java 创建任务，Python 模型回调 Java 保存结果。

## 当前页面与目标检索页面的差距

你的目标应当是：选中目录中的图片 + 输入文本后，页面展示图片缩略图、每个文本的相似度、按分数排序的结果；本地与远程预设只改变计算位置，不改变结果展示。

当前 benchmark 页面已经具备“选图、输入文本、选择本地/远程模式、发起真实推理”的入口，但只展示性能汇总，不展示匹配结果。后续应把接口响应扩展为包含图片名、文本、相似度和排名，再由前端显示检索结果；这样它才是完整的 CLIP 图文匹配/以文搜图界面。
