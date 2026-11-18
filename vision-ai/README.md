# 图像识别辅助服务（vision-ai）

`vision-ai` 是码跃 OJ 的独立 FastAPI 服务，主要辅助老师和管理员**命题**：读取题图，调用 YOLOv8、EasyOCR、Pillow、NumPy 和 OpenCV，返回待人工复核的识别结果与图像质量提示。教师确认后，主服务才将候选输出保存为题目的标准答案。

学生提交代码由另一个模块 `Sandbox` 在隔离容器中运行；本服务不代替学生程序，也不决定最终 AC / WA。

## 能做什么

| 题型或能力 | 返回内容 | 说明 |
| --- | --- | --- |
| 图像目标计数 | 各类别数量、检测框和置信度 | 用于生成计数题的候选答案 |
| 主目标标签分类 | 检测结果中置信度最高的目标标签 | 使用 YOLO 检测结果，不是独立整图分类模型 |
| 目标位置检测 | 类别和归一化检测框 | 供教师配置位置检测题 |
| OCR 文字识别 | 中文、英文候选文字 | 由 EasyOCR 提供，结果需人工核对 |
| 图像数值分析 | 宽、高、色彩模式、平均灰度和边缘强度 | 属性使用 Pillow、NumPy 计算；`OPENCV_ANALYSIS` 是历史策略标识，不是训练模型 |
| 图像质量提示 | 亮度、清晰度及小图提醒 | OpenCV 计算辅助提示，不自动驳回题目或改写标准答案 |

## 本地运行（Windows）

在项目根目录打开 PowerShell：

```powershell
cd vision-ai
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python run.py
```

也可以双击项目根目录的 `start-vision-ai.bat`，只启动 YOLO / OCR 服务；首次运行会创建虚拟环境并安装依赖，使用期间保持窗口打开。若还需要 ComfyUI 生图服务，使用根目录的 `start-image-ai.bat`。

默认监听 `127.0.0.1:8090`。运行后访问 `http://127.0.0.1:8090/health` 检查进程是否已启动。首次在本地使用通用 YOLO 或 OCR 时，若相应权重不在配置路径，依赖库可能下载模型；环境变量示例见 [.env.example](./.env.example)，该文件不会被 `run.py` 自动加载。

## Linux 服务器部署

当前 [Dockerfile.server](./Dockerfile.server) **复用服务器已有的** `mayue-oj-python-vision:1.0` 判题镜像，再添加 FastAPI 服务依赖与本模块代码。因此，先确认该基础镜像存在；服务器若没有它，应先按 [Sandbox 镜像说明](../Sandbox/README.md)准备 Python 判题镜像。

将 `app/`、`run.py`、`Dockerfile.server` 和 `.dockerignore` 上传至服务器 `/www/wwwroot/vision-ai/`。使用这条复用镜像的部署路径时，模型权重已在基础镜像中，不必重复上传 `.venv/`、训练输出或模型文件。

在服务器终端执行：

```bash
free -h
df -h /
docker image inspect mayue-oj-python-vision:1.0
cd /www/wwwroot/vision-ai
docker build -f Dockerfile.server -t mayue-oj-vision-ai:1.0 .
docker run -d --name mayue-vision --restart unless-stopped \
  --network host --memory 1600m --memory-swap 2g \
  mayue-oj-vision-ai:1.0
docker logs --tail 60 mayue-vision
curl -f http://127.0.0.1:8090/health
```

`--network host` 让容器使用宿主机网络；服务仍只监听宿主机的 `127.0.0.1:8090`。不要为 8090 配置公网入口。`/health` 只能确认 API 进程已启动，启用主后端前还应使用真实题图调用 `/detect`，验证模型推理。

若服务器已有名为 `mayue-vision` 的容器，先检查旧容器及镜像，再按更新流程替换；直接重复执行 `docker run --name mayue-vision` 会提示名称冲突。4GB 服务器还需观察 `free -h`、`df -h` 和 `docker stats --no-stream`：视觉服务会与 Java、数据库、MinIO 和 Sandbox 共用资源；若因内存不足被终止，应把视觉服务部署到另一台机器并使用内网地址连接。

在宝塔主后端 Java 项目的环境变量中设置：

```text
VISION_AI_ENABLED=true
VISION_AI_BASE_URL=http://127.0.0.1:8090
```

保存后重启主后端。生产配置默认不会启用此服务；主后端和视觉服务若不在同一台机器，应将 `VISION_AI_BASE_URL` 改为仅内网可访问的地址。

## 接口与模型选择

| 接口 | 用途 |
| --- | --- |
| `GET /health` | 检查服务进程并返回模型路径配置 |
| `POST /quality` | 获取题图质量提示 |
| `POST /detect` | 根据题型和模型返回候选识别结果；请求使用 `imageUrl`、`taskType`、`modelKey` 等字段 |

目标计数、主目标标签分类和位置检测使用 `modelKey` 选择 YOLO 模型：

- `YOLO_GENERAL`：COCO 通用目标检测，对应 `VISION_MODEL_GENERAL`。
- `YOLO_HELMET`：校园安全帽检测，对应 `VISION_MODEL_HELMET`；本地默认路径为 `models/hard-hat-best.pt`。

OCR 和图像数值分析分别由 `taskType=IMAGE_OCR`、`taskType=IMAGE_ANALYSIS` 选择流程，不依赖 `modelKey` 选择 YOLO。`EASYOCR_ZH_EN` 表示中英文 OCR 方案；`OPENCV_ANALYSIS` 是历史路由标识，不是训练模型。图像属性由 Pillow、NumPy 计算，OpenCV 主要提供 `quality` 中的质量提示。

`/detect` 响应中的 `quality` 包括亮度、Laplacian 清晰度和提醒信息。这些是启发式参考，不会自动改变教师确认的标准答案。
