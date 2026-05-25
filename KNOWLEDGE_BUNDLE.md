
# Codex Knowledge Base — 完整知识包

> 这是一个自包含的工程经验库。将此文件完整提供给任何 AI（ChatGPT、Claude、Codex、Cursor 等），
> 它即可理解用户的全部工程背景、偏好、经验和教训。
>
> 生成时间: 2026-05-25 20:00
> 原始位置: D:\Codex-Knowledge

---

================================================================================
第一部分：系统定义
================================================================================

### SYSTEM.md — 角色定义

# SYSTEM.md — 工程知识伙伴角色定义 v2

> 我不是聊天机器人，不是代码生成器。
> 我是**长期工程知识伙伴**。

## 核心使命

帮助用户逐渐形成**稳定、有效、可演化**的软件工程经验体系——
每个回答、每个项目、每个决策，都在为这个体系添砖加瓦。

## 四层身份

### 第一层：执行者（Code Assistant）
写代码、修 bug、跑命令。但不停留于此。

### 第二层：审计者（Code Reviewer）
每完成一个任务，自动回顾：
- 为什么这样设计？
- 为什么失败/返工？
- 哪些方案更稳定？
- 哪些模式可复用？

### 第三层：知识管理者（Knowledge Partner）
- 将回顾结果蒸馏为结构化知识
- 建立条目间的交叉引用（错误↔决策↔经验↔模式）
- 标记知识的生命周期（active → verified → best_practice → deprecated）

### 第四层：知识保鲜者（Knowledge Curator）
- 遇到 API/框架/模型时主动验证信息时效性
- 检索后蒸馏，对比旧经验，更新或淘汰
- 稳定知识用缓存，变动技术要验证
- 详见 `FRESHNESS.md`

## 工作流

```
收到问题
    │
    ├─ 检查知识库
    │   ├─ 涉及有时效的技术？ → 联网验证 → 对比更新
    │   └─ 稳定的知识？ → 直接用
    │
    ├─ 执行任务
    │
    ├─ 完成任务 → 自动审计
    │   ├─ 为什么成功/失败
    │   ├─ 可复用模式
    │   └─ 根因追溯
    │
    └─ 蒸馏入库
        ├─ 分类写入对应目录
        ├─ 建立交叉引用
        ├─ 检查是否验证/推翻了旧知识
        └─ 更新状态标记
```

## 知识生命周期

```
          ┌─────────┐
          │ active  │ ← 新记录
          └────┬────┘
               │ 2+ 次验证
          ┌────▼────────────┐
          │ verified        │
          └────┬────────────┘
               │ 3+ 次验证 + 3月稳定
          ┌────▼────────────┐
          │ best_practice   │ ← 最高级别
          └─────────────────┘

          ┌─────────┐
          │ active  │
          └────┬────┘
               │ 技术变化
          ┌────▼──────────┐
          │ deprecated     │ → 指向替代方案
          └────────────────┘
```

## 回答问题的优先级

1. **知识库** → 有相关条目且不涉及时效性风险 → 直接用
2. **联网验证** → 涉及时效性风险 → 检索 → 蒸馏 → 对比 → 更新
3. **通用知识** → 不依赖版本的稳定知识 → 正常回答

## 关键文件

| 文件 | 角色 |
|------|------|
| `SYSTEM.md` | 角色定义（你在这里） |
| `FRESHNESS.md` | 知识保鲜规则 |
| `.value-rules.md` | 价值判断 + 交叉引用 + 演化规则 |
| `.codex-instructions.md` | 给未来 AI 的自举指南 |
| `README.md` | 库的整体入口 |

## 自我约束

- ✅ 稳定知识不联网浪费资源
- ✅ 变动技术主动验证不偷懒
- ✅ 检索后蒸馏不复制粘贴
- ✅ 过时标记不删除
- ✅ 验证升级不遗漏
- ❌ 不使用已废弃的 API
- ❌ 不推荐已被推翻的方案
- ❌ 不堆积无价值的闲聊


---

### preferences/user-profile.md — 用户画像

# User Profile — 蒸馏版

> 来源: 记忆系统 ad-hoc notes + 项目审计中观察到的编码风格
> 目的: 任何 AI 读完此文件即可理解用户的偏好和风格

## 核心偏好

### 软件安装
- **必须询问**再安装软件，不擅自操作
- **避免 C 盘**，除非别无选择
- **模仿已有风格**: 观察已有软件的安装位置和命名方式，保持一致
- 安装完成后**告知实际路径**

### 品牌一致性
- 快捷方式名称和图标**必须匹配实际品牌**
- 不要用依赖工具（如 Ollama）的图标代替目标产品（如 DeepSeek）的图标
- DeepSeek 快捷方式 → 用 DeepSeek 图标，不是 Ollama 图标

## 编码风格

### 命名
- **实用主义**: 短变量名可接受（`THIN`, `P2C`, `MCW`, `MRH`）
- 模块级常量用短名，不追求"自文档化"的长名
- 但核心函数/类应有清晰命名

### 代码组织
- **快速原型风格**: 可以容忍 `sys.path.insert`、内联导入
- 先跑通再优化，不追求完美初始架构
- v1 完成后做一轮"硬化"（清理死代码、关 debug、统一配置）

### 时间/地区
- 时区硬编码为 UTC+8（中国用户）
- 中文注释和文档，技术术语可混合英文

### 测试
- 有测试基础设施（unittest 导入），但覆盖不追求全面
- 核心逻辑可加测试，但不过度推销 TDD

### 技术栈偏好
- Python 为主力语言
- Flask 用于 Web 应用
- SQLite 用于本地存储
- 优先轻量方案而非重量级框架

## 工作原则

1. **空值优于假数据**: 不可读区域留空，不做猜测填充
2. **实用优先**: 方案能跑通比架构优雅更重要（v1 阶段）
3. **第二个实现时抽象**: 同一接口出现两次 → 抽公共层
4. **v1 完成后加固**: 不立刻加功能，先清理债务

---

*蒸馏自: 2026-05-24 两条 ad-hoc note + 2026-05-25 项目审计*


---


================================================================================
第二部分：项目经验
================================================================================
### projects/pdf-to-excel.md

# pdf-to-excel

## 基本信息
- **创建日期**: 2026-05-24
- **状态**: v1 已完成
- **路径**: D:\Projects\pdf-to-excel

## 项目目标
将 PDF（日文 CAD/工程图纸）中的表格精确还原为格式化 Excel。

## 技术栈
| 层次 | 技术 | 用途 |
|------|------|------|
| Web 框架 | Flask 3.x | 路由、Session、模板 |
| PDF 解析 | pdfplumber | 文本型 PDF 表格检测 |
| OCR | Tesseract (TSV) | 图像型 PDF 文字识别 |
| OCR | PaddleOCR (japan) | 高精度日语 OCR |
| OCR | GPT-4o Vision | AI 兜底复杂表格 |
| PDF 渲染 | PyMuPDF (fitz) | 页面→PNG |
| Excel | openpyxl | 写入+样式控制 |
| 本地 DB | SQLite WAL | 用户、历史 |
| 云端 | Supabase REST | 跨设备同步 |
| 认证 | werkzeug | 密码哈希 |

## 实现方案
多级 OCR 降级链：pdfplumber → Tesseract → PaddleOCR → GPT-4o。
三路并行跑，按列数最多的结果输出（智能选择而非简单降级）。
Web 端上传→处理→下载，带账号系统和云端历史同步。

## 最终结果
- v1 可用，能处理日文 PDF 表格
- 多种 OCR 引擎协同工作
- 账号系统 + Supabase 云端同步完整
- 已知问题：安全漏洞（debug模式、无鉴权下载）、代码冗余、GPT prompt 未生效

## 关联
- lessons/multi-ocr-fallback.md
- lessons/quality-gating.md
- lessons/empty-over-fake.md
- patterns/multi-engine-parallel-select.md
- patterns/sqlite-migration.md
- mistakes/copy-paste-engines.md
- mistakes/dead-code-orphan.md
- mistakes/hardcoded-paths.md
- decisions/ocr-fallback-vs-select.md
- decisions/libs-vendoring.md


---


================================================================================
第三部分：长期经验
================================================================================
### lessons/abstract-on-second.md

# 第二个实现时就应该抽象

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
先写了 Tesseract OCR，然后需要加 PaddleOCR，选择了复制 Tesseract 的文件改中间逻辑。再加 GPT-4o 时又复制了一次。

## 经验
第一次实现可以具体。第二次实现同一个接口时，必须停下来抽公共层。第三次还不抽象就是技术债。

## 行动指南
同一接口的第二个实现 = 抽象信号。不要等第三个。

## 标签
#architecture #refactoring #technical-debt



---

### lessons/empty-over-fake.md

# 空值优于假数据

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
PDF 中的图纸、图形区域无法被 OCR 识别。常见做法是填 "N/A" 或 0。

## 经验
留空。填假数据会让用户误以为有真实数据，比留空危险得多。

## 行动指南
数据提取类工具：不可读区域 → 留空字符串 `""`，不做任何猜测填充。

## 标签
#data-quality #ux #ocr



---

### lessons/multi-engine-parallel-select.md

# 多引擎并行择优 > 串行降级

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
需要从多种 OCR 引擎中选择最佳结果。传统做法是串行：A 失败→B 失败→C。但 OCR 没有"绝对失败"，只有"产出质量不同"。

## 经验
不是"前面的失败才用后面的"，而是"全跑一遍，按列数（结构化程度）选最优"。

```python
# 好的做法
for engine in engines:
    result = engine.run(pdf)
    if result.cols > best_cols:
        best = result
return best
```

## 行动指南
当多个方案都能产出结果但质量不同时，优先并行评估而非串行降级。设计一个可量化的评分函数（列数、填充率、置信度）。

## 标签
#ocr #architecture #algorithm-design



---

### lessons/quality-gating.md

# 输出质量门控比完美解析更重要

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
OCR 引擎经常产出"看起来像表格但不是"的垃圾数据。如果把垃圾直接输出给用户，信任度会崩塌。

## 经验
在数据进入最终输出前设一道质量门控（填充率、列数、唯一值数量），宁可少输出也不输出错误。

```python
def _valid(data):
    # 填充率 < 20% 且少于4行 → 丢弃
    if fill_rate < 0.2 and len(data) < 4:
        return False
    # 单列表超过30行 → 可能是文本流，丢弃
    if len(data) > 30 and len(data[0]) == 1:
        return False
```

## 行动指南
任何 AI/ML 输出管道都要加质量门控。门控规则应该从实际 bad case 中提炼。

## 标签
#quality #ocr #data-validation



---

### lessons/v1-hardening.md

# 快速原型的工程债务清单

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 场景
v1 快速跑通后，代码里留下了大量原型期痕迹。

## 经验
v1 完成后应该立即做一轮"原型债务清理"：
1. 删除死代码
2. 关掉 debug 模式
3. 统一配置管理（消灭硬编码路径）
4. 补鉴权遗漏
5. 整理依赖管理

## 行动指南
每完成一个可用的 v1，做一次"硬化"——不是加功能，是加固现有代码。

## 标签
#engineering #technical-debt #v1-hardening



---


================================================================================
第四部分：可复用模式
================================================================================
### patterns/lightweight-supabase-client.md

# 轻量 Supabase REST 客户端模式

## 适用场景
不想引入 @supabase/supabase-js 或 supabase-py 的完整依赖，只需要 Auth + Storage + REST 的基本操作。

## 结构
直接用 httpx/requests 调用 Supabase REST API，自己管理 anon key 和 Bearer token。

## 代码示例
```python
def _headers(auth_token=None):
    h = {"apikey": SUPABASE_KEY}
    h["Authorization"] = f"Bearer {auth_token or SUPABASE_KEY}"
    return h

def supabase_signin(email, password):
    r = httpx.post(f"{SUPABASE_URL}/auth/v1/token?grant_type=password",
                   json={"email": email, "password": password},
                   headers=_headers())
    return r.json()
```

## 已知应用
- pdf-to-excel: supabase_client.py 完整实现

## 标签
#supabase #rest #lightweight-client



---

### patterns/multi-engine-parallel-select.md

# 多引擎并行择优模式

## 适用场景
有多种算法/引擎/模型都能处理同一输入，但输出质量取决于输入特征，无法预先判断哪个最好。

## 结构
```
输入 → [引擎A] → 评分A ┐
     → [引擎B] → 评分B ├→ max(评分) → 输出
     → [引擎C] → 评分C ┘
```

## 代码示例
```python
class MultiEngineExtractor:
    def __init__(self):
        self.engines = []
    
    def register(self, engine, cost=1):
        self.engines.append((engine, cost))
    
    def extract(self, input_data):
        best_result, best_score = None, 0
        for engine, cost in self.engines:
            result = engine.run(input_data)
            score = engine.evaluate(result) / cost
            if score > best_score:
                best_result, best_score = result, score
        return best_result
```

## 已知应用
- pdf-to-excel: pdfplumber / Tesseract / PaddleOCR / GPT-4o 四路并行选优

## 标签
#architecture #ocr #multi-model



---

### patterns/output-quality-gate.md

# 输出质量门控模式

## 适用场景
AI/ML/OCR 管道产出结果不确定，需要在输出前过滤低质量结果。

## 结构
```
原始数据 → 质量门控(填充率/结构完整性/异常检测) → 合格数据 → 输出
                                              → 不合格 → 丢弃/降级
```

## 代码示例
```python
def quality_gate(data, min_fill_rate=0.2, min_cols=2, min_rows=2):
    total_cells = sum(len(row) for row in data)
    filled_cells = sum(1 for row in data for c in row if c)
    if total_cells == 0:
        return False
    if filled_cells / total_cells < min_fill_rate:
        return False
    if len(data[0]) < min_cols or len(data) < min_rows:
        return False
    return True
```

## 已知应用
- pdf-to-excel: `_valid()` 函数

## 标签
#quality #ai-pipeline #validation



---

### patterns/sqlite-migration.md

# 渐进式 Schema 迁移模式

## 适用场景
使用 SQLite 等轻量数据库，需要在不停机不丢数据的情况下加字段。

## 结构
```python
def migrate():
    cur = conn.execute("PRAGMA table_info(users)")
    existing_cols = [row[1] for row in cur.fetchall()]
    if "new_field" not in existing_cols:
        conn.execute("ALTER TABLE users ADD COLUMN new_field TEXT DEFAULT ''")
```

检测→按需添加，幂等操作（多次运行安全）。

## 代码示例
见 pdf-to-excel database.py `migrate_add_profile()`

## 已知应用
- pdf-to-excel: avatar 和 display_name 字段后续添加

## 标签
#database #sqlite #migration



---


================================================================================
第五部分：错误教训
================================================================================
### mistakes/copy-paste-ocr-engines.md

# 三个 OCR 引擎复制粘贴——没有抽象

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
ocr.py、ocr_paddle.py、ocr_gpt.py 各 100+ 行，70% 代码重复。FakeTable 类在三个文件中各自定义。改一处需要改三处。

## 根因
先实现了主流程（pdfplumber→Excel），后续加 OCR 引擎时直接复制文件修改中间逻辑。没有在第二个引擎加入时停下来抽公共层。

## 修复
抽 `BaseOCRExtractor`，定义 `extract(pdf_path) -> List[TableData]` 接口。三个引擎实现该接口。FakeTable 替换为 dataclass。

## 预防
- 同一接口的第二个实现 = 抽象信号
- 用 `jscpd` 或 `sonar` 检测代码重复
- 加新引擎前先看能不能复用现有 pipeline

## 标签
#copy-paste #refactoring #ocr #technical-debt



---

### mistakes/dead-code-orphan.md

# 死代码残留

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
main.py 中有两段无路由装饰器的孤儿代码（魔法链接处理、oauth callback 的 return），永远不会执行。

## 根因
重构或删除路由时，只删了 `@app.route` 装饰器，忘了删函数体。代码审查缺失。

## 修复
删除孤儿代码块。

## 预防
- 功能删除时搜索函数名确认无残留
- 引入 pylint/pyflakes 自动检测无引用的代码
- CI 中加 dead code 检测

## 标签
#dead-code #refactoring #lint



---

### mistakes/download-no-auth.md

# 下载接口无鉴权

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
`/download/<token>` 没有 `@login_required`，知道 UUID 就能下载任何用户的文件。

## 根因
开发时为了方便测试跳过了鉴权，事后忘记加回。

## 修复
加上 `@login_required` 装饰器，并验证 token 属于当前用户。

## 预防
- 所有涉及用户数据的路由都应该有鉴权
- 用装饰器统一管理而非逐路由手动检查
- 安全审计 checklist

## 标签
#security #auth #api



---

### mistakes/flask-debug-true.md

# Flask debug=True 上线

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
`app.run(debug=True)` — 即使监听 127.0.0.1，debug 模式会暴露 Werkzeug 调试控制台（可执行任意 Python）。

## 根因
开发-部署切换没有自动化流程，手动改配置容易遗漏。

## 修复
```python
debug = os.environ.get("FLASK_ENV") == "development"
app.run(debug=debug)
```

## 预防
- 用环境变量区分 dev/prod
- 部署 checklist 包含 "确认 debug=False"
- 使用 flask run 而非 app.run()

## 标签
#security #flask #deployment



---

### mistakes/gpt-system-prompt-unused.md

# GPT SYSTEM_PROMPT 定义但未使用

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
ocr_gpt.py 定义了一个详细的 SYSTEM_PROMPT 常量（含 6 条 CRITICAL RULES），但 API 调用时 messages 数组里只有一条 user message，SYSTEM_PROMPT 从未传入。

## 根因
可能是先写了 prompt，后来改了 API 调用方式，忘了把 system prompt 加回去。没有自动化测试覆盖 OCR 路径。

## 修复
```python
messages=[
    {"role": "system", "content": SYSTEM_PROMPT},
    {"role": "user", "content": [...]}
]
```

## 预防
- LLM API 调用加集成测试验证 prompt 生效
- prompt 常量靠近使用点，或统一管理
- 代码审查时检查 API 参数完整性

## 标签
#llm #prompt-engineering #bug



---

### mistakes/hardcoded-paths.md

# 硬编码路径

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 症状
- `TESSERACT_EXE = Path(r"D:\OCR\tesseract.exe")`
- `Path(r"D:\Projects\pdf-to-excel\uploads")` 在 ocr.py 中写死
- 换机器或重装系统后直接不可用

## 根因
快速原型期图方便，直接写绝对路径。Flask config 有 `UPLOAD_FOLDER` 但 OCR 模块没读。

## 修复
- Tesseract: 从环境变量 `TESSERACT_PATH` 或系统 PATH 读取
- 上传目录: 统一读取 Flask config

## 预防
- 规则：配置信息只出现在 `.env` / `config.py` / 环境变量中
- 代码中永远不出现绝对路径字符串

## 标签
#hardcoded #configuration #portability



---


================================================================================
第六部分：设计决策
================================================================================
### decisions/libs-vendoring.md

# 依赖管理：vendoring vs pip/venv

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
项目依赖 30+ Python 库，有两种管理方式：
- pip + venv（标准做法）
- 把库文件直接复制到 libs/（vendoring）

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| pip + venv | 标准做法，版本清晰，可复现 | 需要 pip install 步骤 |
| libs/ vendoring | 免安装，复制即用 | 版本混乱，800+ 文件，无法复现 |

## 决策
选择了 vendoring（所有依赖放在 libs/），但 requirements.txt 只写了 5 个库。

## 后果
环境无法复现。建议 v2 切换为标准 venv 方案，`pip freeze > requirements.txt`。

## 标签
#dependency-management #python #technical-debt



---

### decisions/local-remote-dual-write.md

# 本地 SQLite + 云端 Supabase 双写

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
历史记录需要跨设备同步。选择：
- 纯云端（Supabase only）
- 双写（SQLite 本地 + Supabase 云端）

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| 纯 Supabase | 架构简单，无同步问题 | 离线不可用，每次查询走网络 |
| 双写 | 离线可用，本地速度快 | 同步逻辑复杂，可能不一致 |

## 决策
双写——本地 SQLite 为主，后台同步到 Supabase。查询时合并去重。

## 后果
离线体验好，但合并去重逻辑简陋（只按 token 去重），编辑/删除不同步。

## 标签
#architecture #sync #database



---

### decisions/ocr-parallel-vs-sequential.md

# OCR 策略：并行择优 vs 串行降级

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
需要从多种 OCR 引擎中获取最佳结果。两个选项：
- 串行降级：A 失败 → B → C
- 并行择优：全跑一遍，按评分选最优

## 选项
| 方案 | 优点 | 缺点 |
|------|------|------|
| 串行降级 | 省资源，大部分情况第一个就够了 | "失败"的定义模糊，可能错过更好的结果 |
| 并行择优 | 总能拿到最佳结果 | 每次都要跑多个引擎，耗时和成本高 |

## 决策
选择了并行择优——按列数评分，三路全跑，选列最多的结果。

## 后果
准确率显著提高，但每次上传都会调用 GPT-4o API（成本）。后续应加入"如果前面引擎产出足够好就跳过 GPT"的优化。

## 标签
#architecture #ocr #trade-off



---

### decisions/web-vs-cli.md

# Web 应用 vs CLI 工具

## 来源
- 项目: pdf-to-excel
- 日期: 2026-05-24

## 背景
goals.md 中写着"命令行程序 或 网页端上传工具"，最终选择了 Web。

## 决策
选择了 Flask Web 应用 + 浏览器界面。

## 后果
- 优点：用户友好，有账号系统和历史记录
- 缺点：部署复杂，需要服务器，不能批量处理本地文件
- 建议：v2 加一个 CLI 模式（`python -m app.cli input.pdf -o output.xlsx`）

## 标签
#product #cli-vs-web



---


================================================================================
第七部分：蒸馏记忆
================================================================================
### memory/distilled-memory.md

# Distilled Memory — 可移植版

> 从 Codex 记忆系统蒸馏出的关键信息，不依赖原始记忆格式。

## 活跃项目

### pdf-to-excel (v1 已完成)
- **路径**: D:\Projects\pdf-to-excel
- **描述**: Web 应用，PDF 表格→格式化 Excel，支持日语 CAD 图纸
- **技术栈**: Flask + pdfplumber + PaddleOCR + GPT-4o + Tesseract + Supabase
- **状态**: v1 可用，存在安全/工程问题待 v2 修复
- **详细**: 见 `projects/2026-05-24-pdf-to-excel.md`

## 长期目标

### G1: PDF → Excel 完整转化工具
- **目标**: PDF 表格精确转为可编辑 Excel，保留格式
- **形态**: CLI 或 Web
- **状态**: v1 完成 (D:\Projects\pdf-to-excel)
- **创建**: 2026-05-24

## 经验速查

| 经验 | 一句话 |
|------|--------|
| 多引擎并行择优 | 多个方案时全跑一遍，按可量化评分选最优 |
| 质量门控 | AI 输出必须过质量检查，宁可少输出不错输出 |
| 空值优于假数据 | 不可读 → 留空 ""，不做猜测填充 |
| 第二实现即抽象 | 同一接口第二次写 → 必须抽公共层 |
| v1 硬化 | v1 完成后先加固（安全、配置、死代码），不加功能 |

## 错误速查

| 错误 | 预防 |
|------|------|
| OCR 引擎复制粘贴 | 第二个引擎时抽基类 |
| 死代码残留 | 删除路由时检查函数体 |
| 硬编码路径 | 统一走配置/环境变量 |
| GPT prompt 未生效 | API 调用加集成测试 |
| Flask debug=True 上线 | 环境变量区分 dev/prod |
| 下载接口无鉴权 | 所有用户数据路由加 @login_required |

## 设计决策速查

| 决策 | 选择 | 原因 |
|------|------|------|
| OCR 策略 | 并行择优 | 准确率优先 |
| 依赖管理 | libs/ vendoring (v1) | 快速原型，v2 改 pip |
| 本地+云端 | SQLite + Supabase 双写 | 离线可用 + 跨设备 |
| Web vs CLI | Web (v1) | 用户友好，v2 加 CLI |

---

*蒸馏自: Codex 记忆系统 2026-05-25 梦境整合*

