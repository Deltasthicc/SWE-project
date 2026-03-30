@echo off
REM ============================================================
REM  BAS Test Suite — Compile, Run, Generate Report (Windows)
REM  Requires: junit-platform-console-standalone-1.10.2.jar in lib\
REM ============================================================

echo.
echo [BAS TEST] Compiling application sources...
if not exist out mkdir out

javac --release 17 -cp "lib\*" -d out ^
  src\bas\config\AppConfig.java src\bas\crypto\AESUtil.java ^
  src\bas\auth\JWTUtil.java src\bas\auth\SessionManager.java ^
  src\bas\model\Book.java src\bas\model\LineItem.java ^
  src\bas\model\SaleRecord.java src\bas\model\OOSRequest.java ^
  src\bas\model\User.java src\bas\util\ISBNValidator.java ^
  src\bas\util\EmailValidator.java src\bas\util\PrinterUtil.java ^
  src\bas\db\ConnectionPool.java src\bas\db\BookCache.java ^
  src\bas\db\DatabaseManager.java src\bas\service\EmailService.java ^
  src\bas\ui\CustomerTerminalPanel.java src\bas\ui\POSTerminalPanel.java ^
  src\bas\ui\InventoryPanel.java src\bas\ui\OwnerPanel.java ^
  src\bas\ui\LoginFrame.java src\bas\ui\MainFrame.java src\bas\Main.java

if %ERRORLEVEL% NEQ 0 ( echo [ERROR] App compilation failed. & pause & exit /b 1 )

echo [BAS TEST] Compiling test sources...

javac --release 17 -cp "lib\*;out" -d out ^
  src\bas\test\TestISBNValidator.java src\bas\test\TestEmailValidator.java ^
  src\bas\test\TestCryptoAndAuth.java src\bas\test\TestModels.java ^
  src\bas\test\TestDatabase.java src\bas\test\TestPoolAndCache.java ^
  src\bas\test\TestEdgeCases.java src\bas\test\TestEmailAndPrinter.java ^
  src\bas\test\TestIntegrationWorkflow.java ^
  src\bas\test\TestSeedDataIntegrity.java ^
  src\bas\test\TestAdvancedScenarios.java ^
  src\bas\test\TestSRSCompliance.java ^
  src\bas\test\TestNegativeCases.java ^
  src\bas\test\TestConcurrency.java ^
  src\bas\test\TestSupplementary.java ^
  src\bas\test\TestReportGenerator.java

if %ERRORLEVEL% NEQ 0 ( echo [ERROR] Test compilation failed. & pause & exit /b 1 )

echo.
echo [BAS TEST] Running all tests...
echo ============================================================

REM Build classpath explicitly
setlocal enabledelayedexpansion
set "CP=out"
for %%f in (lib\*.jar) do set "CP=!CP!;%%f"

REM Clean old reports
if exist test-reports rmdir /s /q test-reports

java -jar lib\junit-platform-console-standalone-1.10.2.jar execute ^
  -cp "%CP%" ^
  --scan-class-path out ^
  --include-package bas.test ^
  --exclude-classname ".*TestReportGenerator.*" ^
  --details tree ^
  --reports-dir test-reports

echo.
echo ============================================================
echo [BAS TEST] Generating summary report...
echo.

java -cp "out" bas.test.TestReportGenerator test-reports

echo.
echo ============================================================
echo [BAS TEST] Complete. HTML report: test-reports\BAS_Test_Report.html
pause
