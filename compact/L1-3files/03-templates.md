# Templates — 可复用代码和配置

---

## 代码模板

### Flask Session Secret（持久化）
```python
import os, secrets
from pathlib import Path

def load_or_create_secret_key(secret_path, environ=None):
    env = environ if environ is not None else os.environ
    env_secret = env.get("SECRET_KEY", "").strip()
    if env_secret:
        return env_secret
    secret_path = Path(secret_path)
    if secret_path.exists():
        existing = secret_path.read_text(encoding="utf-8").strip()
        if existing:
            return existing
    secret_path.parent.mkdir(parents=True, exist_ok=True)
    secret = secrets.token_hex(32)
    secret_path.write_text(secret + "\n", encoding="utf-8")
    return secret
```

### 多引擎调度器
```python
class MultiEngineExtractor:
    def __init__(self):
        self._engines = []
    
    def register(self, engine, score_fn):
        """engine: 有 .run(input) 方法的对象, score_fn: 评分函数(越高越好)"""
        self._engines.append((engine, score_fn))
    
    def extract(self, input_data):
        best_result, best_score = None, -1.0
        for engine, score_fn in self._engines:
            try:
                result = engine.run(input_data)
                score = score_fn(result)
                if score > best_score:
                    best_result, best_score = result, score
            except Exception:
                continue
        return (best_result, best_score) if best_result is not None else None
```

### SQLite 渐进式迁移
```python
def ensure_columns(conn, table, columns: dict):
    """按需添加列，幂等操作"""
    cur = conn.execute(f"PRAGMA table_info({table})")
    existing = {row[1] for row in cur.fetchall()}
    for col_name, col_def in columns.items():
        if col_name not in existing:
            conn.execute(f"ALTER TABLE {table} ADD COLUMN {col_name} {col_def}")
    conn.commit()
```

---

## 配置模板

### .env（Web 应用）
```bash
FLASK_ENV=development
SECRET_KEY=change-me-to-random-64-chars
DATABASE_URL=sqlite:///data/app.db
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=your-anon-key
OCR_MODE=auto
TESSERACT_PATH=C:\OCR\tesseract.exe
OPENAI_API_KEY=sk-...
```

### .gitignore（Python Web）
```gitignore
__pycache__/
*.py[cod]
.venv/
venv/
.env
*.secret_key
data/*.db
uploads/*.xlsx
uploads/*.png
```

---

## 目录结构模板

```
project/
├── app/
│   ├── __init__.py
│   ├── routes/        # auth.py, api.py
│   ├── services/      # 核心业务逻辑
│   ├── models/        # database.py
│   └── config.py
├── tests/
├── templates/
├── static/
├── data/              # .gitignore
├── uploads/           # .gitignore
├── .env
├── .gitignore
├── requirements.txt
├── README.md
└── run.py
```
