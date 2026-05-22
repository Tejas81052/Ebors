# Effective Browser v10 — design handoff

This folder bridges the **paper-theme prototype** (`Effective Browser.html`) back to the Kotlin app. Drop files in, rebuild, no code refactor needed beyond two small `MainActivity.kt` additions.

---

## Drop-in mapping

| Prototype | Android target | Status |
|---|---|---|
| `tokens.css` light palette | `app/src/main/res/values/colors.xml` | **replace** with `handoff/res/values/colors.xml` |
| `tokens.css` ink palette | `app/src/main/res/values-night/colors.xml` | **replace** with `handoff/res/values-night/colors.xml` |
| Top chrome + bottom nav | `app/src/main/res/layout/activity_main.xml` | **replace** with `handoff/res/layout/activity_main.xml` |
| Address pill background | new drawable | **add** `handoff/res/drawable/bg_address_pill.xml` |
| Tab-count badge | new drawable | **add** `handoff/res/drawable/bg_tab_badge.xml` |
| Host/path two-tone text | `MainActivity.kt` | **merge** `handoff/MainActivity.patch.kt` (two routines) |

Every existing view ID is preserved — `MainActivity.kt` compiles against the new layout without rename or refactor.

---

## What actually changed visually

### Chrome
1. **Address pill** swaps `MaterialCardView` for a `FrameLayout + shape drawable`. The shape paints a 1dp warm stroke and surface fill; same 22dp corner radius (height/2) as before. One fewer view in the hierarchy.
2. **URL text** is monospace 13sp with a Spannable: bold host, faint path. Applied on every `onPageFinished` / tab switch; cleared when the EditText is focused so editing is unimpeded.
3. **Refresh icon** retints from `browser_icon` → `browser_text_2` so it reads as secondary chrome, not a primary action.
4. **Progress hairline** insets 12dp on both sides so it lives inside the chrome margin rather than running edge-to-edge.

### Bottom nav
1. **Hairline** 1dp `browser_stroke` separates nav from content (replaces shadow elevation).
2. **Caption** prepends an accent terracotta dot via a Spannable, sized in monospace 11sp at 0.02em tracking. New routine `renderBrowserCaption()` in the patch.
3. **Tab count** moves onto a soft `bg_tab_badge` chip — a 4dp-radius pill in `browser_surface_muted` — so it reads as a count, not a free-floating glyph.
4. **Touch target** grows 48 → 52dp, padding 12 → 14dp.

### Color system
All semantic names kept; only hex values changed. Adds three new tokens used by the new chrome:

```
browser_accent_soft  — accent at ~14% over paper, for tinted cards
browser_on_accent    — ink color on solid accent fills
browser_text_2       — secondary ink (subtitles, secondary icons)
browser_faint        — mono metadata (URL path)
```

`browser_chip_bg` is aliased to `browser_accent_soft` so any existing references to it (chip styling in `bookmarks` / `downloads`) keep working without a search-and-replace.

---

## What's *not* in this handoff (yet)

These prototype screens map to existing activities but weren't included as XML rewrites — happy to do them next:

- **Tabs sheet** (`dialog_tab_switcher.xml` + `item_tab.xml`) — segmented Regular/Private, card grid with thumbnails, private cards on ink surface.
- **Library** (`activity_bookmarks.xml` + `item_bookmark.xml`) — Bookmarks/History segmented tabs, big serif title, mono hostnames.
- **Settings** (`activity_settings.xml` + `item_settings_switch.xml`) — section headers in serif italic, icon-leading rows, paper-warm dividers.
- **Menu** — currently a `PopupMenu`. The prototype uses a 4×2 quick-action grid + list bottom sheet; would become a `BottomSheetDialogFragment` with a new `sheet_browser_menu.xml`.
- **Reader mode** (`reader.html`) — pure CSS replacement, drop-in for `app/src/main/assets/reader.html`.

---

## Type recommendation

The prototype uses Instrument Serif + Geist + JetBrains Mono. On Android, `fontFamily="monospace"` already gives a serviceable mono. For the serif moments (settings section headers, library title), pull a font into `res/font/` — `instrument_serif.ttf` for display, fall back to `serif` for the activity titles. Body remains the system sans.

---

---

## Welcome flow (`activity_welcome.xml`)

Three-page intro: Welcome / Privacy promise / Default browser. Re-skin only — every existing view ID and string reference is preserved, so `WelcomeActivity.kt` compiles unchanged (one optional patch adds the step indicator + promise binding).

### Drop-ins

| File | Destination | Status |
|---|---|---|
| `handoff/res/layout/activity_welcome.xml` | `app/src/main/res/layout/` | **replace** |
| `handoff/res/layout/welcome_header.xml` | `app/src/main/res/layout/` | **add** |
| `handoff/res/layout/welcome_header_inverse.xml` | `app/src/main/res/layout/` | **add** |
| `handoff/res/layout/welcome_promise_row.xml` | `app/src/main/res/layout/` | **add** |
| `handoff/res/drawable/ill_welcome_paper.xml` | `app/src/main/res/drawable/` | **add** (hero on page 1) |
| `handoff/res/drawable/ill_welcome_default.xml` | `app/src/main/res/drawable/` | **add** (hero on page 3) |
| `handoff/res/drawable/ic_welcome_check.xml` | `app/src/main/res/drawable/` | **add** |
| `handoff/res/drawable/bg_welcome_*.xml` (×9) | `app/src/main/res/drawable/` | **add** |
| `handoff/strings.welcome.xml` | merge into `values/strings.xml` | **add 8 new keys** |
| `handoff/WelcomeActivity.patch.kt` | merge into `WelcomeActivity.kt` | **add 2 routines** |

### What changed visually

1. **Step indicator** (top-right of each page) — 3 dashes; the active one is filled accent and 28dp wide, inactive ones are 14dp neutral.
2. **Brand line** (top-left) — Ebros logo monogram tile + small mono wordmark on every page.
3. **Hero illustration on pages 1 & 3** — `ill_welcome_paper` (layered paper sheets with a folded terracotta corner and a bookmark ribbon) and `ill_welcome_default` (a phone with the Ebros launcher icon haloed in dashed accent).
4. **Page 2 promises** are now four cards with check chips + inline title/body. The old single multiline TextView (`@string/welcome_privacy_bullets`) is no longer referenced — you can delete the key.
5. **Page 3 finish-line** — solid accent panel across the top (with the wordmark + headline inverted on it), paper bottom with the hero and CTAs. Visually rewards the user for finishing the flow.
6. **Buttons go pill-shaped** (28dp radius) and gain a small forward arrow drawable.

### Kotlin changes (small)

`WelcomeActivity.patch.kt` adds two helpers:
- `refreshSteps(activeIndex: Int)` — paints the active dot (wider + accent fill) whenever the ViewFlipper changes child. Call from your existing `showNext()` / `showPrevious()` paths.
- `bindPromises()` — populates the four promise card titles & bodies on page 2. Call once in `onCreate` after `setContentView`.

Both routines tolerate the include-layout duplication (the header layout is `<include>`d on every page so the same view IDs appear 3×; the helper walks the tree and updates all instances).

### String migration

Keep your existing welcome strings as-is. The patch adds 8 new keys:
- `welcome_promise_1_title` / `_body` … `welcome_promise_4_title` / `_body`

If you want the suggested copy upgrades (more vibrant tone), `handoff/strings.welcome.xml` also includes `*_new` variants of the existing keys. Either rename them and overwrite, or just copy the text into your existing keys.

### Light/dark

The new drawables reference semantic color tokens (`@color/browser_accent`, `@color/browser_surface`, etc.) which already have night variants in `values-night/colors.xml` — so the new welcome flow respects system dark mode without per-drawable variants.
