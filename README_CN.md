# HuskySU

**HuskySU** 是面向 Pixel 8 Pro（`husky`）的 [KernelSU](https://github.com/tiann/KernelSU) 维护版：LKM root（`android14-6.1`）、Material 3 Expressive 管理器，以及通过 GitHub Releases 分发 `.ko` + APK。

[English](README.md) | [简体中文](README_CN.md)

| | |
|--|--|
| **应用** | HuskySU |
| **包名** | `me.weishu.kernelsu.husky.fork` |
| **分支** | [`husky-lkm`](https://github.com/heDuke/KernelSU/tree/husky-lkm) |
| **KMI** | `android14-6.1` |
| **CI** | [Husky LKM Release](https://github.com/heDuke/KernelSU/actions/workflows/husky-release.yml) |
| **发布** | [`husky-v*` 标签](https://github.com/heDuke/KernelSU/releases) — 资源：`HuskySU.apk`、`android14-6.1_kernelsu.ko` |

## 安装

1. 打开某个 **`husky-v*`** Release，下载 `HuskySU.apk`；首次刷入还需 `android14-6.1_kernelsu.ko`。
2. PC 首次 root：见 **[docs/husky.md](docs/husky.md)** — 用官方同版本 `init_boot` 打补丁后 `fastboot flash init_boot`。
3. 安装 APK。之后可在应用内检查 husky Release /「一键对齐」更新 LKM。

请先卸载旧的 `me.weishu.kernelsu` / `.pr` 管理器（包名不同）。

> **发版说明：** `git push` 只会跑 CI **构建**，**不会**自动创建 GitHub Release。发布请到 Actions → **Husky LKM Release** → Run workflow，并勾选 **`create_release`**。

## 本 fork 功能

### Root / 更新
- 单 KMI CI：`android14-6.1` LKM + 与生产证书匹配的 APK
- 应用内检查 husky Releases；有 root 时可一键更新 LKM
- 系统 OTA 后的 **非活动槽** LKM 安装（需手动确认；「对齐」不会自动刷槽）
- 生产主证书槽绑定（`KSU_EXPECTED_SIZE` / `HASH`，不要用 `SIZE2`/`HASH2`）

### 管理器界面
- 仅 Material 3 Expressive（已移除 Miuix）
- 外观：跟随系统 / 浅色 / 深色 + 可选动态取色（关闭时为 husky 色 `#1A73E8`）
- 精简安装面；设置 / 模块页可进入模块仓库
- 推荐模块；**待处理**卡片支持多选批量安装/更新（最多 5 个）
- 关于页：HuskySU 品牌与上游鸣谢

### 环境检测
- 设置 → **环境检测**（首页亦有摘要卡）
- Bootloader / Verified Boot（AVB）、KernelSU/LKM 状态、管理器↔驱动是否一致
- Zygisk / LSPosed / 推荐缺口；常见 SSL unpin 辅助模块静态扫描
- Play Integrity：占位（需自行配置 Cloud 项目后才可真接）
- 可复制报告；「对齐」只更新 LKM（不自动刷非活动槽）

## 构建

```text
分支:   husky-lkm
工作流: Husky LKM Release
        workflow_dispatch + create_release=true
密钥:   KEYSTORE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
```

设备说明：[docs/husky.md](docs/husky.md)  
功能收尾记录：[docs/superpowers/specs/2026-08-18-feature-wrapup.md](docs/superpowers/specs/2026-08-18-feature-wrapup.md)

## 鸣谢

基于 **[tiann/KernelSU](https://github.com/tiann/KernelSU)**。上游完整致谢见：[docs/README_CN.md § 鸣谢](docs/README_CN.md#鸣谢) / [docs/README.md § Credits](docs/README.md#credits)。

## 许可证

与上游 KernelSU 相同：

- `kernel/` — GPL-2.0-only
- 其余 — GPL-3.0-or-later

---

上游文档镜像：[`docs/`](docs/)。
