# Privacy data-flow table

| Data | Origin | Destination | Stored? | User control / note |
| --- | --- | --- | --- | --- |
| PCM audio | Microphone capture | Groq STT or local sidecar, based on selected backend | Whole audio is not persisted by the current record contract | Choose backend; local path is user-owned. |
| Raw transcript | STT provider | Optional polish, insertion, history | Per retention: forever / 24h / never | NeverStore skips whole-record history. |
| Final text | Raw transcript + optional text-only polish | Insertion and history | Same retention policy | Polish never receives audio. |
| API credential | User settings | Windows current-user secret seam and provider Authorization header | Secret store only | Never export, log, or prompt. |
| Daily usage aggregate | Dictation orchestration | Local aggregate repository | Yes, count/seconds/characters only | May remain with NeverStore. |
| Runtime/model/cache | User configuration | Local filesystem and sidecar | User-owned, outside export | Never commit or upload. |
| Device/window identifiers | Windows foreground target | Insertion decision | Not part of record/export | Do not write to logs or export. |

This is a source-level contract. A production release must verify each edge,
including deletion behavior and provider request payloads, before claiming
complete privacy controls.
