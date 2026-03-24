#!/bin/bash
# BAS — Compile & Run (Linux / macOS)
# Run from the BAS/ root: chmod +x compile_and_run.sh && ./compile_and_run.sh

echo "[BAS] Java version:"
java -version

mkdir -p out

echo ""
echo "[BAS] Compiling..."

javac -source 17 -target 17 \
  -cp "lib/*" \
  -d out \
  src/bas/model/Book.java \
  src/bas/model/LineItem.java \
  src/bas/model/SaleRecord.java \
  src/bas/model/OOSRequest.java \
  src/bas/model/User.java \
  src/bas/util/ISBNValidator.java \
  src/bas/util/EmailValidator.java \
  src/bas/util/PrinterUtil.java \
  src/bas/db/DatabaseManager.java \
  src/bas/service/EmailService.java \
  src/bas/ui/CustomerTerminalPanel.java \
  src/bas/ui/POSTerminalPanel.java \
  src/bas/ui/InventoryPanel.java \
  src/bas/ui/OwnerPanel.java \
  src/bas/ui/LoginFrame.java \
  src/bas/ui/MainFrame.java \
  src/bas/Main.java

if [ $? -ne 0 ]; then
    echo ""
    echo "[ERROR] Compilation failed."
    exit 1
fi

echo "[BAS] Compiled. Launching..."
java -cp "lib/*:out" bas.Main
