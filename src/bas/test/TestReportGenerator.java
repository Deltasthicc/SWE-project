package bas.test;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Reads JUnit XML reports from test-reports/ directory,
 * prints a clean PASS/FAIL summary table to console,
 * and generates an HTML test report file.
 *
 * Run after JUnit: java -cp out bas.test.TestReportGenerator
 */
public class TestReportGenerator {

    static int totalTests = 0, totalPassed = 0, totalFailed = 0, totalSkipped = 0, totalErrors = 0;
    static double totalTime = 0;
    static List<String[]> allResults = new ArrayList<>(); // {class, name, status, time, message}
    static List<String[]> failures = new ArrayList<>();

    public static void main(String[] args) {
        String reportDir = args.length > 0 ? args[0] : "test-reports";
        File dir = new File(reportDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Report directory not found: " + reportDir);
            System.exit(1);
        }

        File[] xmlFiles = dir.listFiles((d, name) -> name.endsWith(".xml"));
        if (xmlFiles == null || xmlFiles.length == 0) {
            System.err.println("No XML report files found in " + reportDir);
            System.exit(1);
        }

        for (File f : xmlFiles) parseFile(f);

        printConsoleTable();
        generateHTML(reportDir + "/BAS_Test_Report.html");
    }

    static void parseFile(File f) {
        try {
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(f);
            NodeList testcases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < testcases.getLength(); i++) {
                Element tc = (Element) testcases.item(i);
                String className = tc.getAttribute("classname");
                String name = tc.getAttribute("name");
                String time = tc.getAttribute("time");
                totalTests++;
                totalTime += parseDouble(time);

                NodeList fails = tc.getElementsByTagName("failure");
                NodeList errors = tc.getElementsByTagName("error");
                NodeList skips = tc.getElementsByTagName("skipped");

                String status;
                String message = "";
                if (fails.getLength() > 0) {
                    status = "FAIL";
                    totalFailed++;
                    message = ((Element)fails.item(0)).getAttribute("message");
                    failures.add(new String[]{className, name, message});
                } else if (errors.getLength() > 0) {
                    status = "ERROR";
                    totalErrors++;
                    message = ((Element)errors.item(0)).getAttribute("message");
                    failures.add(new String[]{className, name, message});
                } else if (skips.getLength() > 0) {
                    status = "SKIP";
                    totalSkipped++;
                } else {
                    status = "PASS";
                    totalPassed++;
                }
                allResults.add(new String[]{shortClass(className), name, status, time + "s", message});
            }
        } catch (Exception e) {
            System.err.println("Error parsing " + f.getName() + ": " + e.getMessage());
        }
    }

    static void printConsoleTable() {
        String sep = "+" + "-".repeat(42) + "+" + "-".repeat(50) + "+" + "-".repeat(8) + "+" + "-".repeat(10) + "+";
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                              BAS TEST SUITE — RESULTS SUMMARY                                              ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println(sep);
        System.out.printf("| %-40s | %-48s | %-6s | %-8s |%n", "Test Class", "Test Name", "Result", "Time");
        System.out.println(sep);

        for (String[] r : allResults) {
            String icon = switch(r[2]) {
                case "PASS" -> "PASS";
                case "FAIL" -> "FAIL";
                case "ERROR" -> "ERR!";
                case "SKIP" -> "SKIP";
                default -> "????";
            };
            System.out.printf("| %-40s | %-48s | %-6s | %8s |%n",
                truncate(r[0], 40), truncate(r[1], 48), icon, r[3]);
        }
        System.out.println(sep);

        // Summary
        System.out.println();
        System.out.println("  TOTAL: " + totalTests + " tests  |  PASSED: " + totalPassed +
            "  |  FAILED: " + totalFailed + "  |  ERRORS: " + totalErrors +
            "  |  SKIPPED: " + totalSkipped +
            "  |  TIME: " + String.format("%.1f", totalTime) + "s");

        double passRate = totalTests > 0 ? (totalPassed * 100.0 / totalTests) : 0;
        System.out.printf("  PASS RATE: %.1f%%%n", passRate);

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("  *** FAILURES ***");
            for (int i = 0; i < failures.size(); i++) {
                String[] f = failures.get(i);
                System.out.printf("  %d. %s.%s%n     -> %s%n", i+1, shortClass(f[0]), f[1],
                    truncate(f[2], 100));
            }
        }
        System.out.println();
    }

    static void generateHTML(String path) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        double passRate = totalTests > 0 ? (totalPassed * 100.0 / totalTests) : 0;
        String statusColor = totalFailed == 0 && totalErrors == 0 ? "#22c55e" : "#ef4444";

        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        sb.append("<title>BAS Test Report</title>");
        sb.append("<style>");
        sb.append("body{font-family:'Segoe UI',sans-serif;margin:0;padding:20px 40px;background:#f8fafc;color:#1e293b}");
        sb.append("h1{color:#0f172a;border-bottom:3px solid #3b82f6;padding-bottom:10px}");
        sb.append(".summary{display:flex;gap:16px;flex-wrap:wrap;margin:20px 0}");
        sb.append(".card{background:white;border-radius:10px;padding:20px 28px;box-shadow:0 1px 3px rgba(0,0,0,0.1);text-align:center;min-width:120px}");
        sb.append(".card .num{font-size:32px;font-weight:700}.card .label{font-size:13px;color:#64748b;margin-top:4px}");
        sb.append(".pass{border-left:4px solid #22c55e}.fail{border-left:4px solid #ef4444}.skip{border-left:4px solid #f59e0b}.time{border-left:4px solid #3b82f6}");
        sb.append("table{width:100%;border-collapse:collapse;margin:20px 0;background:white;border-radius:8px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.1)}");
        sb.append("th{background:#1e293b;color:white;padding:12px 16px;text-align:left;font-size:13px}");
        sb.append("td{padding:10px 16px;border-bottom:1px solid #e2e8f0;font-size:13px}");
        sb.append("tr:hover{background:#f1f5f9}.status-PASS{color:#16a34a;font-weight:700}.status-FAIL{color:#dc2626;font-weight:700;background:#fef2f2}");
        sb.append(".status-ERROR{color:#dc2626;font-weight:700;background:#fef2f2}.status-SKIP{color:#d97706;font-weight:700}");
        sb.append(".failures{background:#fef2f2;border:1px solid #fecaca;border-radius:8px;padding:16px 24px;margin:20px 0}");
        sb.append(".failures h3{color:#dc2626;margin-top:0}.failures li{margin:8px 0}");
        sb.append(".footer{color:#94a3b8;font-size:12px;margin-top:30px;text-align:center}");
        sb.append("</style></head><body>");

        sb.append("<h1>BAS Test Suite Report</h1>");
        sb.append("<p>Bookshop Inventory & Sales Management System — Group G01</p>");
        sb.append("<p>Generated: ").append(timestamp).append("</p>");

        // Summary cards
        sb.append("<div class='summary'>");
        sb.append("<div class='card'><div class='num'>").append(totalTests).append("</div><div class='label'>Total Tests</div></div>");
        sb.append("<div class='card pass'><div class='num' style='color:#22c55e'>").append(totalPassed).append("</div><div class='label'>Passed</div></div>");
        sb.append("<div class='card fail'><div class='num' style='color:#ef4444'>").append(totalFailed + totalErrors).append("</div><div class='label'>Failed</div></div>");
        sb.append("<div class='card skip'><div class='num' style='color:#f59e0b'>").append(totalSkipped).append("</div><div class='label'>Skipped</div></div>");
        sb.append("<div class='card time'><div class='num' style='color:#3b82f6'>").append(String.format("%.1f", totalTime)).append("s</div><div class='label'>Total Time</div></div>");
        sb.append("<div class='card' style='border-left:4px solid ").append(statusColor).append("'><div class='num' style='color:").append(statusColor).append("'>").append(String.format("%.1f%%", passRate)).append("</div><div class='label'>Pass Rate</div></div>");
        sb.append("</div>");

        // Failures section
        if (!failures.isEmpty()) {
            sb.append("<div class='failures'><h3>Failed Tests (").append(failures.size()).append(")</h3><ol>");
            for (String[] f : failures) {
                sb.append("<li><strong>").append(esc(shortClass(f[0]))).append(".").append(esc(f[1]));
                sb.append("</strong><br><code>").append(esc(f[2])).append("</code></li>");
            }
            sb.append("</ol></div>");
        }

        // Full results table
        sb.append("<h2>All Test Results</h2>");
        sb.append("<table><thead><tr><th>#</th><th>Test Class</th><th>Test Method</th><th>Status</th><th>Time</th></tr></thead><tbody>");
        int idx = 1;
        for (String[] r : allResults) {
            sb.append("<tr class='status-").append(r[2]).append("'>");
            sb.append("<td>").append(idx++).append("</td>");
            sb.append("<td>").append(esc(r[0])).append("</td>");
            sb.append("<td>").append(esc(r[1])).append("</td>");
            sb.append("<td class='status-").append(r[2]).append("'>").append(r[2]).append("</td>");
            sb.append("<td>").append(r[3]).append("</td>");
            sb.append("</tr>");
        }
        sb.append("</tbody></table>");

        sb.append("<div class='footer'>BAS Test Report — Shiv Nadar IoE, Group G01 — ").append(timestamp).append("</div>");
        sb.append("</body></html>");

        try {
            Files.writeString(Path.of(path), sb.toString());
            System.out.println("  HTML report: " + path);
        } catch (IOException e) {
            System.err.println("Failed to write HTML report: " + e.getMessage());
        }
    }

    static String shortClass(String full) {
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
    }

    static double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (Exception e) { return 0; }
    }
}
