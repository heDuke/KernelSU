# Home Expressive UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the Home pager to max Material 3 Expressive: status hero, merged Husky update card with full-width actions, SegmentedColumn device info, keep Learn, remove Donate.

**Architecture:** UI-only changes in `HomeMaterial.kt` plus two string keys. Reuse `TonalCard(shape = MaterialTheme.shapes.extraLarge)`, `SegmentedColumn` / `SegmentedListItem`, M3 `FilledTonalButton` / `Button` at full width. No ViewModel / business-logic changes.

**Tech Stack:** Jetpack Compose Material3 Expressive, existing HuskySU manager components.

## Global Constraints

- Package / branding stay HuskySU; no Donate card.
- Do not change husky Release check / OTA install actions — only presentation.
- Prefer existing strings; add `husky_update_section_title` and `home_device_section` only.
- Verify on device via adb screenshot after install (local assemble or CI apk).

---

### Task 1: Strings

**Files:**
- Modify: `manager/app/src/main/res/values/strings.xml`
- Modify: `manager/app/src/main/res/values-zh-rCN/strings.xml`

- [ ] **Step 1: Add section titles**

English:
```xml
<string name="husky_update_section_title">Husky updates</string>
<string name="home_device_section">Device</string>
```

Chinese:
```xml
<string name="husky_update_section_title">Husky 更新</string>
<string name="home_device_section">设备信息</string>
```

Place near existing `husky_update_card_title` / `home_manager_version`.

- [ ] **Step 2: Commit**

```bash
git add manager/app/src/main/res/values/strings.xml manager/app/src/main/res/values-zh-rCN/strings.xml
git commit -m "chore(manager): strings for expressive Home sections"
```

---

### Task 2: Home pager layout + Status hero + merged update card

**Files:**
- Modify: `manager/app/src/main/java/me/weishu/kernelsu/ui/screen/home/HomeMaterial.kt`

**Interfaces:**
- Consumes: `HomeUiState` (`showOtaSlotCard`, `huskyBusy`, `canDirectInstallLkm`, husky update fields), `HomeActions` (unchanged)
- Produces: `StatusCard` hero, `HuskyUpdateCard` (replaces `HuskyLkmCard` + `OtaSlotCard`), removes `DonateCard` call

- [ ] **Step 1: Reorder `HomePagerMaterial` content**

Order: StatusCard → HuskyUpdateCard → warnings/UpdateCard (unchanged) → InfoCard → LearnMoreCard → Spacer. Remove `DonateCard(...)`. Remove separate `HuskyLkmCard` / `OtaSlotCard` calls.

- [ ] **Step 2: Rewrite `StatusCard` as expressive hero**

Use `TonalCard(shape = MaterialTheme.shapes.extraLarge, containerColor = …, onClick = …)` with Column padding 20.dp, Icon size 48.dp, title `headlineSmall` + FontWeight.Medium, summary `bodyLarge`, tags in a Flow/Row under title. Keep existing color rules and click→install behavior.

- [ ] **Step 3: Replace LKM+OTA with `HuskyUpdateCard`**

Single card `extraLarge`; title `stringResource(R.string.husky_update_section_title)` with `titleLargeEmphasized`. Upper block = current LKM summary/actions; when `huskyBusy`, show `CircularProgressIndicator` (24.dp) beside/instead of enabling primary action. Primary actions: `Modifier.fillMaxWidth().height(56.dp)` `Button` or `FilledTonalButton` with leading icon (`Icons.Outlined.SystemUpdate` / `Download`). If `showOtaSlotCard`, `HorizontalDivider` then OTA summary + full-width `FilledTonalButton` for inactive slot (reuse confirm dialog). Delete old `HuskyLkmCard` / `OtaSlotCard` / `DonateCard` composables.

- [ ] **Step 4: Restyle Learn + Info**

`LearnMoreCard`: `extraLarge` TonalCard, `titleMediumEmphasized`, trailing `Icons.Outlined.OpenInNew`.  
`InfoCard`: wrap rows in `SegmentedColumn(title = stringResource(R.string.home_device_section))` using non-clickable `SegmentedListItem` (headline = label, supporting = value) OR keep TonalCard with labelSmall + bodyMedium and 8.dp spacers — prefer SegmentedColumn per spec.

- [ ] **Step 5: Fix previews** so they still compile (StatusCard / pager previews without Donate).

- [ ] **Step 6: Commit**

```bash
git add manager/app/src/main/java/me/weishu/kernelsu/ui/screen/home/HomeMaterial.kt
git commit -m "feat(manager): expressive Home hero and merged Husky update card"
```

---

### Task 3: Device verify

**Files:** none (adb)

- [ ] **Step 1: Build/install**

Prefer local `./gradlew :app:assembleRelease` if SDK ready; else push commit and wait for husky-release, or install debug. `adb install -r …`

- [ ] **Step 2: Screenshot Home**

Force-stop, launch, Home tab, `screencap`. Confirm: no Donate; Status hero; one Husky updates card; Learn present; Info segmented.

- [ ] **Step 3: Tap Check update once** and confirm busy progress appears (no crash).
