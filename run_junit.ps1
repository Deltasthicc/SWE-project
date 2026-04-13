param(
    [Parameter(Position = 0)]
    [string]$TestClass,

    [Parameter(Position = 1)]
    [string]$TestMethod,

    [switch]$All
)

$ErrorActionPreference = "Stop"

$JUnitJar = "lib\junit-platform-console-standalone-1.10.2.jar"
$BasePackage = "bas.test"

if (!(Test-Path $JUnitJar)) {
    Write-Host "ERROR: Could not find $JUnitJar" -ForegroundColor Red
    exit 1
}

if (!(Test-Path "out")) {
    Write-Host "ERROR: 'out' folder not found." -ForegroundColor Red
    Write-Host "Compile first by running: .\run_tests" -ForegroundColor Yellow
    exit 1
}

function Normalize-ClassName {
    param([string]$Name)

    if ([string]::IsNullOrWhiteSpace($Name)) {
        return $null
    }

    $Name = $Name.Trim()

    if ($Name.StartsWith("$BasePackage.")) {
        return $Name
    }

    return "$BasePackage.$Name"
}

function Get-FullClassPath {
    $entries = @()

    if (Test-Path "out") {
        $entries += (Resolve-Path "out").Path
    }

    if (Test-Path "lib") {
        $jarFiles = Get-ChildItem "lib" -Filter *.jar | ForEach-Object { $_.FullName }
        $entries += $jarFiles
    }

    return ($entries -join ";")
}

$FullClassPath = Get-FullClassPath

if ([string]::IsNullOrWhiteSpace($FullClassPath)) {
    Write-Host "ERROR: Could not build classpath." -ForegroundColor Red
    exit 1
}

if ($All) {
    Write-Host "Running ALL tests..." -ForegroundColor Cyan
    & java -jar $JUnitJar execute `
        --class-path $FullClassPath `
        --scan-package $BasePackage `
        --details tree
    exit $LASTEXITCODE
}

if ([string]::IsNullOrWhiteSpace($TestClass)) {
    Write-Host "Usage:" -ForegroundColor Yellow
    Write-Host "  .\run_junit.ps1 TestDatabase"
    Write-Host "  .\run_junit.ps1 TestDatabase authValidOwner"
    Write-Host "  .\run_junit.ps1 -All"
    exit 1
}

$FullClass = Normalize-ClassName $TestClass

if (-not [string]::IsNullOrWhiteSpace($TestMethod)) {
    $Selector = "$FullClass#$TestMethod"
    Write-Host "Running test method: $Selector" -ForegroundColor Cyan

    & java -jar $JUnitJar execute `
        --class-path $FullClassPath `
        --select-method $Selector `
        --details tree

    exit $LASTEXITCODE
}

Write-Host "Running test class: $FullClass" -ForegroundColor Cyan

& java -jar $JUnitJar execute `
    --class-path $FullClassPath `
    --select-class $FullClass `
    --details tree

exit $LASTEXITCODE