# FlowerWhisp renderer design contract

This is the committed renderer direction for FlowerWhisp. It translates the
supplied Whisper Flow brief and the current product contract into values and
rules that can be implemented consistently.

This document describes intended UI behavior and visual tokens. It does not
claim that screenshots, a packaged runtime, a global shortcut hook, a
transcription provider, or text insertion have been verified.

## Committed world

FlowerWhisp is a quiet Windows desktop utility, not a website or a voice
dashboard. The main window is a calm management surface; the tiny floating
pill is the primary dictation interaction.

- The default canvas is warm ivory with near-black text, restrained gray
  hierarchy, subtle warm borders, and sparse purple or muted-teal accents.
- Content has an editorial rhythm: a strong left anchor, uneven but deliberate
  section spacing, and a narrow supporting column where useful. Do not tile
  every feature into equal cards.
- The pill remains compact, dark, rounded, and stable. It expands only for the
  controls required by the current mode.
- Copy is concrete and operational. Do not add promotional claims, fake
  analytics, placeholder transcript content, emoji, neon, glassmorphism, or
  generic component-library dashboard treatment.
- The user's active application and focus are sacred. The overlay must not
  resize or steal focus from the target application.

## Token architecture

Use three layers: primitive values, semantic purpose tokens, and component
tokens. Components consume semantic or component tokens; they do not introduce
one-off colors, spacing, radii, or shadows.

### Primitive tokens

These are the small set of raw values from which the renderer is built.

```css
:root {
  /* Warm neutral palette */
  --fw-ivory-50: #fcfaf6;
  --fw-ivory-100: #f4f0e9;
  --fw-ivory-200: #ece7de;
  --fw-warm-gray-300: #d9d2c8;
  --fw-graphite-500: #77716a;
  --fw-graphite-700: #625d57;
  --fw-graphite-900: #1d1b18;

  /* Restrained semantic accents */
  --fw-purple-600: #675a93;
  --fw-purple-700: #52477a;
  --fw-teal-700: #3e6c65;
  --fw-red-700: #984d44;

  /* Four-pixel spacing scale */
  --fw-space-1: 4px;
  --fw-space-2: 8px;
  --fw-space-3: 12px;
  --fw-space-4: 16px;
  --fw-space-5: 20px;
  --fw-space-6: 24px;
  --fw-space-8: 32px;
  --fw-space-10: 40px;
  --fw-space-12: 48px;
  --fw-space-16: 64px;

  /* Shape and elevation */
  --fw-radius-control: 8px;
  --fw-radius-panel: 14px;
  --fw-radius-pill: 999px;
  --fw-shadow-overlay: 0 8px 24px rgb(29 27 24 / 18%);
  --fw-shadow-menu: 0 4px 16px rgb(29 27 24 / 12%);

  /* Motion */
  --fw-duration-fast: 120ms;
  --fw-duration-standard: 160ms;
  --fw-duration-emphasis: 220ms;
  --fw-ease-standard: cubic-bezier(0.2, 0.8, 0.2, 1);
}
```

### Semantic tokens

The ivory theme is the committed default. A future user-selected theme may
override this semantic layer, but must retain the same contrast and hierarchy.

| Token | Resolves to | Use |
| --- | --- | --- |
| `--fw-color-canvas` | `--fw-ivory-100` | Main window background |
| `--fw-color-surface` | `--fw-ivory-50` | Raised panels and input fields |
| `--fw-color-surface-muted` | `--fw-ivory-200` | Selected navigation and quiet grouping |
| `--fw-color-text-primary` | `--fw-graphite-900` | Headings, values, primary labels |
| `--fw-color-text-secondary` | `--fw-graphite-700` | Descriptions, supporting labels |
| `--fw-color-text-tertiary` | `--fw-graphite-500` | Metadata only; never essential meaning |
| `--fw-color-border` | `--fw-warm-gray-300` | Dividers and quiet outlines |
| `--fw-color-focus` | `--fw-purple-700` | Visible keyboard focus |
| `--fw-color-accent` | `--fw-purple-600` | Selected state and intentional emphasis |
| `--fw-color-accent-secondary` | `--fw-teal-700` | Optional local/healthy status cue |
| `--fw-color-danger` | `--fw-red-700` | Errors and destructive actions |
| `--fw-color-overlay` | `--fw-graphite-900` | Floating pill background |
| `--fw-color-overlay-text` | `--fw-ivory-50` | Floating pill content |

Accents are never the only state signal. Pair them with text, position, icon
shape, or an accessible status announcement.

### Component tokens

| Component | Contract tokens |
| --- | --- |
| Sidebar | `--fw-color-canvas`, `--fw-color-border`, 216–232px rail width |
| Nav item | 36px visual row, `--fw-radius-control`, muted selected surface |
| Standard control | 40px minimum height, 12px horizontal padding, 8px radius |
| Icon control | 36px hit box minimum; 40px for primary actions |
| Panel | `--fw-color-surface`, 1px border, 20–24px internal padding, 14px radius |
| Settings row | Canvas surface, 16px vertical padding, hairline divider |
| Status badge | 20–24px height, full radius, short text plus icon when needed |
| Floating pill | 52–56px visual height, full radius, overlay shadow, 8px outer padding |
| Pill action | 28px visual control inside a 36px hit area; always labelled |

## Surfaces and elevation

- The canvas is flat and warm. Do not use a page-wide gradient, background
  image, blur, or translucent glass layer.
- Use the raised surface only to separate an interaction from the canvas:
  setup/configuration panels, style cards, transform cards, dialogs, and
  transcript detail. A page may use a bordered section or a list instead of a
  card.
- Settings are primarily rows and groups, not a grid of cards. Use dividers and
  spacing to establish hierarchy.
- The overlay is the sole consistently dark surface. Its shadow is functional
  separation from the active application, not decoration.
- Menus and dialogs may use the menu shadow. Do not layer multiple shadows or
  make every control look elevated.

## Typography

Typography carries the hierarchy; containers do not need to become large.

| Role | Family | Size / line height | Weight | Use |
| --- | --- | --- | --- | --- |
| Page title | `ui-serif, Georgia, "Times New Roman", serif` | 32px / 36px | 400–500 | Page identity such as Insights or Style |
| Section title | Same serif family | 20px / 26px | 400–500 | A meaningful section within a page |
| Body | `"Segoe UI Variable", "Segoe UI", system-ui, sans-serif` | 14px / 20px | 400 | Transcript and explanatory text |
| Control label | Same sans family | 13px / 18px | 500 | Buttons, settings labels, navigation |
| Metadata | Same sans family | 11px / 15px | 400–500 | Timestamp, provider, application |
| Compact value | Same sans family | 24px / 28px | 500–600 | Real measured stat, never decoration |
| Pill status | Same sans family | 11px / 14px | 500 | Short operational status |

Use the serif face for page and section identity, not for dense controls. Use
sentence case by default. Reserve uppercase and letter spacing for short,
non-essential metadata. Keep body measure near 60–70 characters where the
layout allows it, and let user text wrap rather than clipping it.

No remote font is required by this contract. If a bundled typeface is added,
it must preserve the same serif/sans role split, include the needed weights,
and be checked in the packaged renderer for glyph coverage.

## Spacing and layout rhythm

The base unit is 4px, but the composition is intentionally asymmetrical.

- The main shell is a left rail plus `minmax(0, 1fr)` content. Keep the rail
  compact; do not turn it into a hero panel.
- The content frame is left anchored and capped around 1120px on wide windows.
  Preserve breathing room on ultrawide displays instead of stretching lists
  across the entire screen.
- Page headers use roughly 40px top space and 24px below the title. Section
  gaps are usually 24px or 32px; related control gaps are 8px, 12px, or 16px.
- Where a supporting column is useful, use an uneven split such as
  `minmax(0, 1.45fr) minmax(260px, 0.8fr)`. The secondary column must not
  compete with the primary transcript or settings task.
- History and settings use full-width rows. Style and transform cards may use
  a two-column arrangement, but their content should remain spacious and
  readable rather than becoming a dashboard matrix.
- Numeric insights are a compact strip followed by the activity detail. Do not
  use tall decorative stat cards or pretend data to fill empty space.
- Apply `min-width: 0` to shrinking grid and flex children. The owning list or
  content region scrolls; the entire frameless window must not become an
  accidental drag surface.

## Icon rules

- Use the installed Phosphor icon set in its regular line style where possible.
  Keep visual icons at 16–18px, with a consistent stroke weight.
- Do not use emoji, Unicode pictograms, text characters, or platform-dependent
  glyphs as interface icons. A microphone, settings, copy, favorite, menu,
  cancel, and confirm action each needs a real icon component.
- A navigation icon is subordinate to its text label. Do not enlarge icons to
  compensate for missing hierarchy.
- Every standalone icon button needs an accessible name, visible focus state,
  and a hit area large enough for desktop keyboard and pointer use. Tooltips
  supplement labels; they do not replace them for essential actions.
- A selected or dangerous icon action must also have a text or semantic state
  cue. Color alone is insufficient.

## Accessibility baseline

- Target at least 4.5:1 contrast for normal text, 3:1 for large text and
  meaningful graphics, and 3:1 for control and focus indicators.
- `:focus-visible` is always visible against the actual surface beneath the
  control. Do not remove the outline without replacing it with an equivalent
  semantic focus ring.
- Labels, status text, and icons must remain understandable at 125–200% display
  scaling. Do not encode a required distinction only through color or motion.
- Respect keyboard navigation, screen-reader names, non-ASCII text, and
  `prefers-reduced-motion` in every state, including the overlay.

## Motion and feedback

Motion explains a state change and then gets out of the way.

| Interaction | Motion contract |
| --- | --- |
| Control hover/focus | Color, border, or shadow transition in 120–160ms |
| Page content reveal | Small 4px vertical settle or opacity change in 160ms; no dramatic entrance |
| Pill show/hide | Quick opacity plus slight scale from 0.98 to 1 in 160ms |
| Recording bars | Driven by the actual microphone level, smoothed to avoid jitter; never random |
| Processing | Compact determinate stage or quiet indeterminate cue; no giant spinner |
| Success | Brief confirmation, then hide the pill without bounce or celebration |

Do not use bouncing, pulsing neon, large scale changes, or animation that
changes the user's target position. Under `prefers-reduced-motion: reduce`,
remove transforms and non-essential transitions; state, text, and focus must
remain fully understandable without motion.

## Responsive desktop behavior

The design is desktop-first but must survive the configured minimum window,
short heights, high-DPI scaling, zoom, long text, and non-ASCII content.

| Window condition | Behavior |
| --- | --- |
| 1280×720 baseline | Full rail, capped content frame, two-column sections where useful |
| 1366×768 | Same hierarchy with additional breathing room, not larger type |
| 1920×1080 and wider | Keep the content cap; retain intentional empty margin |
| Narrow desktop window | Reduce rail width or collapse labels with accessible tooltips; stack secondary columns; keep the primary task first |
| Short window | Keep the shell usable and give the content region vertical scrolling; do not hide actions below a fixed viewport |
| 125–200% display scaling or zoom | Allow wrapping, `clamp()` where appropriate, and no coordinate-based layout |
| Long or localized text | Wrap or deliberately truncate with a discoverable full value; never let it cover controls |

The floating pill is an independent always-on-top overlay window. Its position
is based on the desktop work area, not the main window's layout. It keeps the
52–56px visual height at every supported desktop size and only grows
horizontally for toggle controls or an actionable error.

## Content and state guardrails

- Use operational labels such as “Transcribing”, “Copy raw text”, “Retry”,
  “Open settings”, and “Could not insert”. Do not use persuasive marketing
  language in the renderer.
- Keep `Raw`, `Cleaned`, and `Inserted` distinct whenever transcript detail is
  shown. Never present a cleaned result as if it were the original speech.
- Render insights only from persisted measurements. If there is no data, use an
  honest empty state instead of demo numbers, artificial streaks, or decorative
  charts.
- Every asynchronous action has a visible loading, success, error, or cancelled
  outcome. The error state includes the next useful action and never exposes a
  credential, raw request, or private filesystem detail.

## Implementation guardrail

The design is complete when a later renderer can implement the tiny dictation
loop, the quiet management shell, and every recovery path using these semantic
tokens and component rules without adding a new visual language.
