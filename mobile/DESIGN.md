# FlowerWhisp Android design contract

## Design read

A quiet instrument for turning thought into text. The shell is editorial and tactile, while the dictation control behaves like a small physical instrument rather than a stock floating action button.

## Dials

- Variance: 6/10. The shell is calm; the dictation instrument is distinctive.
- Motion: 6/10. State changes have physical continuity, but never delay input.
- Density: 5/10. The repeated action is prominent; support details stay compact.

## Visual world

- Canvas: ink `#0C0B0A`, not gray and not a decorative gradient.
- Raised surface: `#161412`; elevated surface: `#201D19`; selected surface: `#29231E`; outline: `#3A342D`.
- Primary text: warm paper `#F5F0E7`; secondary text: `#BDB4A8`; muted text: `#8C847A`.
- Accent: restrained clay `#D17A5A`; strong clay `#B85D43`; on-accent ink `#1C110D`.
- Error: `#FFB3A7`; warning and resolved confirmation: `#E4BC83`.
- No cool blue or botanical accent tokens are permitted in the product UI.
- Cards use 24dp radii only where a panel is a real unit, 16dp for grouped rows, and 12dp for controls. Compact status and segmented controls may use full pills.
- Typography uses the Android system sans for reliable multilingual coverage. Weight, measure, and tabular figures establish hierarchy.
- Motion uses short spatial transitions. Waveform movement is derived from microphone amplitude. Reduced motion keeps every state legible without scale or repeated animation.

## Memorable detail

The floating bubble is a tiny graphite instrument: a quiet seed in READY, a clay listening mark with a live waveform in RECORDING, a restrained stepped state in PROCESSING, and a brief resolved mark in SUCCESS. It never becomes a generic floating action button or a large obstructive panel.

The app mark is the existing desktop waveform-flower asset at `assets/flowerwhisp.png`, reused unchanged on Android so the two products share one identity. It is used as a quiet brand signature, not as a new mascot or a literal microphone illustration.
