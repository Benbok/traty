# Traty release helper: checks the repo, runs tests, creates an annotated tag and pushes it.
# Pushing the tag starts .github/workflows/release.yml, which builds and publishes the APK.
#
# Usage (Windows PowerShell 5.1+ or PowerShell 7):
#   scripts\release.ps1 -Version 0.2 -NotesFile notes.md          # real release
#   scripts\release.ps1 -Version 0.2 -NotesFile notes.md -DryRun  # only checks, changes nothing
#   scripts\release.ps1 -Suggest                                  # show last tag and commits since it
#
# The annotated tag message (from -NotesFile) becomes the GitHub release description.
# This file is ASCII-only on purpose: Windows PowerShell 5.1 reads BOM-less UTF-8 as ANSI.

param(
    [string]$Version,
    [string]$NotesFile,
    [switch]$DryRun,
    [switch]$SkipTests,
    [switch]$Suggest
)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)

function Fail([string]$msg) { Write-Host "RELEASE BLOCKED: $msg" -ForegroundColor Red; exit 1 }
function Step([string]$msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

# Run git without PowerShell turning stderr output into errors.
# Note: PowerShell is case-insensitive, so this function must call git.exe (not 'git'), or it would call itself.
function Git {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $out = & git.exe @args 2>&1 | ForEach-Object { "$_" }
    $code = $LASTEXITCODE
    $ErrorActionPreference = $old
    [pscustomobject]@{ Out = ($out -join "`n").Trim(); Code = $code }
}

Step 'Fetching origin (with tags)'
$r = Git fetch origin --tags --prune --force
if ($r.Code -ne 0) { Fail "git fetch failed: $($r.Out)" }

# No tags yet (first release): git describe fails, so there is no last tag.
$described = Git describe --tags --abbrev=0 --match 'v[0-9]*'
$lastTag = if ($described.Code -eq 0) { $described.Out } else { '' }

if ($Suggest) {
    Write-Host "LAST_TAG=$lastTag"
    $range = if ($lastTag) { "$lastTag..HEAD" } else { 'HEAD' }
    Write-Host "COMMITS_SINCE_LAST_TAG:"
    (Git log $range --no-merges --max-count=80 --pretty=format:'%h %s').Out
    exit 0
}

if (-not $Version) { Fail 'Pass -Version, for example -Version 0.2' }
$Version = $Version.TrimStart('v')
if ($Version -notmatch '^[0-9]+(\.[0-9]+){1,2}$') { Fail "Version '$Version' must look like 0.2 or 0.2.1" }
$tag = "v$Version"

Step 'Checking repository state'
$branch = (Git rev-parse --abbrev-ref HEAD).Out
if ($branch -ne 'main') { Fail "You are on '$branch', releases are made from 'main'" }

$dirty = (Git status --porcelain).Out
if ($dirty) { Fail "Working tree is not clean:`n$dirty" }

$head = (Git rev-parse HEAD).Out
$remote = (Git rev-parse origin/main).Out
if ($head -ne $remote) {
    $counts = (Git rev-list --left-right --count 'HEAD...origin/main').Out
    Fail "main differs from origin/main (ahead/behind: $counts). Push or pull first, so the tag points at a commit that exists on GitHub."
}

if ((Git rev-parse -q --verify "refs/tags/$tag").Code -eq 0) { Fail "Tag $tag already exists" }

if (-not (Test-Path '.github/workflows/release.yml')) { Fail 'No .github/workflows/release.yml in this commit' }

Step 'Checking that the Room schema for the current DB version is committed'
$dbText = Get-Content 'app/src/main/java/ru/my/traty/data/DbVersion.kt' -Raw
if ($dbText -match '\bconst\s+val\s+DB_SCHEMA_VERSION\s*=\s*(\d+)\b') {
    $dbVersion = $Matches[1]
    $schema = "app/schemas/ru.my.traty.data.AppDatabase/$dbVersion.json"
    if ((Git ls-files --error-unmatch $schema).Code -ne 0) { Fail "Schema $schema is not committed (DB version $dbVersion)" }
} else {
    Fail 'Could not read DB_SCHEMA_VERSION from DbVersion.kt'
}

$notes = ''
if ($NotesFile) {
    if (-not (Test-Path $NotesFile)) { Fail "Notes file '$NotesFile' not found" }
    $notes = (Get-Content $NotesFile -Raw -Encoding UTF8).Trim()
}
if (-not $notes) { Write-Host 'WARNING: no release notes given, GitHub will not have a custom description' -ForegroundColor Yellow }

if ($SkipTests) {
    Write-Host 'WARNING: tests skipped (-SkipTests)' -ForegroundColor Yellow
} else {
    Step 'Running unit tests (gradlew :app:testDebugUnitTest)'
    & .\gradlew.bat :app:testDebugUnitTest --console=plain -q
    if ($LASTEXITCODE -ne 0) { Fail 'Unit tests failed, nothing was tagged or pushed' }
}

if ($DryRun) {
    Write-Host "DRY RUN OK: $tag could be released from $($head.Substring(0, 7)). Nothing was changed." -ForegroundColor Green
    exit 0
}

Step "Creating annotated tag $tag"
$msgFile = Join-Path ([IO.Path]::GetTempPath()) "traty-release-$Version.txt"
$body = if ($notes) { $notes } else { "Traty $Version" }
# UTF-8 without BOM, so git stores the Russian text correctly
[IO.File]::WriteAllText($msgFile, $body, (New-Object System.Text.UTF8Encoding($false)))
$r = Git tag -a $tag -F $msgFile
Remove-Item $msgFile -ErrorAction SilentlyContinue
if ($r.Code -ne 0) { Fail "git tag failed: $($r.Out)" }

Step "Pushing $tag"
$r = Git push origin $tag
if ($r.Code -ne 0) {
    Git tag -d $tag | Out-Null
    Fail "git push failed, local tag removed: $($r.Out)"
}

Write-Host "DONE: pushed $tag. Build: https://github.com/Benbok/traty/actions  Release: https://github.com/Benbok/traty/releases/tag/$tag" -ForegroundColor Green
