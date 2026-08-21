# ═══════════════════════════════════════════════════════════════════════════════
# Build msb-json-transform thành JAR mà KHÔNG cần Maven.
#
# Dùng khi máy chưa cài Maven: script gọi trực tiếp javac/jar của JDK và lấy Jackson
# từ ~/.m2. Nếu có Maven thì cứ `mvn clean package` cho tiện hơn, kết quả tương đương.
#
#   powershell -ExecutionPolicy Bypass -File build-jar.ps1
# ═══════════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = 'Stop'

$moduleDir  = $PSScriptRoot
$version    = '1.0.0'
$artifact   = 'msb-json-transform'
$targetDir  = Join-Path $moduleDir 'target'
$classesDir = Join-Path $targetDir 'classes'
$srcDir     = Join-Path $moduleDir 'src\main\java'

# ── Tìm JDK ────────────────────────────────────────────────────────────────────
$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome 'bin\javac.exe'))) {
    $candidate = Get-ChildItem 'C:\Program Files\Java\jdk-*' -Directory -ErrorAction SilentlyContinue |
                 Sort-Object Name -Descending | Select-Object -First 1
    if (-not $candidate) { throw 'Khong tim thay JDK. Dat JAVA_HOME roi chay lai.' }
    $jdkHome = $candidate.FullName
}
$javac = Join-Path $jdkHome 'bin\javac.exe'
$jar   = Join-Path $jdkHome 'bin\jar.exe'
Write-Host "JDK      : $jdkHome"

# ── Classpath: Jackson tu ~/.m2 ────────────────────────────────────────────────
$m2 = Join-Path $env:USERPROFILE '.m2\repository\com\fasterxml\jackson'
$needed = @('core\jackson-databind', 'core\jackson-core', 'core\jackson-annotations')
$cpParts = @()
foreach ($n in $needed) {
    $dir = Join-Path $m2 $n
    if (-not (Test-Path $dir)) { throw "Khong tim thay $n trong $m2" }
    # Duyet version moi nhat truoc, nhung BO QUA version chi co .pom hoac download do
    # (thu muc ton tai ma khong co file .jar dung duoc).
    $found = $null
    foreach ($verDir in (Get-ChildItem $dir -Directory | Sort-Object Name -Descending)) {
        $candidateJar = Get-ChildItem (Join-Path $verDir.FullName '*.jar') -ErrorAction SilentlyContinue |
                        Where-Object { $_.Name -notmatch '-(sources|javadoc)\.jar$' } |
                        Select-Object -First 1
        if ($candidateJar) { $found = $candidateJar; break }
    }
    if (-not $found) { throw "Khong tim thay file jar nao trong $dir" }
    $cpParts += $found.FullName
    Write-Host "Jackson  : $($found.Name)"
}
$classpath = $cpParts -join ';'

# ── Compile ────────────────────────────────────────────────────────────────────
if (Test-Path $targetDir) { Remove-Item $targetDir -Recurse -Force }
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null

$sources = Get-ChildItem $srcDir -Recurse -Filter *.java | ForEach-Object { $_.FullName }
Write-Host "Sources  : $($sources.Count) file"

# Argfile PHAI khong co BOM — javac coi BOM la phan cua ten file dau tien va bao
# "Invalid filename". Set-Content -Encoding utf8 tren PowerShell 5.1 lai them BOM,
# nen ghi bang .NET voi UTF8Encoding($false).
$argFile = Join-Path $targetDir 'sources.txt'
$noBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($argFile, [string[]]$sources, $noBom)

& $javac --release 17 -encoding UTF-8 -Xlint:all -d $classesDir -cp $classpath "@$argFile"
if ($LASTEXITCODE -ne 0) { throw "javac that bai (exit $LASTEXITCODE)" }
Write-Host 'Compile  : OK'

# ── Dong goi JAR (binary + sources) ────────────────────────────────────────────
$binJar = Join-Path $targetDir "$artifact-$version.jar"
$srcJar = Join-Path $targetDir "$artifact-$version-sources.jar"

& $jar --create --file $binJar -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw "jar that bai (exit $LASTEXITCODE)" }

& $jar --create --file $srcJar -C $srcDir .
if ($LASTEXITCODE -ne 0) { throw "jar sources that bai (exit $LASTEXITCODE)" }

Remove-Item $argFile -Force

Write-Host ''
Write-Host '==========================================================='
Write-Host ' BUILD OK'
Write-Host ('  ' + $binJar + '  (' + [math]::Round((Get-Item $binJar).Length / 1KB, 1) + ' KB)')
Write-Host ('  ' + $srcJar + '  (' + [math]::Round((Get-Item $srcJar).Length / 1KB, 1) + ' KB)')
Write-Host '==========================================================='
