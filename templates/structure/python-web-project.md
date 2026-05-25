# Python Web 项目目录结构

```
project/
├── app/
│   ├── __init__.py          # Flask app factory
│   ├── routes/
│   │   ├── __init__.py
│   │   ├── auth.py          # 认证相关路由
│   │   └── api.py           # 业务 API 路由
│   ├── services/
│   │   ├── __init__.py
│   │   └── extraction.py    # 核心业务逻辑
│   ├── models/
│   │   ├── __init__.py
│   │   └── database.py      # 数据层
│   └── config.py            # 配置管理
├── tests/
│   ├── __init__.py
│   ├── test_routes.py
│   └── test_services.py
├── templates/               # Jinja2 模板
├── static/                  # CSS/JS/图片
├── data/                    # 运行时数据 (.gitignore)
├── uploads/                 # 用户上传 (.gitignore)
├── .env                     # 环境变量 (.gitignore)
├── .gitignore
├── requirements.txt
├── README.md
└── run.py                   # 入口: app.run()
```
