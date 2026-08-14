# Developer setup

## Prerequisites

- Windows 10 build 19041 or later (Windows 11 recommended for development).
- x64 machine and the .NET 10 SDK. WinUI 3 packaging may require the matching
  Windows App SDK tooling and Visual Studio components.
- PowerShell 5.1-compatible commands.

## Keep caches off a crowded system drive

The repository may contain a project-local SDK at `.tools/dotnet`. The following
keeps .NET, NuGet, temporary files, and test output under the checkout (place
the checkout on D: when C: is constrained):

```powershell
$cacheRoot = Join-Path $pwd '.tools'
$env:DOTNET_CLI_HOME = Join-Path $cacheRoot 'dotnet-home'
$env:NUGET_PACKAGES = Join-Path $cacheRoot 'nuget-packages'
$env:NUGET_HTTP_CACHE_PATH = Join-Path $cacheRoot 'nuget-http'
$env:TEMP = Join-Path $cacheRoot 'temp'; $env:TMP = $env:TEMP
$dotnet = Join-Path $pwd '.tools\dotnet\dotnet.exe'
if (-not (Test-Path $dotnet)) { $dotnet = 'dotnet' }
& $dotnet restore FlowerWhisp.slnx
& $dotnet build FlowerWhisp.slnx --configuration Debug --arch x64 --no-restore
& $dotnet test tests\FlowerWhisp.Tests\FlowerWhisp.Tests.csproj --no-restore
```

Do not put Groq keys in PowerShell history or checked-in files. Configure them
through the application secret seam. Configure an official OpenAI Whisper
checkout and model outside this repository, then set
`FLOWERWHISP_WHISPER_CHECKOUT`, `FLOWERWHISP_MODEL_DIR`, and (optionally)
`FLOWERWHISP_DEVICE=cuda` for the local host. The host loads one model per
process; without a checkout it stays in parser/handshake diagnostic mode.

The current configured D: drive `small`/CUDA handshake was attempted but timed
out after five minutes during model initialization. Treat model startup as
unverified until a future run completes the handshake and a real transcription.

## Evidence labels

When reporting work, distinguish source inspection, build/test output, package
installation, and an end-to-end runtime exercise. A successful build alone is
not a visual or microphone verification.
