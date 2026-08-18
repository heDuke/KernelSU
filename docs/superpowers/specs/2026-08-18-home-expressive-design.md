# Home screen — max Material 3 Expressive

**Branch:** `husky-lkm`  
**Scope:** `HomeMaterial.kt` (+ few `strings.xml` entries). No theme rewrite.

## Goals

Make the Home pager the most Expressive surface in HuskySU: bold color blocks, emphasized type, larger radii, full-width action bars. Stay slim: drop Donate; keep Learn; merge LKM + OTA.

## Layout (top → bottom)

1. **Status hero** (first)
   - Full-width `TonalCard` with `shapes.extraLarge`
   - Color: working → `secondaryContainer`; not installed / unsupported → `errorContainer`
   - Large leading icon (~40–48.dp)
   - Title: `headlineSmall` / emphasized weight
   - Summary: `bodyLarge`
   - Mode tags (LKM / safe / jailbreak) as chips under or beside title
   - Still clickable → install when allowed

2. **Husky 更新** (merged LKM + OTA)
   - One `TonalCard`, `shapes.extraLarge`
   - Available update → `primaryContainer`; else `surfaceBright`
   - Section title: `titleLargeEmphasized` (“Husky 更新” / existing husky strings)
   - Upper zone: status summary + **full-width** `FilledTonalButton` / `Button` (Extended-FAB height ~56.dp, icon + label)
   - While `huskyBusy`: disable actions + inline `CircularProgressIndicator`
   - `HorizontalDivider` when OTA slot is shown
   - Lower zone (if `showOtaSlotCard`): OTA summary + full-width tonal/outlined action

3. **Warnings / Manager update** — keep existing `WarningCard` / `UpdateCard` behavior; prefer `extraLarge` shape if easy

4. **Device info**
   - `SegmentedColumn` titled (e.g. device / system), labelSmall + bodyMedium rows, tighter than current 16.dp gaps

5. **Learn KernelSU**
   - Keep; style as clickable `TonalCard` / segmented row with `OpenInNew` trailing icon; `titleMediumEmphasized`

6. **Donate** — remove

## Non-goals

- Settings / SuperUser polish
- New shared theme tokens beyond using existing `MaterialTheme.shapes` / typography
- Changing husky Release / OTA business logic

## Verification

Install on Pixel 8 Pro (`husky`): Home shows hero + merged update card; Checking shows progress; Donate gone; Learn remains; screenshot light+dark if time.
