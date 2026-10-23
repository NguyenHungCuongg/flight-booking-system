# SkyLine — Design System

> Night sky over cool paper. A heavy sans-serif wordmark, blue-grey neutrals, and one warm terracotta accent that marks every action and every price.

**Theme:** auto (follows `prefers-color-scheme`; light and dark are both first-class)

Visual direction for the landing (measurements, layout) is inspired by the public website of Hyer Aviation. All brand names, copy, logos, fonts and imagery in this document are SkyLine's own or freely licensed — see [Assets & Licensing](#assets--licensing).

SkyLine reads as a calm travel product: a night-sky hero with a sculptural jet, then app screens on a cool, blue-tinted paper canvas where white cards float on soft tinted shadows. The palette is one family of blue-grey neutrals plus a single terracotta accent. The accent is the brand's voice in the app: primary buttons, the active nav item, prices and totals, focus rings, selected states. Status (issued, waiting, cancelled) has its own small semantic palette so a list of bookings can be scanned by colour. Typography is Be Vietnam Pro, drawn for Vietnamese from the start, so stacked diacritics stay clean at display sizes.

**Revision 2026-10-23.** The first version was strictly monochrome (ink on white, clay on one card per page, no shadows, square panels). In dark mode every app screen became navy-black with white text and nothing else, which made screens hard to scan. This revision promotes terracotta to the system accent, adds status colours, tinted surfaces with elevation, rounded cards and inputs, and swaps Inter for Be Vietnam Pro. The landing (C-01) keeps its own CSS (`landing.css`); only its dark palette and the main search button were aligned with these tokens.

## Tokens — Colors

Role tokens live in `frontend/src/app/globals.css` (`@theme`, overridden under `prefers-color-scheme: dark`). Use the role names (`bg-surface`, `text-accent`…), never raw hex, in app code.

| Role           | Light     | Dark           | Use                                                                     |
| -------------- | --------- | -------------- | ----------------------------------------------------------------------- |
| `ink`          | `#0e1726` | `#e8edf3`      | Primary text, headings                                                  |
| `ink-2`        | `#3a4656` | `#c3ccd7`      | Form labels, table headers                                              |
| `muted`        | `#5a6675` | `#94a1b2`      | Secondary text, hints, inactive nav (≥ 4.5:1 on paper and surface)      |
| `paper`        | `#f3f5f8` | `#0a111c`      | Page background (with two faint glows at the top, sky left, clay right) |
| `surface`      | `#ffffff` | `#111b29`      | Cards, inputs, menus, dialogs: anything that floats on paper            |
| `tint`         | `#e9eef3` | `#172435`      | Hover fills, table header row, skeletons                                |
| `line`         | `#dde3ea` | `#213044`      | Hairline dividers                                                       |
| `line-strong`  | `#c2cbd5` | `#34465f`      | Input borders, secondary button borders                                 |
| `accent`       | `#a85a3c` | `#e08a68`      | Primary buttons, active nav, prices and totals, focus ring, sliders     |
| `accent-hover` | `#8f4a2f` | `#eba085`      | Primary button hover                                                    |
| `accent-fg`    | `#ffffff` | `#0e1726`      | Text on accent (5.0:1 light, 7.0:1 dark)                                |
| `accent-soft`  | `#f7ebe5` | accent at 14 % | Active nav pill, selected chip, input focus halo, airline code chip     |

**Status colours** (badges and notices only; text colour on its own soft background):

| Tone     | Light text / soft     | Dark text / soft | Statuses                                                      |
| -------- | --------------------- | ---------------- | ------------------------------------------------------------- |
| `ok`     | `#17704a` / `#e1f3e9` | `#6fd6a0` / 13 % | ISSUED, COMPLETED, SUCCESS                                    |
| `warn`   | `#8a5300` / `#fcefd5` | `#f2bd5c` / 13 % | PENDING_PAYMENT, PENDING                                      |
| `danger` | `#b4232f` / `#fbe6e7` | `#f58c94` / 13 % | CANCELLED, REJECTED, FAILED; form errors; destructive buttons |
| `info`   | `#1f5a92` / `#e3edf8` | `#8dbcf0` / 13 % | SCHEDULED, APPROVED, REFUNDED                                 |
| neutral  | `muted` on `tint`     | same             | EXPIRED and anything unknown                                  |

The mapping lives in one place, `components/status-badge.tsx`. Status colours are semantic, not decoration: never use them for brand elements.

**Brand constants** (logo, imagery, landing): Deep Ink `#000d10`, Clay Ember `#bc7155` (the wordmark dot and landing clay band; too light for white text, so the app uses `accent` instead).

## Tokens — Typography

### Be Vietnam Pro — sole typeface · `--font-sans`

- **License:** SIL Open Font License 1.1. Source: Google Fonts.
- **Why:** designed for Vietnamese first, so stacked diacritics on capitals (Ấ, Ở, Ữ) have room at display sizes; a warmer, more distinctive grotesque than Inter while staying neutral enough for tables.
- **Loading (Next.js):** `next/font/google` → `Be_Vietnam_Pro({ subsets: ["latin", "vietnamese"], weight: ["400","500","600","700","800"], variable: "--font-sans" })`. Not a variable font, so list every weight in use.
- **Weights:** 400 body, 500 (rare), 600 labels/nav/buttons/table headers, 700 headings, prices, codes, 800 reserved for the landing.
- **Numbers:** `font-variant-numeric: tabular-nums` on `body`, so prices, times and seat counts line up.

### Type Scale (app)

| Role       | Size  | Line height | Letter spacing | Token               |
| ---------- | ----- | ----------- | -------------- | ------------------- |
| badge      | 12px  | 16px        | 0              | `--text-badge`      |
| table      | 14px  | 20px        | 0              | `--text-table`      |
| caption    | 14px  | 20px        | 0              | `--text-caption`    |
| body       | 15px  | 24px        | 0              | `--text-body`       |
| subheading | 18px  | 24px        | -0.2px         | `--text-subheading` |
| heading-sm | 22px  | 28px        | -0.3px         | `--text-heading-sm` |
| heading    | 28px  | 34px        | -0.5px         | `--text-heading`    |
| heading-lg | 34px  | 40px        | -0.9px         | `--text-heading-lg` |
| display    | 48px  | 54px        | -1.4px         | `--text-display`    |
| display-xl | 131px | 131px       | -2.62px        | `--text-display-xl` |
| hero       | 187px | 150px       | -3.74px        | `--text-hero`       |

Page titles are `heading-lg` bold and end with a period ("Chọn chuyến bay."). Line heights are written in px (`--text-*--line-height`), never unitless.

## Tokens — Spacing & Shapes

`--spacing: 1px`, so `p-16` is 16px. Common steps: 4, 6, 8, 10, 13, 16, 18, 28, 38, 80. The app is compact: card padding 16px, block gap 16–28px.

| Shape                                | Radius | Token           |
| ------------------------------------ | ------ | --------------- |
| Buttons, nav items, chips            | 1000px | `rounded-pill`  |
| Cards, dialogs                       | 14px   | `rounded-card`  |
| Inputs, inner panels, menus, notices | 10px   | `rounded-inner` |
| Badges, airline code chip            | 6px    | `rounded-badge` |

One rule, applied everywhere: **interactive = pill, containers = 14px, things inside containers = 10px, labels = 6px.** Nothing in the app is square.

### Layout

- Page max-width 1200px (`max-w-page`), side padding 16px (mobile) / 38px.
- Vertical gap between page blocks 28px; card padding 16px; bottom padding 80px.
- Sticky header (from 640px up), about 60px tall, translucent paper with backdrop blur.
- Sidebars (flight filters, C-09 price column) are cards that stick at `top-80` on desktop.

## Elevation

| Level | Token                 | Use                                                                 |
| ----- | --------------------- | ------------------------------------------------------------------- |
| 0     | none                  | Page (`paper`)                                                      |
| 1     | `--shadow-card`       | Cards, tables. A 1px ink-tinted ring plus a soft drop; no `border`. |
| 2     | `--shadow-card-hover` | Hovered clickable card, user menu, dialogs                          |
| —     | `--shadow-accent`     | Primary button only (terracotta-tinted glow)                        |

Shadows are tinted with ink (light) or near-black (dark) and fall from above. Never use pure-black `rgba(0,0,0,…)` shadows in light mode.

## Components

Shared class strings live in `components/form.tsx`; utilities `card` and `card-hi` in `globals.css`.

- **Primary button (`pillPrimary`):** pill, `accent` background, `accent-fg` text, 14px semibold, min-height 40px, padding 10px 18px, accent shadow. Hover lifts 1px and darkens; press scales to 0.98. One per view region.
- **Secondary button (`pillLine`):** pill, `surface` background, `line-strong` border, ink text; hover darkens the border and tints the fill.
- **Danger button (`pillDanger`):** pill, `danger` border and text, fills red on hover. Only for irreversible actions (cancel a flight).
- **Text link (`textLink`):** semibold ink with a 2px accent underline at 40 %; hover turns text and underline fully accent.
- **Card (`card`):** `surface`, 14px radius, `shadow-card`. Clickable cards add `hover:-translate-y-1 hover:shadow-card-hover`.
- **Highlighted card (`card-hi`):** a card with a 1.5px accent ring. For the one block per page that needs attention: booking status on C-09, chosen outbound flight on C-02, the total on C-07.
- **Input / select (`Field`, `SelectField`):** label above (14px semibold `ink-2`), 42px-tall 10px-radius box on `surface` with `line-strong` border; focus turns the border accent with a 4px `accent-soft` halo; errors turn it `danger` with the message below in `danger`. Never placeholder-as-label.
- **Status badge (`StatusBadge`):** 6px radius, 12px semibold, tone text on tone soft. `large` variant (18px bold, 10px radius) heads detail screens. Keep the status words in the DOM text (E2E reads them).
- **Notice:** 10px radius, `danger-soft` (error) or `ok-soft` (done) background, icon in the tone colour.
- **Navigation (`SiteHeader`):** items are pills; the active one is `accent-soft` with accent text, inactive ones are muted and get a `tint` fill on hover.
- **Filter chips (C-08):** pills on `surface` with a `line` border; selected = accent border, `accent-soft` fill, accent text.
- **Table (`tableBox`, `th`, `td`):** wrapped in a card; header row on `tint`, 14px semibold `ink-2`; body cells 14px with a hairline between rows; rows tint on hover.
- **Flight card:** airline code chip (accent-soft), times 18px bold, a route line (hollow dot, dashed line, accent dot), price 18px bold accent, primary "Chọn".

## Do's and Don'ts

### Do

- Use `accent` for every primary action and every money total, so the eye learns "terracotta = act / pay".
- Put content on `surface` cards over the `paper` canvas; let elevation, not borders, separate blocks.
- Show every status through `StatusBadge`, never as plain text.
- Keep one `card-hi` per page at most.
- Test every screen in both colour schemes.
- End page titles with a period.

### Don't

- Don't add a second brand accent (blue CTAs, green links…). Status colours are not accents.
- Don't use `danger` red for anything that is not an error, a cancellation or a destructive action.
- Don't use `clay-ember` (`#bc7155`) behind white text: it is 3.7:1. Use `accent`.
- Don't put a `border` and a shadow on the same card; the ring is already inside `shadow-card`.
- Don't use square corners or mix radii outside the shape rule above.
- Don't set body copy below 14px; badges (12px) are the only exception. Don't scale buttons, inputs or cards back up: the compact sizes are deliberate (revision 2026-10-23).

## Surfaces

| Level | Name       | Light     | Dark      | Purpose                         |
| ----- | ---------- | --------- | --------- | ------------------------------- |
| 0     | Paper      | `#f3f5f8` | `#0a111c` | Page background                 |
| 1     | Surface    | `#ffffff` | `#111b29` | Cards, inputs, menus, dialogs   |
| 2     | Tint       | `#e9eef3` | `#172435` | Hover, header rows, skeletons   |
| —     | Footer Ink | `#000d10` | `#000d10` | Landing footer, overscroll area |

## Imagery

Photography and 3D renders dominate the visual language, specifically a single hero image of a white commercial airliner against a night sky. The aircraft carries a plain white livery with no airline logos or markings — SkyLine sells tickets across many airlines and must not imply partnership with any one carrier. The image is hyper-clean, product-showcase style — no lifestyle context, no passengers, no airports. The aircraft IS the hero. The account screens (C-03–C-06) use one photo of the flag tower in Hà Nội with a dark scrim. Iconography is minimal and always stroke-based in the current text colour.

## Layout (landing)

Full-bleed sections: night-sky hero, white content bands, one clay band (after-sales), dark FAQ and final call. The hero is asymmetric: wordmark flush-left at extreme scale, a right-side two-line headline, the aircraft in the lower-centre negative space, and the search dock below it with the terracotta "Tìm chuyến bay" button. The footer is a full-bleed dark band. The landing keeps the Claude Design CSS verbatim in `landing.css` (scoped under `.sk`) so a changed mock can be diffed; it mirrors the app tokens through its own `--accent`, `--paper`, `--panel`… variables.

## Agent Prompt Guide

**Quick Color Reference**

- text: `ink` (#0e1726 / #e8edf3)
- page: `paper` (#f3f5f8 / #0a111c), cards: `surface` (#ffffff / #111b29)
- accent, primary action, prices: `accent` (#a85a3c / #e08a68) with `accent-fg`
- muted text: `muted` (#5a6675 / #94a1b2)
- border: `line` / `line-strong`
- status: `ok`, `warn`, `danger`, `info` with `-soft` backgrounds

**Example Component Prompts**

1. Primary button: `className={pillPrimary}`. Terracotta pill, white text in light mode, dark text in dark mode, accent shadow, 1px lift on hover.
2. A content block: `<section className="card p-16">`. White card on the paper canvas, 14px radius, tinted ring and soft shadow.
3. A status: `<StatusBadge status={b.status} label={STATUS_LABEL[b.status]} />`.
4. A data table: wrap in `<div className={tableBox}>`, use `th` / `td` from `admin-ui.tsx` or `staff-ui.tsx`.

## Assets & Licensing

SkyLine is a non-commercial, personal academic project. Every asset must still carry a license that permits web use — "educational use" is not a legal exemption for fonts or images.

| Asset                    | Choice                                                   | Status                                                                                           |
| ------------------------ | -------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Typeface                 | Be Vietnam Pro (static, 400–800)                         | Free — SIL OFL 1.1                                                                               |
| Previous typeface        | Inter                                                    | Replaced 2026-10-23; still OFL if ever needed                                                    |
| Reference typeface       | Helvetica Now Display (Monotype)                         | Not used — commercial license required, even for non-commercial web use                          |
| Hero aircraft image      | Own render, or a photo/model under Unsplash, Pexels, CC0 | Must be free-licensed; keep the source URL in the repo. CC-BY models require visible attribution |
| Icons                    | Stroke-based icons in `components/landing/icons.tsx`     | Own                                                                                              |
| Wordmark, copy, taglines | SkyLine's own                                            | Do not reuse the reference site's logo, copy, or imagery                                         |
