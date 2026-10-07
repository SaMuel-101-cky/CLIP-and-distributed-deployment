# AutoDL 远程模型卸载实验操作文档

## 1. 实验范围与结论边界

本实验只把 `CLIP_model/server.py` 部署到 AutoDL；以下服务始终运行在本机：

- Java Spring Boot 后端
- MySQL
- `CLIP_model/client.py`
- 图片文件与 Chroma（如测试路径需要它）

本机 `client.py` 通过 HTTP 调用 AutoDL 上的 `server.py`，将启用的 CLIP 组件卸载到远程 GPU。通信协议保持 HTTP，不使用 WebSocket。

```text
本机：Java + MySQL + client.py  -- HTTP / base64 tensor -->  AutoDL：server.py + GPU
```

实验的目标是比较：

1. **本地基线**：所有 `OFFLOAD_*=false` 时的端到端推理耗时。
2. **远程卸载**：开启指定 `OFFLOAD_*` 后的端到端耗时、每个远程 RPC 的 RTT，以及 AutoDL 端的 GPU 推理耗时。

若本机与 AutoDL 的 GPU 型号不同，结论只能表述为“当前两台机器上的端到端服务性能比较”，不能把差异完全归因于网络卸载。每组结果必须记录两端 GPU 型号、CUDA/PyTorch 版本和网络出口。

## 2. 先提交代码到 GitHub

当前远程仓库：

```text
https://github.com/SaMuel-101-cky/CLIP-and-distributed-deployment.git
```

先在本机 `D:\CLIP` 检查状态：

```powershell
cd D:\CLIP
git status
```

不要使用 `git add .`。`results/` 是实验输出，应保留在本机或另行归档，不应随代码提交。以下命令只加入本次日志改动和本文档：

```powershell
git add CLIP_model/client.py CLIP_model/server.py CLIP_model/utils/setup.py CLIP_model/utils/speed_measurement.py CLIP_model/tests/test_inference_logging.py docs/autodl-experiment-guide.md
git commit -m "feat: add per-inference logs and AutoDL experiment guide"
git push origin main
```

提交前再次确认 `.env`、日志、`chroma_data/`、`results/`、图片上传目录和模型权重没有被暂存：

```powershell
git status
git diff --cached --name-only
```

`ViT-L-14.pt` 当前不在 Git 工作树索引中，不能依赖 `git clone` 获得它。请使用可信的模型权重来源、对象存储或 AutoDL 网盘单独传输；校验文件大小和来源后，将其放入远程仓库的 `CLIP_model/ViT-L-14.pt`。

## 3. 创建 AutoDL 实例

1. 选择 Linux + CUDA/PyTorch 基础镜像，确保 CUDA 与 PyTorch 可用。
2. 选择单卡 GPU 即可开始；显存应留有模型、激活和输入批次的余量。为使计算比较更公平，优先记录并尽可能匹配本机 GPU 型号。
3. 为 `6006` 配置 **TCP** 自定义服务映射。当前 Python 客户端使用 `http://host:port/...`，因此不要把仅 HTTPS 的地址直接填入 `SERVER_IP`/`SERVER_PORT`。
4. 记下映射后的公网 `主机名/IP` 与 `端口`，它们将在本机的 `.env` 中使用。

AutoDL 仅允许映射特定端口；官方文档说明每个实例的 `6006`、`6008` 可通过“自定义服务”获得公网地址。参见 [开放端口说明](https://www.autodl.com/docs/port/)。实例本地数据盘性能较好但不是可靠备份；重要权重、实验记录和结果应同步到本地或文件存储。参见 [实例数据保留说明](https://www.autodl.com/docs/instance_data/) 和 [文件存储说明](https://www.autodl.com/docs/fs/)。

## 4. 在 AutoDL 上部署 `server.py`

以下命令在 AutoDL 终端执行。示例目录使用本地数据盘；可替换为你的实际路径。

```bash
cd /root/autodl-tmp
git clone https://github.com/SaMuel-101-cky/CLIP-and-distributed-deployment.git clip-distributed
cd clip-distributed/CLIP_model
```

### 4.1 创建 Linux 环境

仓库的 `requirements.txt` 已改为 Linux/AutoDL 可安装的运行时清单，固定 PyTorch 2.5.1 的 CUDA 12.1 轮子。先创建环境并直接安装：

```bash
conda create -n clip-server python=3.10 -y
conda activate clip-server

pip install --upgrade pip
pip install -r requirements.txt
```

CUDA 12.1 轮子要求宿主机 NVIDIA 驱动兼容。若 AutoDL 镜像的驱动不支持 CUDA 12.1，必须选择兼容镜像或调整 `requirements.txt` 中的 PyTorch CUDA 轮子版本；不要退回到 CPU 版 PyTorch 后继续做 GPU 性能实验。

验证远端 CUDA：

```bash
python -c "import torch; print(torch.__version__); print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'no cuda')"
nvidia-smi
```

输出必须包含 `True` 和实际 GPU 名称。若为 `False`，先修复 PyTorch/CUDA 环境，不要开始性能实验。

### 4.2 放置权重与配置密钥

将 `ViT-L-14.pt` 放到当前目录：

```bash
ls -lh ViT-L-14.pt
```

生成一个随机卸载密钥；不要提交该密钥，也不要把它写入 Git：

```bash
python -c "import secrets; print(secrets.token_urlsafe(32))"
```

创建远端 `CLIP_model/.env`：

```bash
cat > .env <<'EOF'
SERVER_PORT=6006
OFFLOAD_TOKEN=<替换为刚生成的随机密钥>
OFFLOAD_MAX_PAYLOAD_BYTES=67108864
EOF
```

远端与本机使用相同的 `OFFLOAD_TOKEN`。`server.py` 不连接本机 Java 或 MySQL，也不需要本机的 `AI_CALLBACK_TOKEN`。

### 4.3 启动远端模型服务

```bash
conda activate clip-server
cd /root/autodl-tmp/clip-distributed/CLIP_model
nohup python server.py > server-stdout.log 2>&1 &
echo $!
```

等待模型加载后检查进程和端口：

```bash
ps -ef | grep '[p]ython server.py'
ss -lnt | grep 6006
tail -n 50 server-stdout.log
```

远端推理日志会按一次 RPC 一份文件写入 `CLIP_model/logs/YYYY_MM_DD_<uuid>.log`。持续观察 GPU 时可使用：

```bash
watch -n 1 nvidia-smi
```

## 5. 配置本机 `client.py`

本机 Java、MySQL 和 `client.py` 的启动方式维持现有本地运行手册。仅修改 `CLIP_model/.env` 中与远程卸载有关的值：

```dotenv
# AutoDL “自定义服务”给出的 TCP 主机和端口
SERVER_IP=<AUTODL_TCP_HOST>
SERVER_PORT=<AUTODL_TCP_PORT>
OFFLOAD_TOKEN=<与远端完全相同的随机密钥>
OFFLOAD_REQUEST_TIMEOUT_SECONDS=30

# 必须是真实推理；smoke 模式不能用于性能结论
CLIP_SMOKE_MODE=false

# 基线实验中全部 false；远程实验仅开启要测的组件
OFFLOAD_VISUAL_ATTN=false
OFFLOAD_VISUAL_MLP=false
OFFLOAD_TEXT_ATTN=false
OFFLOAD_TEXT_MLP=false
OFFLOAD_VISUAL_CONV=false
OFFLOAD_VISUAL_PROJ=false
OFFLOAD_TEXT_PROJ=false
OFFLOAD_COMPLETE_ENCODER=false
OFFLOAD_COS_SIM=false
OFFLOAD_VISUAL_ENCODER=false
OFFLOAD_TEXT_ENCODER=false
```

本机与远端是两个独立进程，因此两端的 `SERVER_PORT` 可以不同：远端监听实例内 `6006`，本机填写 AutoDL 映射后的公网端口。

先从本机测试远端服务的连通性；服务端已要求 `X-Offload-Token`：

```powershell
$token = '<与 .env 中 OFFLOAD_TOKEN 相同>'
Invoke-WebRequest -Headers @{ 'X-Offload-Token' = $token } -Uri 'http://<AUTODL_TCP_HOST>:<AUTODL_TCP_PORT>/health'
```

若失败，依次检查：AutoDL 实例是否运行、`6006` 是否为 TCP 映射、映射主机/端口是否正确、远端 `server.py` 是否监听、令牌是否一致，以及本机网络是否能访问该公网地址。

本机服务运行：

```powershell
cd D:\CLIP\CLIP_model
D:\Anaconda\envs\CLIP\python.exe client.py
```

Java 后端仍按 `docs/local-runbook.md` 在本机启动，`AI_SERVICE_BASE_URL` 保持为 `http://localhost:5000`，因为它调用的是本机的 `client.py`，不是 AutoDL。

## 6. 实验设计：先基线，再逐级卸载

### 6.1 保持不变的条件

- 使用同一批固定图片、固定文本标签、固定批大小和固定请求次数。
- 在每组正式测量前做至少 5 次预热；模型加载、CUDA 初始化和首轮缓存不计入统计。
- 每个配置至少记录 30 次请求，报告中位数、p95、均值和标准差。
- 每轮实验前记录本机与远端的 GPU 型号、驱动、CUDA、PyTorch、CPU、内存、网络位置和提交 SHA。
- 不在测量期间改动 `.env` 之外的代码；每种配置单独保存环境变量快照。

### 6.2 配置顺序

1. **L0 本地基线**：所有 `OFFLOAD_*=false`。
2. **R1 粗粒度**：仅 `OFFLOAD_COMPLETE_ENCODER=true`，观察一次大张量传输是否值得。
3. **R2 中粒度**：分别测试 `OFFLOAD_VISUAL_ENCODER`、`OFFLOAD_TEXT_ENCODER`、`OFFLOAD_VISUAL_PROJ`、`OFFLOAD_TEXT_PROJ`、`OFFLOAD_VISUAL_CONV`、`OFFLOAD_COS_SIM`。
4. **R3 细粒度**：再测试 Attention/MLP。细粒度会产生大量 HTTP 请求，通常最容易被 RTT、序列化和 base64 放大开销拖慢。

每次只改变一组卸载开关。若要测纯模型服务，优先调用本机 `POST /predict` 并使用固定样本；不要把上传文件、MySQL 写入、Chroma 回填、任务排队混入该基线。后端完整链路可以作为单独的集成实验记录。

## 7. 重点关注的耗时与读日志方法

本次新增日志文件名格式为：

```text
logs/YYYY_MM_DD_<uuid>.log
```

本机每次完整预测/后台 AI 任务一份日志；AutoDL 每次卸载 RPC 也会生成独立日志。二者 UUID 不相同，可通过实验批次、端点名称和相近时间关联。

| 指标 | 来源 | 含义与解读 |
|---|---|---|
| 端到端耗时 | 本机压测脚本或请求开始/结束时间 | 用户实际等待时间；用于判断远程卸载是否有收益。 |
| 本地 `infer_ms` | 本机日志 | 未被卸载组件的纯计算时间；计时函数会在 CUDA 下同步，适合比较同机运行。 |
| 远端 `infer_ms` | AutoDL 日志 | AutoDL GPU 内真正执行该模块的时间。 |
| `[endpoint] ... rtt=...ms type=传输` | 本机日志 | 一次 HTTP 调用的往返包络时间；包含网络、服务端排队/解码/编码/推理等，不等于纯网络。 |
| `one_way_ms` | AutoDL 日志 | 依赖两台机器的系统时钟同步；未确认 NTP 同步前只能作诊断参考，不能作为严谨的单程网络延迟结论。 |
| `nvidia-smi` | 两端终端 | GPU 利用率、显存、功耗；低利用率但高 RTT 往往表明传输或小请求数量是瓶颈。 |

重点查看下面的模块分段日志：

- `encoder_blocks`：视觉/文本编码器主体，通常是主要计算量。
- `visual_projection`、`text_projection`：两端投影层，日志前有真实空行，方便定位。
- `attention`、`mlp`：如果细粒度卸载，必须同时统计调用次数；单次很快不代表累计开销低。
- `vision_conv`、`cos_sim`：通常计算较小，远程调用常常不划算，需用数据验证。

当前代码会在内存中计算 `serialize_ms`、`deserialize_ms` 和 payload 字节数，但常规服务日志没有持久化这些细分字段。若 RTT 成为瓶颈而无法判断原因，再单独为 `OffloadHandler` 接入指标落盘；不要在实验前把这部分遗漏误解释为 GPU 慢。

## 8. 结果记录模板

每一行代表同一数据集和同一批大小下的一种配置：

| 配置 ID | Git SHA | 本机 GPU | AutoDL GPU | 开启的 OFFLOAD | 预热/样本数 | p50 E2E ms | p95 E2E ms | 远端 infer_ms 汇总 | RPC 次数 | RTT p50/p95 ms | 结论 |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---|
| L0 | `<sha>` | `<GPU>` | `-` | 全 false | 5/30 |  |  | 0 | 0 | 0 | 本地基线 |
| R1 | `<sha>` | `<GPU>` | `<GPU>` | `COMPLETE_ENCODER` | 5/30 |  |  |  |  |  |  |

结论应按以下顺序书写：

1. 远程配置相对 L0 的端到端加速比（或变慢比例）。
2. 优势来自远端 GPU 计算、还是被 RTT/RPC 次数抵消。
3. 适合卸载的粒度，以及不适合卸载的粒度。
4. 硬件差异、时钟不同步、网络波动、冷启动是否限制了结论的外推范围。

## 9. 实验结束后的动作

1. 复制本机与远端的相关日志、环境快照和结果表到可靠存储。
2. 停止远端进程：`kill <server.py_PID>`。
3. 不继续实验时，在 AutoDL 控制台关机以停止 GPU 计费。
4. 将结果提交到独立的实验目录或外部存储；不要提交 `.env`、令牌、模型权重或大体积原始日志。
