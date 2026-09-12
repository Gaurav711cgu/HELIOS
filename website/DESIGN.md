# Design System: HELIOS Orchestrator
**Project:** HELIOS — Java-native durable workflow engine dashboard

---

## 1. Visual Theme & Atmosphere

**Mood:** "High-Frequency Data Terminal" — the aesthetic of a mission-critical aerospace control room or financial trading terminal. The UI must feel **earned and serious**, never decorative. Every pixel serves a purpose: surface information, signal state, confirm action.

**Density:** High. Breathing room is deliberately tight. Padding is functional (12–16px internal), not generous. This is a dashboard for engineers who need information density, not whitespace.

**Personality:** Industrial Utilitarian. No border-radius anywhere — every edge is sharp and squared-off. No floating cards — panels are **docked**, separated by hairline 1px borders, like circuit board segments. Nothing floats. Nothing decorates. Structure is information.

**Motion Philosophy:** Single-purpose only.
- Log entries fade-in from left (`opacity 0 + translateX(-8px)` → natural) at 150ms ease-out — confirms new data arrived.
- Chart areas sweep in on mount (300ms ease-out) — reveals data as it becomes available.
- The system status dot pulses indefinitely — signals live state without demanding attention.
- Fault cells in the DAG matrix pulse amber — draws attention to an abnormal state.
- No section slide-ups. No hover card lifts. No scatter-gun entrance animations.

---

## 2. Color Palette & Roles

| Name | Hex | Role |
|---|---|---|
| **Void Black** | `#09090B` | Page background — the deepest layer; cool near-black, not pure black |
| **Graphite Slate** | `#111117` | Panel/surface fill — one step above void, creates layer depth without shadows |
| **Hairline Border** | `#27272A` | All 1px structural dividers, panel borders, table rows — the grid of the interface |
| **Phosphor Amber** | `#FF4D00` | Primary accent — used exclusively for critical states: active nav, focus rings, breach indicators, primary CTAs. Inspired by old phosphor terminal screens. |
| **Steel Blue** | `#3E6DB4` | Secondary data accent — secondary chart lines, info-level log entries, non-critical data tracks |
| **Terminal Green** | `#22C55E` | Success / confirmed execution — step completion, operational status, recovered state |
| **Caution Amber** | `#EAB308` | Warning / fault injected — chaos events, SLA-approaching latency, panel fault status |
| **Stark White** | `#FAFAFA` | Primary text — headings, metric values, critical labels. Full-contrast on Void Black (21:1) |
| **Ash Gray** | `#71717A` | Secondary text — labels, metadata, descriptions, axis ticks. Meets WCAG AA at 4.6:1 on `#09090B` |

---

## 3. Typography Rules

Three typefaces. Each has a single, exclusive role — they never swap.

### Display: `Bricolage Grotesque` (weights 700, 800)
Used **only** for metric values and page headings. Its adjustable width and brutalist bone structure make large numbers visually commanding. A single `48px / font-weight: 800` metric value owns the panel it sits in. Letter-spacing: `-0.01em` (slightly tighter than default for tighter number groupings).

### Body: `Manrope` (weights 400, 500, 600)
Used for all prose — descriptions, explanations, challenge narration, comparison paragraphs. Clean geometric sans-serif, highly legible at 14–16px. Line height: `1.7` for prose, `1.4` for compact descriptions. Max line width: 65 characters (42rem).

### Data / Code: `Space Mono` (weights 400, 700)
Used for **everything data-adjacent**: nav links, labels, log entries, timestamps, table data, code snippets, button text, axis ticks, badges. Its fixed-width columns make numeric data scannable. Letter-spacing: `0.1–0.2em` uppercase for labels. This font is the "voice" of the machine.

### Type Scale (8px base grid)
| Token | Size | Usage |
|---|---|---|
| `text-[9px]`  | 9px | Version tags, status pills |
| `text-[10px]` | 10px | Section eyebrows, table headers, legend labels |
| `text-[11px]` | 11px | Nav links, log entries, tooltips |
| `text-sm`     | 14px | Body descriptions, card prose |
| `text-xl`     | 20px | Sub-headings |
| `text-3xl`    | 30px | Section headings (Bricolage) |
| `text-5xl`    | 48px | Metric values (Bricolage 800) |

---

## 4. Component Stylings

### Buttons (`TerminalButton`)
- **Shape:** Sharp, squared-off edges (`border-radius: 0`). A button is a command, not a suggestion.
- **Structure:** `1px border` + label in `Space Mono` uppercase tracking-widest. Icon left-aligned.
- **Primary:** Phosphor Amber border (`#FF4D00`), Amber text, hover fills `#FF4D00` at 10% opacity.
- **Destructive:** Caution Amber border (`#EAB308`), Amber text — used for fault injection only.
- **Ghost:** Hairline border (`#27272A`), Stark White text, hover brightens border.
- **Loading state:** `Loader2` spinner replaces icon. Button `disabled + aria-busy`. Never double-fires.
- **Disabled:** Border collapses to `#27272A`, text fades to Ash Gray — visually inert.

### Panels (`PanelShell`)
- **Shape:** Sharp corners everywhere. No `border-radius`.
- **Background:** Graphite Slate (`#111117`) — one step above page void.
- **Border:** 1px Hairline Border (`#27272A`) on all sides.
- **Status top border (2px):** 
  - `live` → Terminal Green (`#22C55E`)
  - `fault` → Caution Amber (`#EAB308`)
  - `idle` → Hairline (`#27272A`) — invisible, no status
- **Header bar:** `border-b #27272A`, `px-4 py-3`, eyebrow label in `Space Mono 10px`.
- **Depth:** Flat. Zero shadows. Depth is implied by stacking `#09090B` → `#111117`.

### Metric Blocks
- **Layout:** `flex-col justify-between`, `p-4`, no fill beyond the shared panel background.
- **Top:** Label in `Space Mono 10px uppercase tracking-[0.15em]` + Lucide icon (14px, Ash Gray).
- **Center:** Value in `Bricolage 800, 48px` — this is the most important element.
- **Bottom:** Trend glyph (↑↓−) in semantic color + sub-text in Ash Gray `Space Mono 12px`.

### Charts
- **Background:** Transparent — charts live inside `PanelShell`, which provides the surface.
- **Grid:** Horizontal only (`strokeDasharray="4 4"`), Hairline Border color. No vertical grid lines.
- **Axes:** `stroke="#27272A"` lines, `fill="#71717A"` ticks, `Space Mono 10px`.
- **Tooltips:** Void Black background, Hairline border, Space Mono font — matches terminal aesthetic.
- **Area fills:** Gradient fade-to-transparent. The color is the data — the fade is the decay.
- **Reference lines:** Dashed. Always labelled. `#FF4D00` for SLA breach lines. `#71717A` for targets.
- **Dot policy:** Dots only on breach points (latency > SLA) — not on every data point.

### DAG Matrix
- **Layout:** CSS Grid. First column = thread label (fixed 100px), remaining = one column per step.
- **Cell states:** `○ ◉ ● ✕` glyphs — legible without color alone (accessible).
- **Idle:** Dark fill `#1C1C22`, glyph in `#27272A`.
- **Executing:** Blue fill `#3E6DB4/15`, glyph in `#3E6DB4`, `animate-pulse`.
- **Done:** Green fill `#22C55E/10`, glyph in `#22C55E`.
- **Fault:** Amber fill `#EAB308/10`, glyph in `#EAB308`.

### Badges
- Shape: Sharp (`border-radius: 0`). 1px border.
- Background: Semantic color at 15% opacity. Border at 30% opacity. Text at full semantic color.
- Font: `Space Mono 10px` uppercase tracking-widest.

### Navigation
- Fixed top, `height: 56px (h-14)`. Void Black background. Hairline bottom border.
- Active link: Phosphor Amber text + Amber border at 40% opacity + Amber fill at 5%. Sharp edges.
- Inactive: Ash Gray text, transparent border, hover → Stark White text.
- Brand: `Bricolage 800 20px uppercase`.

### Tables
- No background alternation (no "zebra striping") — rows separated by Hairline borders only.
- Hover: `bg-[#27272A]/30` — subtle but present.
- Header: `Space Mono 10px uppercase tracking-widest Ash Gray`.
- Data: `Space Mono 12px`. Critical values in semantic color (Terminal Green for zero dupes).

### Log Feed
- Entries: `border-l-2` in semantic color + `pl-3 py-1.5`.
- Three columns: `[timestamp Ash Gray] [EVENT Semantic-colored bold uppercase] [detail Fafafa/80]`.
- Entrance: Framer Motion `opacity 0 + translateX(-8px)` → natural, 150ms ease-out.
- Container: `aria-live="polite"` — screen readers announce new entries.

---

## 5. Layout Principles

**Grid base:** 8px. All padding/margin/gap values are multiples of 4px or 8px.

**Page structure:** `max-w-7xl mx-auto px-6 py-10` for content pages. Dashboard uses `full-bleed` grid.

**Panel grid:** Panels are separated by `1px` gaps using the `gap-px bg-[#27272A]` trick — the gap itself becomes the border. This ensures perfectly flush panel edges with a single-pixel structural grid.

**Whitespace philosophy:** Whitespace is earned by information density. Sections are separated by 80px (`space-y-20`) — the only generous spacing on the page, creating clear section rhythm without decorative dividers.

**Mobile strategy:** `grid-cols-1` on mobile → `grid-cols-2` at md → `grid-cols-3/4` at lg. The dashboard collapses gracefully; charts stack vertically; the DAG matrix becomes horizontally scrollable (`overflow-x-auto`).

**Information hierarchy:**
1. Metric value (Bricolage 48px) — what is the number
2. Trend + current (Space Mono 12px) — how is it moving
3. Label (Space Mono 10px uppercase) — what does it measure
4. Panel title (Space Mono 10px uppercase muted) — where does it live

**Elevation model:** Zero drop shadows. All depth is implied by background color steps:
- `#09090B` → page void (floor)
- `#111117` → panel surface (table)
- `#27272A` → border / divider (edge)

Everything lives on these three layers. Nothing floats above them.
