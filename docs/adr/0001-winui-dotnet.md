# ADR 0001: WinUI 3 and C#/.NET 10

- Status: accepted
- Date: 2026-08-13

## Context

FlowerWhisp is a Windows desktop product that needs native global shortcuts,
foreground-window checks, text insertion, accessibility integration, and a
small local-first footprint.

## Decision

Use WinUI 3 with C# and .NET 10, targeting Windows 10 build 19041 and Windows 11
x64. Keep product rules in platform-neutral Core/Application projects and
isolate Windows APIs in Platform.Windows.

## Consequences

Native behavior and accessibility are available without a web runtime. The
tradeoff is Windows-only packaging/tooling and the need to test Windows App SDK
version changes deliberately.
