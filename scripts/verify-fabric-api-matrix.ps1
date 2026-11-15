[CmdletBinding()]
param(
    [string[]]$OnlyTarget
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
# PowerShell 7 can otherwise turn a nonzero native exit code into a terminating
# error before the matrix has recorded the failed coordinate.
$PSNativeCommandUseErrorActionPreference = $false

$metadataUri = 'https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradleWrapper = if ($env:OS -eq 'Windows_NT') {
    Join-Path $repositoryRoot 'gradlew.bat'
} else {
    Join-Path $repositoryRoot 'gradlew'
}

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "Gradle wrapper was not found at $gradleWrapper"
}

$targets = @(
    [pscustomobject]@{ Name = '1.21.4'; ProjectPath = ':mc1214'; Suffix = '+1.21.4' },
    [pscustomobject]@{ Name = '1.21.11'; ProjectPath = ':'; Suffix = '+1.21.11' },
    [pscustomobject]@{ Name = '1.21.11_unobfuscated'; ProjectPath = ':mc12111unobfuscated'; Suffix = '+1.21.11_unobfuscated' },
    [pscustomobject]@{ Name = '26.1'; ProjectPath = ':mc261'; Suffix = '+26.1' },
    [pscustomobject]@{ Name = '26.1.1'; ProjectPath = ':mc2611'; Suffix = '+26.1.1' },
    [pscustomobject]@{ Name = '26.1.2'; ProjectPath = ':mc2612'; Suffix = '+26.1.2' },
    [pscustomobject]@{ Name = '26.2'; ProjectPath = ':mc262'; Suffix = '+26.2' },
    [pscustomobject]@{ Name = '26.3'; ProjectPath = ':mc263'; Suffix = '+26.3' },
    [pscustomobject]@{ Name = '26.4 (snapshot-8)'; ProjectPath = ':mc264beta'; Suffix = '+26.4' }
)

if ($OnlyTarget) {
    $unknownTargets = @($OnlyTarget | Where-Object { $_ -notin $targets.Name })
    if ($unknownTargets.Count -gt 0) {
        throw "Unknown target name(s): $($unknownTargets -join ', ')"
    }
    $targets = @($targets | Where-Object { $_.Name -in $OnlyTarget })
}

Write-Host 'Reading published Fabric API versions from the official Maven metadata...'
try {
    if ($env:OS -eq 'Windows_NT') {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    }
    $metadataResponse = Invoke-WebRequest -Uri $metadataUri -TimeoutSec 60 -UseBasicParsing
    [xml]$metadata = $metadataResponse.Content
    $publishedVersions = @(
        foreach ($node in $metadata.SelectNodes("/*[local-name()='metadata']/*[local-name()='versioning']/*[local-name()='versions']/*[local-name()='version']")) {
            $value = $node.InnerText.Trim()
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                $value
            }
        }
    )
} catch {
    Write-Error "Could not read Fabric API Maven metadata from $metadataUri"
    exit 1
}

if ($publishedVersions.Count -eq 0) {
    Write-Error 'Fabric API Maven metadata contained no published versions.'
    exit 1
}

$cases = [System.Collections.Generic.List[object]]::new()
foreach ($target in $targets) {
    $targetVersions = @(
        foreach ($version in $publishedVersions) {
            if ($version.EndsWith($target.Suffix, [System.StringComparison]::Ordinal)) {
                $version
            }
        }
    )

    Write-Host ('Target {0} ({1}): {2} published API version(s)' -f $target.Name, $target.ProjectPath, $targetVersions.Count)

    foreach ($version in $targetVersions) {
        $taskPath = if ($target.ProjectPath -eq ':') {
            ':compileClientJava'
        } else {
            '{0}:compileClientJava' -f $target.ProjectPath
        }
        $cases.Add([pscustomobject]@{
            TargetName = $target.Name
            ProjectPath = $target.ProjectPath
            TaskPath = $taskPath
            ApiVersion = $version
        })
    }
}

if ($cases.Count -eq 0) {
    Write-Error 'No published Fabric API versions matched the configured Minecraft targets.'
    exit 1
}

$failureList = [System.Collections.Generic.List[object]]::new()
$passedCount = 0
$totalCount = $cases.Count
$progressInterval = [Math]::Max(1, [int][Math]::Ceiling($totalCount / 20.0))
$caseIndex = 0

Write-Host ('Starting {0} compileClientJava checks. Build output is suppressed; failures are listed at the end.' -f $totalCount)
Push-Location $repositoryRoot
try {
    foreach ($case in $cases) {
        $caseIndex++
        if ($caseIndex -eq 1 -or $caseIndex -eq $totalCount -or ($caseIndex % $progressInterval) -eq 0) {
            $percent = [int][Math]::Floor(($caseIndex * 100.0) / $totalCount)
            Write-Host ('Progress {0}/{1} ({2}%): {3} / {4}' -f $caseIndex, $totalCount, $percent, $case.TargetName, $case.ApiVersion)
        }

        # Scope each override to the selected project. Gradle configures every
        # target during a build, and a global override would apply an API
        # artifact from one Minecraft mapping namespace to every other target.
        # Keep the coordinate as one argument so the plus sign remains intact.
        $projectName = if ($case.ProjectPath -eq ':') { 'root' } else { $case.ProjectPath.TrimStart(':') }
        $overrideArgument = '-Pfabric_api_override_' + $projectName + '=' + $case.ApiVersion
        $arguments = @(
            $case.TaskPath,
            '--console=plain',
            '--no-configuration-cache',
            $overrideArgument
        )

        $exitCode = 1
        try {
            & $gradleWrapper @arguments *> $null
            $exitCode = $LASTEXITCODE
        } catch {
            # Record the coordinate below and continue with the remaining matrix.
            $exitCode = 1
        }

        if ($exitCode -eq 0) {
            $passedCount++
        } else {
            $failureList.Add([pscustomobject]@{
                TargetName = $case.TargetName
                ProjectPath = $case.ProjectPath
                ApiVersion = $case.ApiVersion
            })
        }
    }
} finally {
    Pop-Location
}

$failedCount = $failureList.Count
Write-Host ''
Write-Host ('Fabric API compile matrix complete: {0} passed, {1} failed, {2} total.' -f $passedCount, $failedCount, $totalCount)

if ($failedCount -gt 0) {
    Write-Host 'Failing target and API coordinates:'
    foreach ($failure in $failureList) {
        Write-Host ('  {0} ({1})  net.fabricmc.fabric-api:fabric-api:{2}' -f $failure.TargetName, $failure.ProjectPath, $failure.ApiVersion)
    }
    exit 1
}

exit 0
