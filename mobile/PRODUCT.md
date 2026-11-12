# FlowerWhisp Android product contract

FlowerWhisp is a system-level Android dictation utility. Its primary job is to turn speech into polished text inside the field the user already selected, without replacing the keyboard.

The first acceptance milestone is concrete: focus a supported field in another app, tap the FlowerWhisp bubble, speak, finish, and receive polished text at the cursor. Direct insertion failure must become a visible clipboard fallback. Audio and raw text must not disappear after provider failure.

The desktop application is read-only reference material. The Android app owns its implementation, permissions, local database, services, and provider adapters inside this directory.

## Snippets

- A snippet trigger is a spoken phrase. Matching is case-insensitive, requires a complete phrase rather than a substring inside another word, and works inside a longer dictation or as the whole dictation with trailing sentence punctuation.
- Existing symbolic triggers remain usable through spoken aliases such as `slash sig` for `/sig` and `semicolon addr` for `;addr`.
- Trigger phrases are limited to 60 characters and saved text to 4,000 characters. Duplicate phrases and duplicate saved blocks are rejected.
- Snippet bodies are local data. The dictation pipeline replaces matched triggers with protected markers before deterministic or Groq cleanup and restores the exact saved text only on-device afterward. Groq requests must not include snippet bodies.
- If cleanup removes, duplicates, reorders, or changes a protected marker, discard that cleanup response, retain the locally expanded text, record cleanup failure, and keep recovery data according to the existing failure policy.
