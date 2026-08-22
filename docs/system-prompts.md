# FlowerWhisp system prompts

FlowerWhisp treats dictation text as user-owned source material. The prompt
builders in `src/main/prompts.ts` are pure functions: they only turn typed
configuration into stable system-prompt text, perform no network calls, and
have no access to credentials or Electron APIs.

## Processing order

The intended text pipeline is:

```text
raw transcription
    -> deterministic dictionary replacements
    -> optional cleanup (none, light, or medium)
    -> optional transform
    -> insertion / persistence
```

The raw transcription remains available to the caller even after later stages
run. `buildDictionaryProtectionPrompt` documents the replacement table for a
provider, but the main-process caller owns applying those literal replacements
before making an optional LLM request. The LLM is told that the replacement
text is authoritative and must not be reversed or approximated.

## Shared guardrails

Cleanup and transform prompts both state the same non-negotiable rules:

- keep raw dictation meaning, claims, uncertainty, voice, and intent intact;
- do not invent facts, names, numbers, dates, locations, actions, or context;
- prevent intent drift when correcting wording or formatting;
- preserve proper nouns, product names, URLs, code identifiers, and domain
  terminology;
- honor deterministic dictionary replacements exactly; and
- return only resulting text, never meta-commentary, explanations, labels, or
  status text.

The transcript is supplied as a user message after the system prompt. It is
explicitly treated as source text rather than as instructions, which prevents
dictated content from changing the processing contract.

## Cleanup levels

`CleanupLevel` is the closed union `none | light | medium`, also exported as
`CLEANUP_LEVELS` and `cleanupLevels`.

- `none` returns the deterministic input without language changes.
- `light` allows only unambiguous punctuation, capitalization, spacing, and
  obvious transcription-artifact corrections.
- `medium` includes light cleanup plus locally clear grammar and sentence-flow
  corrections. It may remove an obvious filler or false start only when that
  cannot change meaning. It is not a summary or a rewrite.

## Styles and transforms

A style is presentation guidance (`PromptStyle.name` and
`PromptStyle.instructions`). A transform is a separately named operation
(`PromptTransform.name` and `PromptTransform.description`). A cleanup prompt
mentions a configured transform as a later stage and does not apply it. A
transform prompt applies only the requested operation and does not silently
broaden it into summarizing, interpretation, or cleanup.

The `PromptContext.dictionaryEntries` field is canonical. The `dictionary`
field is retained as a small compatibility alias for callers that already use
that setting name; when both are present, `dictionaryEntries` wins. A dictionary
entry may use `phrase` or the current IPC-facing `spoken` name for its source
text. Both forms produce the same literal protection rule.

The main-process provider seam may also pass `language`, `styleId`, and
`styleRules` directly. A structured style or transform can be supplied when a
caller has the full profile rather than only its selected ID and rules.

## Provider and error behavior

The prompt text is provider-agnostic. If a cleanup or transform provider is
unavailable, times out, fails, or returns empty/unusable text, the caller keeps
the deterministic input and surfaces an application-level message from
`PROMPT_ERROR_MESSAGES`. It must not ask the model to invent fallback text or
pass through raw provider diagnostics.

The prompt context contains no API keys, headers, provider secrets, or machine
paths. Provider credentials remain a main-process concern and are outside this
prompt slice.

## Example

```ts
import { buildCleanupSystemPrompt } from './src/main/prompts'
import type { PromptContext } from './src/shared/promptTypes'

const context: PromptContext = {
  cleanupLevel: 'light',
  style: {
    name: 'Professional note',
    instructions: 'Use direct sentences and normal paragraph breaks.',
  },
  dictionaryEntries: [
    { phrase: 'flower wisp', replacement: 'FlowerWhisp', protected: true },
  ],
}

const systemPrompt = buildCleanupSystemPrompt(context)
```

The raw transcript should be sent separately as the provider's user message.
Do not put credentials or unrelated machine state into `PromptContext`.
