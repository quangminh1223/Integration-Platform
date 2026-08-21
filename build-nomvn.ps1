# ==============================================================================
# Bien dich integration-platform KHONG can Maven.
#
# Goi javac truc tiep, classpath lay tu ~/.m2 (moi artifact chon ban version cao nhat),
# Lombok chay qua -processorpath. Compile tung module theo thu tu phu thuoc, ghi class
# vao build\<module>, module sau ke thua classpath cua module truoc.
#
# Muc dich: kiem chung code co bien dich duoc hay khong khi may chua co Maven.
# Co Maven thi dung `mvn clean compile` van la chuan.
#
#   powershell -ExecutionPolicy Bypass -File build-nomvn.ps1
# ==============================================================================
# Continue, khong Stop: javac ghi loi bien dich ra stderr va PowerShell coi do la
# NativeCommandError. Neu de Stop thi script chet giua duong va khong ghi duoc log.
$ErrorActionPreference = 'Continue'

$root     = $PSScriptRoot
$buildDir = Join-Path $root 'build'
$logFile  = Join-Path $root 'build-nomvn.log'
$log      = @()

function Say($t) { $script:log += $t; Write-Host $t }

# ---- JDK ---------------------------------------------------------------------
$jdk = $env:JAVA_HOME
if (-not $jdk -or -not (Test-Path (Join-Path $jdk 'bin\javac.exe'))) {
    $c = Get-ChildItem 'C:\Program Files\Java\jdk-*' -Directory -ErrorAction SilentlyContinue |
         Sort-Object Name -Descending | Select-Object -First 1
    if (-not $c) { throw 'Khong tim thay JDK' }
    $jdk = $c.FullName
}
$javac = Join-Path $jdk 'bin\javac.exe'
Say ('JDK: ' + $jdk)

# ---- Classpath tu .m2: moi artifact lay 1 jar version cao nhat ---------------
$m2 = Join-Path $env:USERPROFILE '.m2\repository'
Say 'Dang quet .m2 ...'
$allJars = Get-ChildItem $m2 -Recurse -Filter *.jar -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' }

# Thu muc cha cua thu muc version = artifact. Nhom theo do, giu version moi nhat.
$byArtifact = @{}
foreach ($j in $allJars) {
    $versionDir  = $j.Directory
    $artifactDir = $versionDir.Parent.FullName
    $cur = $byArtifact[$artifactDir]
    if (-not $cur -or ([string]$versionDir.Name -gt [string]$cur.Version)) {
        $byArtifact[$artifactDir] = [pscustomobject]@{ Version = $versionDir.Name; Path = $j.FullName }
    }
}
$libJars = $byArtifact.Values | ForEach-Object { $_.Path }
Say ('Classpath: ' + $libJars.Count + ' artifact tu .m2')

$lombok = $libJars | Where-Object { $_ -match '\\lombok-[\d.]+\.jar$' } | Select-Object -First 1
if (-not $lombok) { throw 'Khong tim thay lombok trong .m2' }
Say ('Lombok: ' + (Split-Path $lombok -Leaf))

# ---- Thu tu module theo phu thuoc -------------------------------------------
$modules = @(
    'integration-common',
    'integration-core',
    'integration-transformer',
    'integration-router',
    'integration-adapter',
    'integration-security',
    'integration-config',
    'integration-monitoring',
    'integration-scheduler',
    'integration-gateway',
    'integration-app'
)

if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

$moduleOuts = @()
$failed     = @()
$noBom      = New-Object System.Text.UTF8Encoding($false)

foreach ($m in $modules) {
    $srcDir = Join-Path $root ($m + '\src\main\java')
    if (-not (Test-Path $srcDir)) { Say ('SKIP ' + $m + ' (khong co src/main/java)'); continue }

    $sources = Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    if ($sources.Count -eq 0) { Say ('SKIP ' + $m + ' (khong co file .java)'); continue }

    $outDir = Join-Path $buildDir $m
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null

    # Classpath = jar .m2 + output cua cac module da compile truoc do
    $cp = (($libJars + $moduleOuts) -join ';')

    $argFile = Join-Path $buildDir ($m + '-sources.txt')
    [System.IO.File]::WriteAllLines($argFile, [string[]]$sources, $noBom)

    $cpFile = Join-Path $buildDir ($m + '-cp.txt')
    [System.IO.File]::WriteAllLines($cpFile, [string[]]@('-cp', $cp), $noBom)

    Say ''
    Say ('=== ' + $m + '  (' + $sources.Count + ' file) ===')

    # Ghi output javac ra file roi doc lai, thay vi bat qua pipeline PowerShell.
    # PowerShell bien stderr cua native command thanh NativeCommandError va lam script
    # chet giua duong, khong ghi duoc log tong hop.
    $javacOut = Join-Path $buildDir ($m + '-javac.txt')
    $p = Start-Process -FilePath $javac -PassThru -Wait -NoNewWindow `
         -RedirectStandardError $javacOut `
         -ArgumentList @('--release', '21', '-encoding', 'UTF-8', '-nowarn',
                         '-processorpath', $lombok,
                         ('@' + $cpFile), '-d', $outDir, ('@' + $argFile))
    $code = $p.ExitCode

    if (Test-Path $javacOut) {
        foreach ($l in (Get-Content $javacOut)) { if ($l.Trim()) { Say ('   ' + $l) } }
        Remove-Item $javacOut -Force
    }

    if ($code -eq 0) {
        Say ('   -> OK')
        $moduleOuts += $outDir
    } else {
        Say ('   -> FAILED (exit ' + $code + ')')
        $failed += $m
        # Van tiep tuc de thay het cac module loi, khong dung o module dau tien
    }

    Remove-Item $argFile -Force
    Remove-Item $cpFile -Force
}

Say ''
Say '==============================================================='
if ($failed.Count -eq 0) {
    Say ' TAT CA MODULE BIEN DICH OK'
} else {
    Say (' MODULE LOI: ' + ($failed -join ', '))
}
Say '==============================================================='

[System.IO.File]::WriteAllLines($logFile, [string[]]$log, $noBom)
