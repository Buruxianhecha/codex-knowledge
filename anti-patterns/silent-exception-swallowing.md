---
confidence: 1.0
first_seen: pdf-to-excel (2026-05-24)
severity: high
---

# Silent Exception Swallowing（静默吞异常）

## 症状
`except: pass` 或 `except Exception: pass` 在关键路径中出现，错误被完全隐藏。

## 腐烂路径
```
某个引擎失败 → except: pass → 静默跳过
以为是正常行为 → 继续开发其他功能
几个月后发现数据不准 → 回溯时无日志 → 无法定位根因
```

## 检测信号
- `except: pass`
- `except Exception: pass`（比裸 except 好一点但同样糟糕）
- OCR/API 调用的异常处理没有任何日志

## 修复
最低限度：`except Exception as e: logger.warning(f"Engine X failed: {e}")`
推荐：区分可恢复和不可恢复错误，前者降级，后者上报。

## 已知案例
- pdf-to-excel: ocr_paddle 和 ocr_gpt 调用用裸 except: pass
