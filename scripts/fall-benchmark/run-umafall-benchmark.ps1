param(
    [Parameter(Mandatory = $true)]
    [string]$DatasetDirectory,

    [string]$ReportPath = "build/fall-benchmark/umafall-report.md",

    [switch]$SimulateCallContext
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$engineClasses = Join-Path $repoRoot "app\build\intermediates\built_in_kotlinc\debug\compileDebugKotlin\classes"
$runnerSource = Join-Path $PSScriptRoot "FallDatasetBenchmarkCli.java"
$runnerClasses = Join-Path $repoRoot "build\fall-benchmark\runner-classes"
$resolvedDataset = (Resolve-Path -LiteralPath $DatasetDirectory).Path
$resolvedReport = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $ReportPath))

if (-not (Test-Path -LiteralPath (Join-Path $engineClasses "com\yinxing\launcher\feature\fall\FallDetectionEngine.class"))) {
    throw "Compiled FallDetectionEngine.class is missing. Run the Android unit tests first."
}

$kotlinStdlib = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\org.jetbrains.kotlin\kotlin-stdlib" `
    -Filter "kotlin-stdlib-*.jar" -Recurse |
    Where-Object { $_.Name -notlike "*-sources.jar" } |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName

if (-not $kotlinStdlib) {
    throw "Kotlin standard library was not found in the Gradle cache."
}

New-Item -ItemType Directory -Force -Path $runnerClasses | Out-Null
javac -encoding UTF-8 -cp "$engineClasses;$kotlinStdlib" -d $runnerClasses $runnerSource
if ($LASTEXITCODE -ne 0) { throw "Failed to compile the UMAFall benchmark runner." }

$runnerArguments = @(
    "com.yinxing.launcher.feature.fall.FallDatasetBenchmarkCli",
    $resolvedDataset,
    $resolvedReport
)
if ($SimulateCallContext) {
    $runnerArguments += "--simulate-call-context"
}

java -cp "$runnerClasses;$engineClasses;$kotlinStdlib" $runnerArguments
if ($LASTEXITCODE -ne 0) { throw "UMAFall benchmark failed." }
