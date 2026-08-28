# FlowerWhisp Android product contract

FlowerWhisp is a system-level Android dictation utility. Its primary job is to turn speech into polished text inside the field the user already selected, without replacing the keyboard.

The first acceptance milestone is concrete: focus a supported field in another app, tap the FlowerWhisp bubble, speak, finish, and receive polished text at the cursor. Direct insertion failure must become a visible clipboard fallback. Audio and raw text must not disappear after provider failure.

The desktop application is read-only reference material. The Android app owns its implementation, permissions, local database, services, and provider adapters inside this directory.
