#!/bin/bash
# BAS Test Suite — Compile, Run, Generate Report (Linux/macOS)

echo "[BAS TEST] Compiling application sources..."
mkdir -p out

javac -source 17 -target 17 -cp "lib/*" -d out \
  src/bas/config/AppConfig.java src/bas/crypto/AESUtil.java \
  src/bas/auth/JWTUtil.java src/bas/auth/SessionManager.java \
  src/bas/model/Book.java src/bas/model/LineItem.java \
  src/bas/model/SaleRecord.java src/bas/model/OOSRequest.java \
  src/bas/model/User.java src/bas/util/ISBNValidator.java \
  src/bas/util/EmailValidator.java src/bas/util/PrinterUtil.java \
  src/bas/db/ConnectionPool.java src/bas/db/BookCache.java \
  src/bas/db/DatabaseManager.java src/bas/service/EmailService.java \
  src/bas/ui/CustomerTerminalPanel.java src/bas/ui/POSTerminalPanel.java \
  src/bas/ui/InventoryPanel.java src/bas/ui/OwnerPanel.java \
  src/bas/ui/LoginFrame.java src/bas/ui/MainFrame.java src/bas/Main.java
if [ $? -ne 0 ]; then echo "[ERROR] App compilation failed."; exit 1; fi

echo "[BAS TEST] Compiling test sources..."
javac -source 17 -target 17 -cp "lib/*:out" -d out \
  src/bas/test/TestISBNValidator.java src/bas/test/TestEmailValidator.java \
  src/bas/test/TestCryptoAndAuth.java src/bas/test/TestModels.java \
  src/bas/test/TestDatabase.java src/bas/test/TestPoolAndCache.java \
  src/bas/test/TestEdgeCases.java src/bas/test/TestEmailAndPrinter.java \
  src/bas/test/TestIntegrationWorkflow.java \
  src/bas/test/TestSeedDataIntegrity.java \
  src/bas/test/TestAdvancedScenarios.java \
  src/bas/test/TestSRSCompliance.java \
  src/bas/test/TestNegativeCases.java \
  src/bas/test/TestConcurrency.java \
  src/bas/test/TestSupplementary.java \
  src/bas/test/TestReportGenerator.java
if [ $? -ne 0 ]; then echo "[ERROR] Test compilation failed."; exit 1; fi

echo ""
echo "[BAS TEST] Running all tests..."
echo "============================================================"

CP="out"
for jar in lib/*.jar; do CP="$CP:$jar"; done

rm -rf test-reports

java -jar lib/junit-platform-console-standalone-1.10.2.jar execute \
  -cp "$CP" \
  --scan-class-path out \
  --include-package bas.test \
  --exclude-classname ".*TestReportGenerator.*" \
  --details tree \
  --reports-dir test-reports

echo ""
echo "============================================================"
echo "[BAS TEST] Generating summary report..."
echo ""

java -cp "out" bas.test.TestReportGenerator test-reports

echo ""
echo "============================================================"
echo "[BAS TEST] Complete. HTML report: test-reports/BAS_Test_Report.html"
