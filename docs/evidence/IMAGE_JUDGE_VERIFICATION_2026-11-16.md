# 五类图像编程题验证记录（2026-11-16）

## 1. 验证范围

本记录区分四种证据：代码审查、单元测试、构建验证和真实服务链路。没有运行过的实验不标记为通过。

| 项目 | 状态 | 证据范围 |
|---|---|---|
| 五类输出协议与语义配置 | 已通过单元测试 | Spring Boot 策略与服务测试 |
| 公开样例与正式提交语义一致 | 已通过单元测试 | 运行服务与正式计数策略测试 |
| 发布前公开/隐藏/多图/人工确认检查 | 已通过单元测试 | 问题校验与预检测试 |
| FastAPI 图像质量、OCR候选与数值分析 | 已通过单元测试 | Python `unittest`，未加载真实 YOLO/OCR 权重做准确率实验 |
| 无网络、非 root、图片只读挂载 | 代码审查；图片挂载已通过单元测试 | Docker 创建参数与挂载构造测试 |
| 浏览器 → 主服务 → Vision AI → MinIO → 沙箱完整链路 | 待真实环境验证 | 本轮未启动全部服务做端到端验收 |

## 2. 可复现命令

### Spring Boot 图像判题

```powershell
mvn -f spingboot-init/pom.xml '-Dtest=QuestionRunServiceImplTest,ObjectCountJudgeStrategyTest,ImageOutputJudgeStrategyTest,QuestionPreflightServiceTest,QuestionImageValidationTest' test
```

覆盖独立数量误差、IoU、OCR 编辑距离、灰度/边缘误差、边界值、历史 `countTolerance` 回退、隐藏反馈脱敏、公开运行和发布预检。

### Vision AI

在 `vision-ai` 目录执行：

```powershell
& '.venv/Scripts/python.exe' -m unittest discover -s tests -v
```

2026-11-16 本地结果：4 项通过。它验证候选 OCR 纯文本、固定图像分析字段和 OpenCV 质量提醒，不等同于 OCR 准确率或 YOLO mAP 实验。

### Docker 代码执行环境

```powershell
mvn -f Sandbox/pom.xml '-Dtest=DockerPythonImageMountTest,SecurityPolicyTest' test
```

2026-11-16 本地结果：3 项通过。代码同时配置 `network=none`、`user=1000:1000`、内存上限、只读 `/input-images` 和临时目录清理；本轮没有启动 Docker 做攻击型动态验证。

### 前端

```powershell
npm --prefix 前端/myoj run check:image-problem-types
npm --prefix 前端/myoj run type-check
npm --prefix 前端/myoj run build-only
```

页面契约、TypeScript 检查和 Vite 生产构建已通过。

## 3. 任务书指标风险

| 指标 | 当前证据 | 结论 |
|---|---|---|
| 提交响应不超过 5 秒 | 图像题前端默认执行上限为 60 秒，模型首次加载和 CPU 推理可能明显超过 5 秒 | 存在冲突，需区分“请求受理响应”和“完整判题完成时间”，并实测 P50/P95 |
| 默认超时不超过 10 秒 | 普通题可按短时限配置；图像题为模型加载预留了更长时限 | 待与任务书口径确认并做冷/热启动实验，不能直接宣称满足 |
| YOLO mAP@0.5 ≥ 80% | 现有独立测试记录的三类平均 mAP50 为 0.676 | 当前未达到；不得写成已达标，应报告类别不平衡并继续训练/补数据 |
| OCR 准确率 ≥ 90% | 没有带真实标注的独立测试集统计 | 待实验验证 |
| 判题与人工标注一致率 ≥ 95% | 没有逐题人工复核对照记录 | 待实验验证 |
| 20 名学生并发 | 没有并发压测报告 | 待实验验证 |
| 4GB 服务器可支撑并发图像推理与评测 | Vision 容器限制约 1.6GB，单个图像评测容器可限制约 1.5GB；服务器还运行 Java、MySQL、Redis、MinIO | 高风险，不能据配置推断可承载 20 并发；需限制图像任务并发并压测 |

## 4. 建议的真实验收

1. 为五类题各建立至少 1 道题，配置 1 张公开图和 2 张隐藏图。
2. 使用教师账号完成识别、人工复核、预检和发布。
3. 使用学生账号分别提交正确、格式错误、边界内和边界外程序。
4. 保存每次请求时间、沙箱时间、内存、模型冷/热启动时间和最终判定。
5. 准备独立 YOLO/OCR 测试集，保存原始标注、预测和计算脚本。
6. 使用 1、5、10、20 并发逐级压测，监测 4GB 服务器内存、交换区、CPU、失败率和 P95。

只有完成上述真实链路与实验后，才能在论文中给出性能和准确率结论。
