package com.tp;

public class Analyzer {
    public static void main(String[] args) throws Exception {
        String projectPath = (args.length > 0)
                ? args[0]
                : "/Users/clstialdsn/eclipse-workspace/company-app/src/main/java";

        ParserAnalyzer pa = new ParserAnalyzer(projectPath);
        pa.analyze();
        pa.printStatistics(3); // Example: X=3
        pa.printCallGraph();
    }
}