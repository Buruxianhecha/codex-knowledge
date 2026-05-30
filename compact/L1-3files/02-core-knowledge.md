# Core Knowledge — 全部经验/模式/错误/决策

---

## 一、项目: pdf-to-excel (2026-05-24)

Web 应用，PDF→Excel，Flask + 多OCR引擎。v1 完成，存在安全/工程问题待 v2。

### 技术栈
Flask 3.x / pdfplumber / Tesseract(TSV) / PaddleOCR(japan) / GPT-4o Vision / PyMuPDF / openpyxl / SQLite WAL / Supabase REST

### 架构核心: 多引擎并行择优
pdfplumber → Tesseract → PaddleOCR → GPT-4o，三路全跑，按列数评分选最优。

---

## 一点五、工具链项目: OpenClaw Provider 迁移 (2026-05-30)

OpenClaw Provider/API 网关配置迁移，重点经验是模型“存在”分三层：在线模型列表、本地 `models.json`、UI/默认映射。
`deepseek-v4-pro` 没有被删除，但 primary/fallback/allowed models 切到新网关后，旧入口从默认 UI 消失。

---

## 二、经验 (8条)

### 1. 多引擎并行择优 > 串行降级
OCR 没有"绝对失败"，只有"产出质量不同"。全跑一遍，按可量化分数选最优。
→ 代码模板: MultiEngineExtractor 类

### 2. 输出质量门控比完美解析更重要
AI/ML 管道产出必须在输出前过质量检查（填充率、列数、唯一值）。
宁可少输出，不错输出。

### 3. 空值优于假数据
不可读区域 → 留空 ""，永不填 "N/A" 或 0。

### 4. 第二个实现时就该抽象
第一个实现可以具体，第二个同一接口必须抽公共层。不等第三个。
→ 反面案例: 三个 OCR 引擎各自复制粘贴，FakeTable 重复定义三次

### 5. v1 完成后做硬化
v1 完成后的动作不是加功能，是: 删死代码、关 debug、统一配置、补鉴权、整理依赖。

### 6. Provider 名称不等于模型来源
`openai` 可能只是兼容协议，不一定是 OpenAI 官方服务。模型路由要看 provider、协议、base URL 类型和真实后端。

### 7. API 成本与缓存要有护栏
连接测试用最小 prompt；高消耗测试要确认目的。检查 usage/cache/fallback，避免缓存未命中或路由错误造成额度异常消耗。

### 8. UI 长对话迭代会放大缓存失效成本
单文件 UI 反复小改会让上下文膨胀、缓存失效、额度异常消耗。超过 5-10 轮迭代时拆文件、批量提需求，或开新对话。

---

## 三、可复用模式 (4个)

### 模式1: 多引擎并行择优 [verified]
```
输入 → [引擎A]→评分A ┐
     → [引擎B]→评分B ├→ max(评分) → 输出
     → [引擎C]→评分C ┘
```
适用: 多种算法都能处理同一输入，质量取决于输入特征。

### 模式2: 渐进式 Schema 迁移 [verified]
```python
def migrate():
    cols = [row[1] for row in conn.execute("PRAGMA table_info(t)")]
    if "new_field" not in cols:
        conn.execute("ALTER TABLE t ADD COLUMN new_field ...")
```
检测→按需添加，幂等安全。

### 模式3: 轻量 Supabase REST 客户端
不用官方 SDK，httpx 直接调 REST API。Auth + Storage + DB 全覆盖。

### 模式4: 输出质量门控 [verified]
原始数据 → 质量检查(填充率/结构完整性) → 合格输出 / 不合格丢弃。

---

## 四、错误教训 (7条)

### 错误1: OCR 引擎复制粘贴
根因: decisions/ocr-parallel (选了并行但没及时抽基类)
预防: 第二个实现时抽公共接口

### 错误2: 死代码残留
根因: 删除路由时只删装饰器忘了删函数体
预防: lint 工具自动检测

### 错误3: 硬编码路径
TESSERACT_EXE = "D:\OCR\tesseract.exe" — 换环境即炸
预防: 环境变量/配置文件统一管理

### 错误4: GPT SYSTEM_PROMPT 定义但未生效
精心写的 prompt 在常量里，API 调用时没传 system message
预防: LLM API 调用加集成测试

### 错误5: Flask debug=True 上线
Werkzeug 调试控制台可执行任意 Python
预防: 环境变量 FLASK_ENV 区分 dev/prod

### 错误6: 下载接口无鉴权
/download/<token> 没 @login_required
预防: 所有用户数据路由加鉴权装饰器

### 错误7: Provider 迁移丢模型映射
模型仍在 `models.json`，但 primary/fallback/allowed models 被改走，UI 入口消失。
预防: 迁移前后对比在线模型、本地清单、默认映射和允许列表。

---

## 五、设计决策 (5条)

### 决策1: OCR 并行择优 vs 串行降级
选并行，按列数评分。✅准确率高，❌每次跑 GPT-4o 成本高。

### 决策2: vendoring vs pip/venv
选 vendoring(libs/ 800+ 文件)。❌环境无法复现。v2 改 pip。

### 决策3: SQLite + Supabase 双写
✅离线可用，❌合并去重简陋。

### 决策4: Web vs CLI
选 Web。✅用户友好，❌不能批量。v2 加 CLI 模式。

### 决策5: LLM Provider 命名空间分离
Provider 名称表达真实来源或网关角色。✅路由清楚，❌配置多一步。

---

## 六、标签速查

| 标签 | 相关内容 |
|------|----------|
| #ocr | 多引擎择优, 复制粘贴引擎, GPT prompt未生效, 质量门控, 并行vs串行 |
| #architecture | 多引擎择优, 第二个实现抽象, 本地+云端双写, 并行vs串行 |
| #security | Flask debug, 下载无鉴权 |
| #technical-debt | 复制粘贴, 第二个实现抽象, v1硬化, vendoring |
| #quality | 质量门控, 空值优于假数据 |
| #refactoring | 复制粘贴, 死代码, 第二个实现抽象 |
| #portability | 硬编码路径, vendoring |
| #openclaw | Provider迁移, 模型映射, 配置验证 |
| #llm | Provider命名, 模型路由, API成本护栏 |
