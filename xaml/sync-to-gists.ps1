# Sync XAML files to GitHub Gists
# Requires: gh CLI authenticated with gist scope
#
# First run: Creates new gists and outputs their IDs
# Subsequent runs: Updates existing gists using stored IDs
#
# Usage: .\sync-to-gists.ps1

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$GistIdsFile = Join-Path $ScriptDir ".gist-ids"

# Load existing gist IDs
$GistIds = @{}
if (Test-Path $GistIdsFile) {
    Get-Content $GistIdsFile | ForEach-Object {
        if ($_ -match '^([^=]+)=(.+)$') {
            $GistIds[$Matches[1]] = $Matches[2]
        }
    }
}

# Gist descriptions
$Descriptions = @{
    "DndThemeDemo.xaml"   = "TTRPG Theme Demo - Dark Fantasy D&D Theme for NoesisGUI/XamlToy"
    "CharacterSheet.xaml" = "TTRPG Character Sheet - D&D 5e Style for NoesisGUI/XamlToy"
    "CombatControl.xaml"  = "TTRPG Combat Control Panel for NoesisGUI/XamlToy"
    "GMControl.xaml"      = "TTRPG GM Control Panel for NoesisGUI/XamlToy"
    "CombatHud.xaml"      = "TTRPG Combat HUD Overlay for NoesisGUI/XamlToy"
    "DndTheme.xaml"       = "TTRPG Shared Theme ResourceDictionary for NoesisGUI/XamlToy"
}

Write-Host "=== XAML to Gist Sync ===" -ForegroundColor Cyan
Write-Host ""

Push-Location $ScriptDir
try {
    $XamlFiles = Get-ChildItem -Filter "*.xaml" -File

    foreach ($file in $XamlFiles) {
        $fileName = $file.Name
        Write-Host "Processing: $fileName" -ForegroundColor Yellow

        $gistId = $GistIds[$fileName]
        $desc = if ($Descriptions.ContainsKey($fileName)) { $Descriptions[$fileName] } else { "TTRPG UI for XamlToy" }

        # Create temp file named Main.xaml (required by XamlToy)
        $mainXaml = Join-Path $ScriptDir "Main.xaml"
        Copy-Item $file.FullName $mainXaml

        if ($gistId) {
            # Update existing gist
            Write-Host "  Updating gist: $gistId" -ForegroundColor Gray
            try {
                gh gist edit $gistId $mainXaml 2>$null
            }
            catch {
                Write-Host "  Warning: Could not update gist. Creating new one." -ForegroundColor Yellow
                $gistId = $null
            }
        }

        if (-not $gistId) {
            # Create new gist
            Write-Host "  Creating new gist..." -ForegroundColor Gray
            $gistUrl = gh gist create $mainXaml --public --desc "$desc"
            $gistId = ($gistUrl -split '/')[-1]
            $GistIds[$fileName] = $gistId
            Write-Host "  Created: $gistId" -ForegroundColor Green
        }

        Remove-Item $mainXaml -ErrorAction SilentlyContinue

        Write-Host "  XamlToy: https://www.noesisengine.com/xamltoy/$gistId" -ForegroundColor Cyan
        Write-Host ""
    }

    # Save gist IDs for future runs
    $content = @("# Auto-generated gist ID mapping")
    $content += $GistIds.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }
    $content | Set-Content $GistIdsFile

    Write-Host "=== Sync Complete ===" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Gist IDs saved to: $GistIdsFile" -ForegroundColor Gray
    Write-Host ""
    Write-Host "XamlToy Preview URLs:" -ForegroundColor Yellow
    foreach ($key in $GistIds.Keys) {
        $name = [System.IO.Path]::GetFileNameWithoutExtension($key)
        Write-Host "  ${name}: https://www.noesisengine.com/xamltoy/$($GistIds[$key])" -ForegroundColor Cyan
    }
}
finally {
    Pop-Location
}
