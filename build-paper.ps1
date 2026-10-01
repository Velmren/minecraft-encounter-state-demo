$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$deps = Join-Path $root '.deps'
New-Item $deps -ItemType Directory -Force | Out-Null
foreach ($dependency in (Get-Content (Join-Path $root 'dependencies.json') -Raw | ConvertFrom-Json)) {
    $path = Join-Path $deps $dependency.file
    if (!(Test-Path -LiteralPath $path)) { Invoke-WebRequest $dependency.url -OutFile $path }
    if ((Get-FileHash -LiteralPath $path).Hash -ne $dependency.sha256) {
        throw "Dependency hash mismatch: $($dependency.file)"
    }
}
$output = [IO.Path]::GetFullPath((Join-Path $root 'build/plugin-classes'))
if (!$output.StartsWith([IO.Path]::GetFullPath($root) + [IO.Path]::DirectorySeparatorChar)) {
    throw 'Build output escaped the project root'
}
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output -Recurse -Force }
New-Item $output -ItemType Directory -Force | Out-Null
$sources = Get-ChildItem (Join-Path $root 'src/main/java') -Filter '*.java' -Recurse | ForEach-Object FullName
$classpath = (Get-ChildItem $deps -Filter '*.jar' | ForEach-Object FullName) -join [IO.Path]::PathSeparator
& javac --release 21 -Xlint:all -Werror -proc:none -cp $classpath -d $output @sources
if ($LASTEXITCODE -ne 0) { throw 'Paper adapter compilation failed' }
Copy-Item (Join-Path $root 'src/main/resources/*') $output
$jar = Join-Path $root 'build/encounter-state-demo-0.2.0.jar'
& jar --create --file $jar -C $output .
if ($LASTEXITCODE -ne 0) { throw 'JAR packaging failed' }
Write-Output $jar
