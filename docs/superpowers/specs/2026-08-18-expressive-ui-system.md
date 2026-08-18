# HuskySU Expressive UI system

**Branch:** `husky-lkm`  
**Theme:** `MaterialExpressiveTheme` + `MotionScheme.expressive()` (unchanged)

## Shared composables (`ui/component/material/ExpressiveCards.kt`)

| Composable | Use |
|------------|-----|
| `ExpressiveHeroCard` | Page-top colored status/context (`extraLarge`, 48.dp icon, `headlineSmall`) |
| `ExpressivePrimaryBar` | Full-width ~56.dp primary / tonal CTA with optional icon |
| `ExpressiveNoticeCard` | Large warning/tip block with optional icon + action |
| `ExpressiveSectionTitle` | Emphasized section label |

## Rules

1. Prefer these over ad-hoc `Surface` + `ListItem` stacks.
2. Read-only rows must not use empty `onClick` ripples.
3. Busy state disables **all** related actions in the same card.
4. Status colors: working → `secondaryContainer`; danger → `errorContainer`; update → `primaryContainer`; tip → `tertiaryContainer`.

## Cleanup (PR0)

- Removed unused `home_support_*` strings
- Removed dead `miuixMonet` and floating-bottom-bar prefs / CompositionLocals
