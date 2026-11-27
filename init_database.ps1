# ===============================================================================
# PROJECT NIHON - KHOI TAO CO SO DU LIEU MYSQL (POWERSHELL SCRIPT)
# ===============================================================================

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host "                PROJECT NIHON - KHOI TAO CO SO DU LIEU TOAN DIEN                " -ForegroundColor Cyan
Write-Host "===============================================================================" -ForegroundColor Cyan
Write-Host ""

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SqlFile = Join-Path $ScriptDir "nihon_database.sql"

if (-not (Test-Path $SqlFile)) {
    Write-Host "[LOI] Khong tim thay file $SqlFile!" -ForegroundColor Red
    exit 1
}

# 1. Tim kiem MySQL CLI
$mysqlPath = (Get-Command mysql -ErrorAction SilentlyContinue)?.Source

if (-not $mysqlPath) {
    $commonPaths = @(
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe",
        "C:\xampp\mysql\bin\mysql.exe",
        "C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe",
        "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe"
    )
    foreach ($p in $commonPaths) {
        if (Test-Path $p) {
            $mysqlPath = $p
            break
        }
    }
}

if (-not $mysqlPath) {
    Write-Host "[THONG BAO] Khong tim thay mysql trong PATH. Ban co the mo file nihon_database.sql trong MySQL Workbench de chay." -ForegroundColor Yellow
    exit 1
}

Write-Host "[*] Su dung MySQL client: $mysqlPath" -ForegroundColor Green

# 2. Thong tin ket noi
$dbHost = "localhost"
$dbPort = "3306"
$dbUser = "root"
$dbPass = ""
$dbName = "nihon_db"

$envFile = Join-Path $ScriptDir ".env"
if (Test-Path $envFile) {
    Write-Host "[*] Dang doc file .env..." -ForegroundColor Green
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#")) {
            $parts = $line.Split("=", 2)
            if ($parts.Length -eq 2) {
                $k = $parts[0].Trim()
                $v = $parts[1].Trim()
                if ($k -eq "DB_HOST") { $dbHost = $v }
                if ($k -eq "DB_PORT") { $dbPort = $v }
                if ($k -eq "DB_USER") { $dbUser = $v }
                if ($k -eq "DB_PASSWORD") { $dbPass = $v }
                if ($k -eq "DB_NAME") { $dbName = $v }
            }
        }
    }
}

Write-Host "Thong tin ket noi:"
Write-Host "- Host:     $dbHost"
Write-Host "- Port:     $dbPort"
Write-Host "- User:     $dbUser"
Write-Host "- Database: $dbName"
Write-Host ""

# 3. Thuc thi
Write-Host "[*] Dang thuc thi nihon_database.sql vao MySQL..." -ForegroundColor Cyan

$procArgs = @("--default-character-set=utf8mb4", "-h", $dbHost, "-P", $dbPort, "-u", $dbUser)
if ($dbPass) {
    $procArgs += "-p$dbPass"
}

$proc = Start-Process -FilePath $mysqlPath -ArgumentList $procArgs -RedirectStandardInput $SqlFile -NoNewWindow -PassThru -Wait

if ($proc.ExitCode -eq 0) {
    Write-Host ""
    Write-Host "===============================================================================" -ForegroundColor Green
    Write-Host "[THANH CONG] Khoi tao co so du lieu '$dbName' hoan tat 100%!" -ForegroundColor Green
    Write-Host "- Da tao database '$dbName' voi bo ma utf8mb4_unicode_ci" -ForegroundColor Green
    Write-Host "- Da tao day du 19 bang nghiep vu va 1 bang sao luu" -ForegroundColor Green
    Write-Host "===============================================================================" -ForegroundColor Green
} else {
    Write-Host "[THAT BAI] Co loi khi chay mysql (ExitCode: $($proc.ExitCode))." -ForegroundColor Red
}
