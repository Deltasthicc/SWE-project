@echo off
REM ============================================================
REM  BAS — Compile Script (Windows)
REM  Run from the BAS\ root folder.
REM  Requires: JDK 17+
REM  Required JARs in lib\:
REM    postgresql-42.x.jar
REM    javax.mail-1.6.2.jar
REM    javax.activation-1.2.0.jar
REM  Optional: flatlaf-3.x.jar (for modern UI)
REM ============================================================

echo.
echo [BAS] Checking Java...
java -version 2>&1

if not exist out mkdir out

echo.
echo [BAS] Compiling all sources...
echo.

javac --release 17 ^
  -cp "lib\*" ^
  -d out ^
  src\bas\config\AppConfig.java ^
  src\bas\crypto\AESUtil.java ^
  src\bas\auth\JWTUtil.java ^
  src\bas\auth\SessionManager.java ^
  src\bas\model\Book.java ^
  src\bas\model\LineItem.java ^
  src\bas\model\SaleRecord.java ^
  src\bas\model\OOSRequest.java ^
  src\bas\model\User.java ^
  src\bas\util\ISBNValidator.java ^
  src\bas\util\EmailValidator.java ^
  src\bas\util\PrinterUtil.java ^
  src\bas\db\ConnectionPool.java ^
  src\bas\db\BookCache.java ^
  src\bas\db\DatabaseManager.java ^
  src\bas\service\EmailService.java ^
  src\bas\ui\CustomerTerminalPanel.java ^
  src\bas\ui\POSTerminalPanel.java ^
  src\bas\ui\InventoryPanel.java ^
  src\bas\ui\OwnerPanel.java ^
  src\bas\ui\LoginFrame.java ^
  src\bas\ui\MainFrame.java ^
  src\bas\Main.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERROR] Compilation failed. Check errors above.
    echo Make sure postgresql, javax.mail, and javax.activation jars are in lib\ folder!
    pause
    exit /b 1
)

echo.
echo [BAS] SUCCESS — compiled to out\
echo.
echo To run:   java -cp "lib\*;out" bas.Main
echo Or just double-click run.bat
echo.
pause
