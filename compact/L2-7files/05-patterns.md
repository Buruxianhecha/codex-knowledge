# Patterns 压缩版 v4

## 多引擎择优

多个引擎都能产出时并行执行，按可量化质量/成本评分选择，而不是模糊的“失败后降级”。

## 输出质量门控

结果进入用户输出前检查结构、填充率、异常和完整性；不合格时留空或降级。

## Auth Provider 边界

UI -> AuthService/AuthStore -> MockProvider/RealProvider。外部身份映射内部 userId，secret/token 只在服务端。

## 单调存档事务

读取更晚快照 -> 拒绝旧页 -> 纯状态转换 -> 拒绝倒退 -> 同步写盘 -> 更新 UI。

## 单文件 VM 测试壳

提取 HTML 内联脚本，在 Node vm 注入最小 DOM/localStorage 和可控随机数。覆盖状态行为，不替代视觉/真机。

## 证据账本

材料表记录来源、日期、定位和哈希；命题表记录支持、反证、允许措辞和禁止升级。多智能体共用同一 Schema。
