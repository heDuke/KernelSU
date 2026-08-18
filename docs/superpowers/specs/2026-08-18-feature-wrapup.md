# Feature wrap-up (2026-08-18)

## Shipped this cycle

| ID | Feature | Notes |
|----|---------|-------|
| UI | Full-app M3 Expressive | Tabs + subflows + ModuleRepo re-entry |
| F1a | Environment check | BL / AVB / LSP·Zygisk / SSL-unpin scan; settings entry |
| F1a fix | i18n + SELinux | Localized item copy; `getenforce` |
| F1b | Home env summary + align | Opens EnvCheck; align updates LKM only (no auto OTA slot) |
| F2 | Module pending + batch | Multi-select ≤5 → sequential download → `FlashModules` |

## Deferred

| ID | Reason |
|----|--------|
| F1a+ Play Integrity live | Needs Google Cloud project / API credentials |
| F3 SU risk summary | Needs Sulog spike / Go-NoGo |
| F4 Release history picker | Not started this wrap-up |

## How to publish Manager

`push` builds only. Create a GitHub Release with:

```bash
gh workflow run husky-release.yml -R heDuke/KernelSU --ref husky-lkm -f create_release=true
```
