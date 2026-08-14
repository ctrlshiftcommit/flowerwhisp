# Signal Ledger design contract

FlowerWhisp uses a cool paper-white / dark graphite visual language. Graphite
controls sit on calm surfaces, with one cobalt accent for focus and progress;
the black pill is the primary recording affordance. Segoe UI Variable is the
default typeface. Surfaces use 12–16 px radii and generous, legible spacing.

The design must work in light, dark, and Windows high-contrast modes. Primary
text remains high contrast, secondary text is a restrained gray, and cobalt is
never the sole indicator of state. Respect reduced-motion settings; transitions
should be short and optional, never required to understand a state change.

## Interaction principles

1. Show the active target, backend, retention choice, and insertion result.
2. Keep a visible escape/cancel path for recording and processing.
3. Make “local”, “Groq”, “polished”, and “stored” distinct labels.
4. Prefer native WinUI controls and keyboard affordances over custom chrome.
5. Explain a failure in the next useful action, without leaking credentials or
   audio content into diagnostics.

## Accessibility checklist

- Keyboard navigation reaches every action and has a visible focus indicator.
- Names and roles are exposed for recording, cancellation, navigation, and
  retention controls.
- Text and controls meet WCAG AA contrast targets in light and dark themes.
- High-contrast mode does not depend on custom colors or background images.
- Reduced motion disables non-essential animation.
- Error text is adjacent to the affected control and is screen-reader friendly.
