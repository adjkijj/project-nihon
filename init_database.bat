@echo off
chcp 65001 >nul
title PROJECT NIHON - Khoi tao Co so du lieu MySQL

echo ===============================================================================
echo                PROJECT NIHON - KHOI TAO CO SO DU LIEU TOAN DIEN
echo ===============================================================================
echo.

set SCRIPT_DIR=%~dp0
set SQL_FILE=%SCRIPT_DIR%nihon_database.sql

REM 1. Kiem tra file nihon_database.sql
if not exist "%SQL_FILE%" (
    echo [CANH BAO] Khong tim thay file %SQL_FILE% trong cung thu muc!
    echo Dang thu tim file trong cac thu muc con...
    if exist "%SCRIPT_DIR%db\nihon_database.sql" (
        set SQL_FILE=%SCRIPT_DIR%db\nihon_database.sql
    ) else (
        echo [LOI] Khong the tim thay file nihon_database.sql.
        echo Vui long dam bao nihon_database.sql nam o thu muc goc cua du an.
        echo.
        pause
        exit /b 1
    )
)

REM 2. Tim kiem MySQL CLI client tren may
set MYSQL_BIN=mysql
where mysql >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    goto :FOUND_MYSQL
)

if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    set MYSQL_BIN="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
    goto :FOUND_MYSQL
)
if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" (
    set MYSQL_BIN="C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
    goto :FOUND_MYSQL
)
if exist "C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe" (
    set MYSQL_BIN="C:\Program Files\MySQL\MySQL Server 9.0\bin\mysql.exe"
    goto :FOUND_MYSQL
)
if exist "C:\xampp\mysql\bin\mysql.exe" (
    set MYSQL_BIN="C:\xampp\mysql\bin\mysql.exe"
    goto :FOUND_MYSQL
)
if exist "C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe" (
    set MYSQL_BIN="C:\laragon\bin\mysql\mysql-8.0.30-winx64\bin\mysql.exe"
    goto :FOUND_MYSQL
)
if exist "C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    set MYSQL_BIN="C:\Program Files (x86)\MySQL\MySQL Server 8.0\bin\mysql.exe"
    goto :FOUND_MYSQL
)

echo [THONG BAO] Khong tim thay lenh 'mysql' trong bien moi truong PATH.
echo Ban co the nhap duong dan thu muc bin cua MySQL hoac an Enter de thu chay mac dinh:
set /p CUSTOM_MYSQL="Duong dan thu muc bin (vi du C:\Program Files\MySQL\MySQL Server 8.0\bin): "
if not "%CUSTOM_MYSQL%"=="" (
    set MYSQL_BIN="%CUSTOM_MYSQL%\mysql.exe"
)

:FOUND_MYSQL
echo [*] Su dung MySQL client: %MYSQL_BIN%
echo.

REM 3. Thiet lap thong so ket noi mac dinh
set DB_HOST=localhost
set DB_PORT=3306
set DB_USER=root
set DB_NAME=nihon_db
set DB_PASSWORD=

REM 4. Doc thong so tu .env neu ton tai
if exist "%SCRIPT_DIR%.env" (
    echo [*] Tim thay tep .env, dang nap thong tin cau hinh database...
    for /f "usebackq tokens=1,2 delims==" %%A in ("%SCRIPT_DIR%.env") do (
        if "%%A"=="DB_HOST" set DB_HOST=%%B
        if "%%A"=="DB_PORT" set DB_PORT=%%B
        if "%%A"=="DB_USER" set DB_USER=%%B
        if "%%A"=="DB_PASSWORD" set DB_PASSWORD=%%B
        if "%%A"=="DB_NAME" set DB_NAME=%%B
    )
)

echo.
echo Thong so ket noi:
echo - Host:     %DB_HOST%
echo - Port:     %DB_PORT%
echo - User:     %DB_USER%
echo - Database: %DB_NAME%
echo.

if "%DB_PASSWORD%"=="" (
    echo [HUONG DAN] Nhap mat khau root MySQL (neu khong co mat khau, chi can nhan Enter):
    set /p DB_PASSWORD="Mat khau MySQL: "
)

echo.
echo [*] Dang thuc thi nihon_database.sql vao MySQL...
echo.

if "%DB_PASSWORD%"=="" (
    %MYSQL_BIN% --default-character-set=utf8mb4 -h %DB_HOST% -P %DB_PORT% -u %DB_USER% < "%SQL_FILE%"
) else (
    %MYSQL_BIN% --default-character-set=utf8mb4 -h %DB_HOST% -P %DB_PORT% -u %DB_USER% -p%DB_PASSWORD% < "%SQL_FILE%"
)

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===============================================================================
    echo [THANH CONG] Khoi tao co so du lieu '%DB_NAME%' hoan tat 100%!
    echo - Da tao database '%DB_NAME%' voi bo ma utf8mb4_unicode_ci
    echo - Da tao day du 19 bang nghiep vu va 1 bang sao luu (tong 20 bang)
    echo - Da thiet lap toan bo Foreign Keys, Unique Constraints va Indexes
    echo ===============================================================================
) else (
    echo.
    echo ===============================================================================
    echo [LUU Y] Thuc thi qua CLI gap loi (Ma loi: %ERRORLEVEL%).
    echo Co the do dich vu MySQL chua bat hoac sai mat khau.
    echo.
    echo BAN CO THE MO TRUC TIEP FILE:
    echo     %SQL_FILE%
    echo bang MySQL Workbench / DBeaver / HeidiSQL roi bam nut Execute (bieu tuong set)
    echo de khoi tao database mot cach de dang!
    echo ===============================================================================
)

echo.
pause
