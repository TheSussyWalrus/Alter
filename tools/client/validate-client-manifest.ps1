param(
    [string]$ManifestUrl = "http://127.0.0.1:4567/client_manifest.json",
    [int]$ExpectedRevision = 228
)

$ErrorActionPreference = "Stop"

function Assert-Value($Condition, $Message) {
    if (-not $Condition) {
        throw $Message
    }
}

$manifest = Invoke-RestMethod -Uri $ManifestUrl -Method Get

Assert-Value ($manifest.game) "Manifest is missing game."
Assert-Value ($manifest.revision -eq $ExpectedRevision) "Expected revision $ExpectedRevision but got $($manifest.revision)."
Assert-Value ($manifest.endpoints.loginHost) "Manifest is missing endpoints.loginHost."
Assert-Value ($manifest.endpoints.loginPort -gt 0) "Manifest is missing endpoints.loginPort."
Assert-Value ($manifest.endpoints.js5Host) "Manifest is missing endpoints.js5Host."
Assert-Value ($manifest.endpoints.js5Ports.Count -gt 0) "Manifest is missing endpoints.js5Ports."
Assert-Value ($manifest.cache.buildId) "Manifest is missing cache.buildId."
Assert-Value ($manifest.rsa.publicExponent) "Manifest is missing rsa.publicExponent."
Assert-Value ($manifest.rsa.modulus) "Manifest is missing rsa.modulus. Is RsaService loaded?"
Assert-Value ($manifest.client.minimumVersion) "Manifest is missing client.minimumVersion."
Assert-Value ($manifest.plugins.allowlistVersion) "Manifest is missing plugins.allowlistVersion."

Write-Host "Alter client manifest is valid."
Write-Host "Game: $($manifest.game)"
Write-Host "Revision: $($manifest.revision)"
Write-Host "Login: $($manifest.endpoints.loginHost):$($manifest.endpoints.loginPort)"
Write-Host "JS5: $($manifest.endpoints.js5Host) ports $($manifest.endpoints.js5Ports -join ', ')"
Write-Host "Cache build: $($manifest.cache.buildId)"
Write-Host "Curated plugins: $($manifest.plugins.allowlist.Count)"
