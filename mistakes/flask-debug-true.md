---
status: active
since: 2026-05-25
lesson: lessons/v1-hardening.md (硬化清单第2项)
related: mistakes/download-no-auth.md (同属安全问题)
---
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

