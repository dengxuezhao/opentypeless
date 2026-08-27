# Task Report: RIM-005 candidate toolbar substitution

## Result

DONE

## Scope

- Implemented: 中文组词期间让稳定候选页替代 QWERTY 的 `Auto / 麦克风 / 更多` 工具行；候选清空或提交后恢复
  工具行；补齐幂等可见性、系统选中 IME 和资源门禁回归。
- Not implemented: 字母几何、底部垫高、中英文全半角快捷符号、Voice 新按钮、中文状态回车提交英文；未修改或打包
  真实小鹤个人资源。

## Changes

- `KeyboardCandidateBar.java`: show/clear 转换发布无能力的候选 surface 可见性事件，同一状态不重复通知。
- `OpenTypelessImeService.java`: 候选可见时隐藏既有工具行，候选清空时恢复；不新增容器或键盘高度。
- Candidate architecture/instrumentation tests: 锁定一次 show/clear 对应 `[true, false]`，禁止移除工具行替换接线。
- Test Host system-IME test: 候选出现时断言“更多语音键盘操作”消失，次选提交后断言恢复。
- Rime resource policy: 审核并登记本次四个变更 APK 的精确哈希；真实小鹤资源仍为 0。

## Architecture

- contracts: 候选条与工具行是同一高度位置的互斥 surface；candidate identity、generation、page revision 与 pending
  交互门禁保持不变。
- state changes: `hidden -> visible` 隐藏工具行，`visible -> hidden` 恢复工具行；连续页更新和重复 clear 无额外转换。
- migration: 无。
- feature flag: 沿用现有 Route-A QWERTY/Rime 路径，无新增 flag。

## Security & privacy

- data sent/stored: 无网络、无新增持久数据；设备验收使用 2,191-byte 合成临时 Rime 包。
- permissions/components: 无新增权限、导出组件、Provider、依赖或 editor capability。
- threat considerations: 所有既有敏感字段、no-learning、错误 target/generation 和 pending 页 fail-closed 规则不变；
  可见性回调只操作既有 View。

## Tests actually run

| Command | Result | Notes |
|---|---|---|
| candidate/Rime/toolbar focused Python suites | PASS | 48/48 |
| targeted Gradle unit + app/test-host AndroidTest compile | PASS | 57 tasks |
| `KeyboardCandidateBarInstrumentedTest` | PASS | API35 arm64 emulator 8/8 |
| actual-librime imported candidate test | PASS | API35 arm64 emulator 1/1；三页和次选链路 |
| system-selected Test Host Rime candidate test | PASS | API35 arm64 emulator 1/1；候选时隐藏工具行，提交后恢复 |
| `scripts/verify_android.sh all` | PASS | 120 script tests、271 architecture tests、191 Gradle tasks、Release lint、五个 APK 和资源扫描 |
| Xiaomi / 真实小鹤 `xkvi` | NOT RUN | 当前只连接 API35 arm64 emulator；不以合成候选冒充个人词库 |

第一次 clean `all` 已成功完成 191-task 构建，随后按设计以 `UNREVIEWED_ARTIFACT` 拒绝两个新 product APK；审核并登记
四个变更 APK 后，第二次 clean `all` 全部 PASS。资源策略单测首次从仓库根目录直接启动时因 Python 模块搜索路径返回
`ModuleNotFoundError`，改在 `scripts/` 目录按项目约定重跑后 **37/37 PASS**；产品代码未为这两个门禁结果改变行为。

## Evidence

- 外部 ADB 真实触控 idle 截图：`Auto / 麦克风 / 更多` 可见；SHA-256
  `6c68bfada497a7daa2739942ee1435b1426d487ee266856a45b8e554289a3060`。
- 同一输入窗口输入 `ni` 后截图：同一位置只显示 `1 甲 / 2 乙 / 3 丙 / 4 丁 / 下一页`，工具行不可见且键盘未增加
  高度；SHA-256 `6f6a577f238faae49d694bd0282136121d01948ae6af19e9a19696221dc4ea4f`。
- Debug APK: 65,523,463 bytes，SHA-256
  `055fba52c8f6c71994b6a564d52fcbc72463dc9685460343cc9fdada2dfb6043`。
- unsigned Release APK: 63,628,769 bytes，SHA-256
  `09dd4e244453421513d6456e0e702107e8cf2027e5374fe8ad9ed9f53cb88af1`。
- app AndroidTest APK: 1,103,906 bytes，SHA-256
  `50af81d31ee32e73b0f4d8877349d79f387bd495967dadf2dc663a6e1607b16b`。
- test-host Debug/AndroidTest APK: SHA-256
  `908d7582c6c668466311aeb2c124167eb5985900abcece78e499bc6970e1a4e3` /
  `cd2ad2680561f1ad54c5a82cb7e7cdcacf5685bb776f8ff1f40e8018d10fd6f8`。
- 验收后已删除合成导入包、active resource store 及其精确 `no_backup/rime_user_data_v1` 测试 UserDB/cache，默认输入法
  读回 `com.android.inputmethod.latin/.LatinIME`。

## Risks

- 当前设备截图使用合成候选，只证明布局、分页、次选和恢复链路；真实小鹤候选内容仍需 Xiaomi 重新连接后验收。
- 本切片不调整键帽几何或底部高度，用户先前列出的其余五项仍是独立后续任务。

## Rollback

- 回滚本交付提交即可；基线 parent 为 `ac0ed1541b3866daff7ceec91758704bb5f66c31`。

## Follow-ups

- KBD-002
- KBD-006
- KBD-009
- KBD-015
- RIM-004

## Git

- branch: `codex/personal-ime`
- commit: 本报告所在交付提交；推送后以 `git rev-parse HEAD` 与 `git ls-remote` 精确核对。
- worktree status: 提交后仅保留用户既有未跟踪文件 `docs/2026-08-19-session-handoff.md`。
