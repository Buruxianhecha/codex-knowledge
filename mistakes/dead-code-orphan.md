---
status: active
since: 2026-05-25
lesson: lessons/v1-hardening.md (硬化清单第1项)
---
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

