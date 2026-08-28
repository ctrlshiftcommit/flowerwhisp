# FlowerWhisp Android design contract

## Design read

An operational Android utility for people writing across apps, with an OLED-black, compact, recovery-first interface built from Material 3 behavior rather than default Material styling.

## Dials

- Variance: 5/10. The shell is predictable; the voice interaction is distinctive.
- Motion: 5/10. State transitions feel continuous, but never delay input.
- Density: 6/10. Daily-use controls and history are compact while onboarding remains spacious.

## Visual world

- Canvas: near-OLED black `#050505`, not gray and not a decorative gradient.
- Raised surface: `#101210`; selected surface: `#172019`; outline: `#283029`.
- Primary text: `#F4F7F4`; secondary text: `#B8C2BA`.
- Accent: botanical mint `#B8F5D0`, used for active listening, focus, and primary actions.
- Error: `#FFB4AB`; warning: `#F2C97D`; success: `#9EE5B2`.
- Cards use 16dp radii only where a surface is a real unit. Compact controls use full pills. Lists primarily use spacing and separators.
- Typography uses the Android system sans for reliable multilingual coverage. Weight, measure, and tabular figures establish hierarchy.
- Motion uses short Material spatial transitions. Waveform motion is derived from microphone amplitude. Reduced motion keeps all states legible without scale or repeated animation.

## Memorable detail

The floating bubble reads like a living flower bud: a compact dark seed in READY, a mint audio bloom in RECORDING, a quiet folding motion in PROCESSING, and a brief resolved mark in SUCCESS. It never becomes a generic floating action button or a large obstructive panel.
