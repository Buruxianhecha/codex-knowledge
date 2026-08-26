---
status: verified
confidence: 0.98
reuse_count: 3
last_used: 2026-08-25
verified_in:
  - doubao-local-environment
  - shiguang-album-test-site
  - minimal-tv-clock
expires_after: none
cross_refs:
  - lessons/user-perspective-verification.md
  - projects/2026-06-30-doubao-local-environment-repair.md
  - projects/2026-08-12-shiguang-album.md
  - mistakes/doubao-script-success-without-client-acceptance.md
  - mistakes/shiguang-test-site-overstated-as-production-ready.md
---

# 应用接纳高于命令成功

> 子步骤成功只能证明子步骤。任务完成必须由拥有最终结果的系统和用户路径确认。

## 为什么需要这条经验

“退出码 0”“构建成功”“文件已生成”“Git push 成功”都是真实证据，但它们回答的是不同问题。把低层证据提升为高层结论，会产生最危险的误报：每一句局部事实都是真的，总体结论却是假的。

## 完成层级

| 层级 | 证据 | 不能自动推出 |
|------|------|--------------|
| L1 命令 | 退出码、标准输出 | 产物正确 |
| L2 产物 | 文件存在、语法可解析 | 宿主能加载 |
| L3 集成 | 构建/测试/接口通过 | 用户路径正确 |
| L4 宿主接纳 | 客户端显示可用、服务注册成功 | 重启后仍保持 |
| L5 用户验收 | 真实入口、真实操作、需求一致 | 长期稳定和全部边界 |
| L6 持久稳定 | 刷新、重启、并发、恢复、时间观察 | 未来版本永不过期 |

报告必须写“验证到 Lx”，不能用一个“完成”覆盖所有层。

## 三个验证案例

### 豆包本地环境

`postinstall.py` 的 UTF-8 修复和 `prepare.ps1` 成功属于 L1/L2。豆包客户端没有稳定接纳环境，L4 未通过，因此任务按失败记录。

### 拾光相册

Lint、构建和部分核心交互通过，说明 L2/L3 的很大一部分成立。深度验收仍发现真实文件矩阵、跨设备恢复、服务端授权和真机触摸未完成，不能升级为正式产品。

### 极简电视时钟

网页和仓库存在，但用户明确需求是“单文件、只显示 HH:MM、不加其他信息”，远端版本不符合。即使功能正常，需求一致性仍未通过 L5。

## 行动规则

1. 开始任务前写出最终拥有者：脚本、构建系统、宿主应用、远端仓库还是用户。
2. 每个验证动作标注它覆盖的层级。
3. 最终声明使用最弱已通过层级，不使用最强希望层级。
4. 如果无法进入真实宿主或用户路径，明确写“集成未验证”。
5. 对会自动重建、刷新或同步的系统，必须增加重启/刷新验证。

## 与“用户视角验收”的关系

`lessons/user-perspective-verification.md` 定义价值原则，本条把它转为证据层级。两者共同约束：未验证不是“可能已经完成”，而是“当前不能声称完成”。

## 标签

#verification #delivery #integration #user-acceptance #evidence
