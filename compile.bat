@echo off
REM ============================================================
REM  BAS — Compile Script (Windows)
REM  Run from the BAS\ root folder.
REM  Requires: JDK 17+ and lib\sqlite-jdbc-*.jar, javax.mail.jar, javax.activation.jar
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
  src\bas\model\Book.java ^
  src\bas\model\LineItem.java ^
  src\bas\model\SaleRecord.java ^
  src\bas\model\OOSRequest.java ^
  src\bas\model\User.java ^
  src\bas\util\ISBNValidator.java ^
  src\bas\util\EmailValidator.java ^
  src\bas\util\PrinterUtil.java ^
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
    echo Make sure sqlite-jdbc, javax.mail, and javax.activation jars are physically in your lib\ folder!
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