---
status: active
confidence: 0.9
reuse_count: 0
last_used: 2026-05-25
verified_in: []
cross_refs:
  - lessons/user-perspective-verification.md (该项目的最终交付验证过程强化了用户视角验收原则)
---

# deepseek-video — HyperFrames 宣传视频

## 基本信息
- **创建日期**: 2026-05-25
- **状态**: v1 已完成（有 MP4 渲染输出）
- **路径**: D:\Projects\deepseek-video

## 项目目标
为 DeepSeek API 平台制作一段自动化宣传视频，展示其模型矩阵和性价比优势。

## 技术栈

| 层次 | 技术 | 用途 |
|------|------|------|
| 视频框架 | HyperFrames (HeyGen) | HTML→MP4 视频渲染引擎 |
| 动画 | GSAP (timeline) | 逐帧动画控制 |
| 样式 | Tailwind v4 (browser-runtime) | 动态样式 |
| TTS | Kokoro | 文字转语音旁白 |
| 转录 | Whisper | 词级时间戳转录 |
| 发布 | HyperFrames CLI | 渲染 + 发布 |

## 实现方案
- 单一 `index.html` 作为根时间线（root timeline）
- GSAP timeline 控制所有动画节奏，注册到 `window.__timelines`
- 音频轨道分离：旁白 + BGM 混合
- 每个元素带 `data-start` / `data-duration` / `data-track-index`
- 多次迭代渲染（4 个 MP4 版本）

## 最终结果
- 成功渲染多版 MP4
- 脚本为 DeepSeek 平台中文宣传（5 句核心卖点）
- 渲染输出：`renders/deepseek-video_2026-05-25_*.mp4`

## 关键教训

### 交付验证
该项目最终交付过程强化了"用户视角验收原则"——用户对最终视觉效果和品牌一致性有严格要求，仅"代码正确"不足以交付。

### HyperFrames 特定知识
1. 每个定时元素必须有 `class="clip"` 控制可见性
2. `npm run dev` 是常驻服务器，必须后台运行
3. 不允许 `Date.now()`、`Math.random()`、网络请求（必须确定性）
4. 视频元素用 `muted` + 独立 `<audio>` 标签

## 关联
- lessons/user-perspective-verification.md (交付验证原则)
- anti-patterns/deliver-without-verification.md (不要仅凭代码正确就交付)

## 标签
#video #hyperframes #animation #deepseek #marketing
