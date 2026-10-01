$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$output = Join-Path $root 'build\classes'
if (Test-Path -LiteralPath $output) {
    Remove-Item -LiteralPath $output -Recurse -Force
}
New-Item -ItemType Directory -Path $output -Force | Out-Null

$sources = @(
    Get-ChildItem -Path (Join-Path $root 'src\main\java\dev\velmren\encounter\core') -Filter '*.java' -Recurse
    Get-ChildItem -Path (Join-Path $root 'src\test\java') -Filter '*.java' -Recurse
) | ForEach-Object FullName

& javac --release 17 -Xlint:all -Werror -proc:none -d $output @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed with exit code $LASTEXITCODE" }

& java -ea -cp $output dev.velmren.encounter.core.EncounterEngineTest
if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }
