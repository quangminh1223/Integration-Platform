# Chay SmokeTest.java tren JAR da build. Chay build-jar.ps1 truoc.
# LUU Y: file nay giu ASCII thuan. PowerShell 5.1 doc .ps1 theo ANSI, ky tu UTF-8
# nhieu byte (vi du em dash) se bi hieu sai va lam vo chuoi -> loi parse.
$ErrorActionPreference = 'Stop'

$here      = $PSScriptRoot
$moduleDir = Split-Path $here -Parent
$targetDir = Join-Path $moduleDir 'target'
$libJar    = Join-Path $targetDir 'msb-json-transform-1.0.0.jar'
if (-not (Test-Path $libJar)) { throw "Chua co $libJar. Chay build-jar.ps1 truoc." }

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) {
    $jdkHome = (Get-ChildItem 'C:\Program Files\Java\jdk-*' -Directory |
                Sort-Object Name -Descending | Select-Object -First 1).FullName
}
$javac = Join-Path $jdkHome 'bin\javac.exe'
$java  = Join-Path $jdkHome 'bin\java.exe'

# Jackson tu ~/.m2, bo qua thu muc version khong co jar
$m2 = Join-Path $env:USERPROFILE '.m2\repository\com\fasterxml\jackson'
$cpParts = @($libJar)
foreach ($n in @('core\jackson-databind', 'core\jackson-core', 'core\jackson-annotations')) {
    $dir = Join-Path $m2 $n
    foreach ($verDir in (Get-ChildItem $dir -Directory | Sort-Object Name -Descending)) {
        $j = Get-ChildItem (Join-Path $verDir.FullName '*.jar') -ErrorAction SilentlyContinue |
             Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } | Select-Object -First 1
        if ($j) { $cpParts += $j.FullName; break }
    }
}
$classpath = $cpParts -join ';'

$outDir = Join-Path $here 'out'
if (Test-Path $outDir) { Remove-Item $outDir -Recurse -Force }
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

& $javac -encoding UTF-8 -d $outDir -cp $classpath (Join-Path $here 'SmokeTest.java')
if ($LASTEXITCODE -ne 0) { throw "javac smoke test that bai ($LASTEXITCODE)" }

$runCp = $outDir + ';' + $classpath
& $java '-Dfile.encoding=UTF-8' '-cp' $runCp 'SmokeTest'
$code = $LASTEXITCODE
Write-Host ('smoke test exit=' + $code)
exit $code
