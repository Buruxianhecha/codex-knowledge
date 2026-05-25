# FRESHNESS.md — 知识保鲜规则

> 知识库不是博物馆，是工具箱。工具必须保持锋利。

---

## 一、回答前检查流程

```
用户提问
    │
    ├─ 知识库有相关条目？
    │   ├─ 是 → 条目是否涉及有时效性的技术？
    │   │   ├─ 是 → 联网验证 → 对比 → 更新或确认
    │   │   └─ 否 → 直接使用
    │   └─ 否 → 正常回答 + 判断是否值得入库
    │
    └─ 完成回答后 → 检查是否有需要标记过时的旧条目
```

## 二、需要验证时效性的技术类别

以下类别**主动验证**（联网检索最新信息）：

| 类别 | 检查频率 | 示例 |
|------|----------|------|
| AI 模型 API | 每次使用前 | OpenAI API, GPT-4o 参数, pricing |
| Web 框架 | 新项目启动时 | Flask, FastAPI 最新版本和 breaking changes |
| Python 生态 | 新项目启动时 | 依赖版本, 弃用警告 |
| OCR 引擎 | 每次涉及 OCR 时 | PaddleOCR, Tesseract 版本 |
| 部署方案 | 新项目部署前 | Supabase, Vercel 免费额度和限制 |
| 安全实践 | 每次涉及安全时 | Flask debug 风险, 最新 CVE |
| 第三方服务 | 每季度 | Supabase API 变更, 定价变化 |

## 三、不需要验证的类别

以下**直接用经验库**，不联网：

| 类别 | 原因 |
|------|------|
| 算法/数据结构 | 不变 |
| 设计模式 | 不变 |
| 工程原则 | 不变（"第二个实现时抽象"永远成立） |
| 用户偏好 | 只有用户自己能改变 |
| Debug 方法论 | 不变 |
| 代码组织原则 | 缓慢变化 |

## 四、检索后的处理

不是"复制粘贴搜索结果"，而是：

### 对比
```
旧知识: Flask session secret 用 secrets.token_hex(32)
新文档: Flask 3.x 推荐 secrets.token_urlsafe()
→ 结论: 两者都安全，旧方案仍有效，无需更新
```

### 提炼
```
搜索结果: 5000 字 changelog
提炼为: "Flask 3.1 废弃了 before_first_request，改用 init_app 中的 with app.app_context()"
入库: 一条简洁的 lessons/ 条目
```

### 更新
```
旧条目: patterns/xxx.md
发现: 有更简洁的实现
操作: 旧条目标记 superseded，指向新条目
```

## 五、淘汰标记格式

当旧方案不再适用时：

```markdown
---
status: deprecated
since: 2026-05-25
deprecated_since: 2026-06-15
reason: Flask 3.1 废弃了 before_first_request
migration_guide: lessons/flask3-upgrade.md
---
```

## 六、稳定最佳实践的形成标准

一条经验升级为"稳定最佳实践"需要：

- [ ] 在 2+ 个项目中成功应用
- [ ] 至少 3 个月内未被推翻
- [ ] 不依赖特定版本号
- [ ] 有对应的代码模板

满足后，在条目中标记：
```markdown
---
status: best_practice
since: 2026-05-25
verified_in: [project-a, project-b, project-c]
stability: high
---
```

## 七、TL;DR 原则

- **稳定知识用缓存**（算法、设计、原则）
- **变动技术要验证**（API、框架、模型）
- **检索后要蒸馏**（不复制粘贴）
- **过时要标记**（不删除，指向替代方案）
- **验证过的要升级**（active → verified → best_practice）
