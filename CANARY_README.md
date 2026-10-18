# HuskySU UAPI 6 Canary 工程候选（v32766）

仅适用于 Pixel 8 Pro / CANARY ZP11.260918.007，并且实际运行内核为：

- 完整版本：`6.1.172-android14-11-g3fc92ed75e44-ab16169542`
- KMI：`android14-6.1`
- 内核页大小：4096 bytes（4 KB）

此 `.ko` 对应接口 6。源码：`2d916ab754975ade73036dca3233b9b6584fd1a1`。
配套正式 Manager：[husky-v32766](https://github.com/heDuke/KernelSU/releases/tag/husky-v32766)。

这次修复允许当前仍是接口 2、已获管理器授权且能取得 Root 的设备，通过审核过的启动镜像写入流程升级 LKM。普通模块安装/开关和权限配置仍要求接口匹配；仅更新 APK 不会替换正在运行的内核模块。

App 内路径：更新上述正式 APK → 首页“刷写/更新”或“升级内核模块” → 选择“使用本地 LKM 文件”导入本目录带 UAPI6/v32766 的 `.ko` → 核对设备、目标文件、分区/槽位和备份信息后确认。进入新 LKM 需要重启。最终以 App 和后端的实际目标校验结果为准。

文件是 LKM，不是模块 ZIP。此候选不是 16 KB 内核版本，也不适用于其他完整内核版本。构建号相同仍须检查实际页大小和内核版本；不提供强制加载或修改 vermagic 绕过的路径。

状态：**offline_checked_candidate**，不是实机验证通过的适配。已检查 230 个符号引用、149 个符号版本、BTF、生产 Manager 包名/证书；仅比较了 1 个共同 CFI 类型，不能据此保证完整 CFI/私有 ABI 或实际加载行为。加载器真实 ELF 回归与审计拒绝测试通过。

尚未验证实际加载、Root/SELinux/hooks、preinit、冷启动、休眠、OTA 与恢复行为。未执行设备刷写；独立实验加载器默认仍关闭。若设备信息不匹配或预检失败，保留错误/日志以进一步排查。

`canary-uapi6-audit.json`、`canary-uapi6-manifest.json` 和 `CANARY_SHA256SUMS` 提供来源、检查结果与文件摘要。

此工程附件不在正式版自动 LKM 更新清单中，需手动选择本地文件并通过实际目标核验。正式 APK 与通用 KMI 文件仍使用原有 manifest.json / SHA256SUMS；此处工程附件使用单独的 CANARY_SHA256SUMS。
