# Decisions & Templates

---

## 设计决策

### 1. OCR: 并行择优 vs 串行降级
**选**: 并行，按列数评分
**后果**: ✅ 准确率高  ❌ 每次跑 GPT-4o 成本高

### 2. 依赖: vendoring vs pip/venv
**选**: libs/ vendoring 800+ 文件
**后果**: ❌ 环境无法复现 → v2 改 pip

### 3. 同步: SQLite + Supabase 双写
**选**: 本地为主，云端备份
**后果**: ✅ 离线可用  ❌ 合并去重简陋

### 4. 形态: Web vs CLI
**选**: Flask Web 应用
**后果**: ✅ 用户友好  ❌ 不能批量 → v2 加 CLI

### 5. LLM Provider 命名空间分离
**选**: Provider 名称表达真实来源或网关角色
**后果**: ✅ 模型路由清楚  ❌ 初次配置多一步

---

## 代码模板

### Flask Session Secret
```python
def load_or_create_secret_key(secret_path, environ=None):
    env = environ if environ is not None else os.environ
    env_secret = env.get("SECRET_KEY", "").strip()
    if env_secret: return env_secret
    if secret_path.exists():
        existing = secret_path.read_text(encoding="utf-8").strip()
        if existing: return existing
    secret_path.parent.mkdir(parents=True, exist_ok=True)
    secret = secrets.token_hex(32)
    secret_path.write_text(secret + "\n", encoding="utf-8")
    return secret
```

### MultiEngineExtractor
```python
class MultiEngineExtractor:
    def __init__(self): self._engines = []
    def register(self, engine, score_fn):
        self._engines.append((engine, score_fn))
    def extract(self, input_data):
        best_result, best_score = None, -1.0
        for engine, score_fn in self._engines:
            try:
                result = engine.run(input_data)
                if (s := score_fn(result)) > best_score:
                    best_result, best_score = result, s
            except Exception: continue
        return (best_result, best_score)
```

### LLM Provider 迁移检查
```text
1. 备份主配置和 models.json
2. 列出 enabled/disabled provider
3. 获取在线模型列表
4. 对比本地模型清单
5. 检查 Chat/Agent/Codex/Fallback 映射
6. 使用最小 prompt 做连接测试
7. 检查 usage/cache/fallback 成本风险
8. 提交前搜索 sk-, ghp_, github_pat_, botToken, Bearer
```
