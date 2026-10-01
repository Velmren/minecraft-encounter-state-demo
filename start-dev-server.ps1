param([switch]$AcceptEula)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$server = Join-Path $root '.dev-server'
New-Item $server -ItemType Directory -Force | Out-Null
$headers = @{'User-Agent'='Velmren-EncounterDev/0.2 (https://github.com/Velmren/minecraft-encounter-state-demo)'}
$paper = Join-Path $server 'paper.jar'
if (!(Test-Path -LiteralPath $paper)) {
    Invoke-WebRequest 'https://fill-data.papermc.io/v1/objects/5ffef465eeeb5f2a3c23a24419d97c51afd7dbb4923ff42df9a3f58bba1ccfba/paper-1.21.11-132.jar' -Headers $headers -OutFile $paper
}
if ((Get-FileHash $paper).Hash -ne '5FFEF465EEEB5F2A3C23A24419D97C51AFD7DBB4923FF42DF9A3F58BBA1CCFBA') { throw 'Paper checksum mismatch' }
# Preload the upstream Mojang server so Paperclip also works where Java's outbound downloads fail.
$cache = Join-Path $server 'cache'
New-Item $cache -ItemType Directory -Force | Out-Null
$mojang = Join-Path $cache 'mojang_1.21.11.jar'
if (!(Test-Path -LiteralPath $mojang)) {
    Invoke-WebRequest 'https://piston-data.mojang.com/v1/objects/64bb6d763bed0a9f1d632ec347938594144943ed/server.jar' -OutFile $mojang
}
if ((Get-FileHash $mojang).Hash -ne 'F83B8E093865806F931C7E34AAE41B177D4C076335263DD124C75D6D65DD1726') { throw 'Mojang server checksum mismatch' }
$eula = Join-Path $server 'eula.txt'
if ($AcceptEula) { Set-Content $eula 'eula=true' }
if (!(Test-Path $eula) -or (Get-Content $eula) -notcontains 'eula=true') {
    throw 'Read https://www.minecraft.net/eula and pass -AcceptEula before starting the development server.'
}
$properties = Join-Path $server 'server.properties'
if (!(Test-Path $properties)) {
@'
server-ip=127.0.0.1
server-port=25576
online-mode=false
enforce-secure-profile=false
level-name=dev_lobby
level-type=minecraft:flat
spawn-protection=0
view-distance=8
simulation-distance=6
max-players=4
pause-when-empty-seconds=-1
motd=Encounter State - loopback development only
'@ | Set-Content $properties
}
if ((Get-Content $properties) -notcontains 'server-ip=127.0.0.1') {
    throw 'This script only starts an isolated loopback development server.'
}
& (Join-Path $root 'build-and-test.ps1')
& (Join-Path $root 'build-paper.ps1')
New-Item (Join-Path $server 'plugins') -ItemType Directory -Force | Out-Null
Copy-Item (Join-Path $root 'build/encounter-state-demo-0.2.0.jar') (Join-Path $server 'plugins/encounter-state-demo-0.2.0.jar')
Push-Location $server
try { & java '-Dterminal.jline=false' '-Dterminal.ansi=false' -Xms512M -Xmx1536M -jar paper.jar --nogui }
finally { Pop-Location }
