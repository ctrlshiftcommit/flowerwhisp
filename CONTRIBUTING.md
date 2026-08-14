# Contributing

Thank you for helping make FlowerWhisp calm, private, and dependable.

## Before opening a change

1. Read PRODUCT.md, DESIGN.md, PRIVACY.md, and the architecture/protocol docs.
2. Check existing issues and preserve unrelated working-tree changes.
3. Keep credentials, audio, models, caches, generated packages, and personal
   absolute paths out of commits.
4. Add or update tests for changed Core, Application, Infrastructure, or
   protocol behavior.

## Local checks

Use the project-local SDK and D: cache settings in
[docs/developer-setup.md](docs/developer-setup.md). At minimum run:

```powershell
git diff --check
dotnet restore FlowerWhisp.slnx
dotnet build FlowerWhisp.slnx --configuration Debug --arch x64 --no-restore
dotnet test tests\FlowerWhisp.Tests\FlowerWhisp.Tests.csproj --no-restore
```

If a Windows App SDK or packaging check cannot run on your machine, say so in
the pull request rather than implying it passed.

## Pull requests

Use a focused title and describe user impact, privacy implications, tests,
runtime verification, and known limitations. A source edit, successful build,
installed package, and end-to-end user exercise are separate evidence gates.
Do not claim public availability or signed artifacts without release evidence.

Contributions are accepted under Apache-2.0 unless a separate written
agreement says otherwise. By submitting intentionally for inclusion, you agree
to the terms in LICENSE.
