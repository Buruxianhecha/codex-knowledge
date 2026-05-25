# Mistakes — 错误教训

---

## 1. OCR 引擎复制粘贴
**根因**: 选了并行方案但没及时抽象基类
**预防**: 第二个实现时抽公共接口

## 2. 死代码残留
**根因**: 删除路由时只删 @app.route 忘了删函数体
**预防**: lint 自动检测 + 删除时全文搜索

## 3. 硬编码路径
**症状**: TESSERACT_EXE = "D:\OCR\tesseract.exe"
**预防**: 环境变量/配置文件统一管理

## 4. GPT SYSTEM_PROMPT 未生效
**症状**: prompt 常量定义了但 API 调用时没传 system message
**预防**: LLM API 调用加集成测试

## 5. Flask debug=True 上线
**风险**: Werkzeug 调试控制台可执行任意 Python
**预防**: FLASK_ENV 环境变量区分 dev/prod

## 6. 下载接口无鉴权
**症状**: /download/<token> 没有 @login_required
**预防**: 所有用户数据路由统一加鉴权装饰器
