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
Assert-Value ($manifest.generatedAt) "Manifest is missing generatedAt."
Assert-Value ($manifest.endpoints.loginHost) "Manifest is missing endpoints.loginHost."
Assert-Value ($manifest.endpoints.loginPort -gt 0) "Manifest is missing endpoints.loginPort."
Assert-Value ($manifest.endpoints.js5Host) "Manifest is missing endpoints.js5Host."
Assert-Value ($manifest.endpoints.js5Ports.Count -gt 0) "Manifest is missing endpoints.js5Ports."
Assert-Value ($manifest.cache.buildId) "Manifest is missing cache.buildId."
Assert-Value ($manifest.rsa.publicExponent) "Manifest is missing rsa.publicExponent."
Assert-Value ($manifest.rsa.modulus) "Manifest is missing rsa.modulus. Is RsaService loaded?"
Assert-Value ($manifest.client.minimumVersion) "Manifest is missing client.minimumVersion."
Assert-Value ($manifest.client.bootstrapVersion) "Manifest is missing client.bootstrapVersion."
Assert-Value ($manifest.client.release.channel) "Manifest is missing client.release.channel."
Assert-Value ($manifest.client.release.latestVersion) "Manifest is missing client.release.latestVersion."
Assert-Value ($null -ne $manifest.client.release.artifacts) "Manifest is missing client.release.artifacts."
Assert-Value ($null -ne $manifest.client.update.enabled) "Manifest is missing client.update.enabled."
Assert-Value ($null -ne $manifest.client.update.required) "Manifest is missing client.update.required."
Assert-Value ($manifest.client.update.rollout -ge 0 -and $manifest.client.update.rollout -le 1) "Manifest client.update.rollout must be between 0 and 1."
Assert-Value ($null -ne $manifest.client.update.eligibleLauncherVersions) "Manifest is missing client.update.eligibleLauncherVersions."
Assert-Value ($manifest.plugins.allowlistVersion) "Manifest is missing plugins.allowlistVersion."

if ($manifest.client.update.enabled) {
    Assert-Value ($manifest.client.release.artifacts.Count -gt 0) "Updates are enabled but client.release.artifacts is empty."
    $usableArtifacts = @(
        $manifest.client.release.artifacts | Where-Object {
            $_.url -and $_.sha256 -and $_.sha256 -match '^[a-fA-F0-9]{64}$' -and $_.sizeBytes -gt 0
        }
    )
    Assert-Value ($usableArtifacts.Count -gt 0) "Updates are enabled but no artifact has url, sha256, and sizeBytes."
}

Write-Host "Alter client manifest is valid."
Write-Host "Game: $($manifest.game)"
Write-Host "Revision: $($manifest.revision)"
Write-Host "Login: $($manifest.endpoints.loginHost):$($manifest.endpoints.loginPort)"
Write-Host "JS5: $($manifest.endpoints.js5Host) ports $($manifest.endpoints.js5Ports -join ', ')"
Write-Host "Cache build: $($manifest.cache.buildId)"
Write-Host "Client: min $($manifest.client.minimumVersion), latest $($manifest.client.release.latestVersion), channel $($manifest.client.release.channel)"
Write-Host "Updates: enabled=$($manifest.client.update.enabled), required=$($manifest.client.update.required), rollout=$($manifest.client.update.rollout)"
Write-Host "Curated plugins: $($manifest.plugins.allowlist.Count)"
