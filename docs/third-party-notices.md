# Third-party notices policy

The repository's direct NuGet dependencies are declared in the project files,
including Microsoft Windows App SDK/SDK build tools, CommunityToolkit.Mvvm,
Microsoft.NET.Test.Sdk, xUnit, and coverlet. Their licenses and notices remain
the responsibility of each upstream project and must be captured from the
resolved lock/restore metadata before a packaged release.

The Groq API, official OpenAI Whisper checkout, Python runtime, CUDA, and FFmpeg
are optional user-selected components rather than bundled repository assets.
They may have separate terms, model licenses, or redistribution restrictions.
Do not ship them in a FlowerWhisp package without a reviewed notice and license
decision.

Before each release, generate an SBOM from the exact resolved dependency graph,
review license compatibility, and update NOTICE plus this policy if a bundled
component changes. CI can produce an SBOM artifact; an artifact is not itself a
license review.
