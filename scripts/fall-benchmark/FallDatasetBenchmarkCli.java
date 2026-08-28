package com.yinxing.launcher.feature.fall;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FallDatasetBenchmarkCli {
    private static final Pattern MOVEMENT_PATTERN =
        Pattern.compile("_(Fall|ADL)_(.+)_\\d+_\\d{4}-");
    private static final Pattern SUBJECT_PATTERN = Pattern.compile("Subject_(\\d+)");

    private FallDatasetBenchmarkCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new IllegalArgumentException(
                "Usage: <dataset-directory> <report-path> [--simulate-call-context]"
            );
        }
        boolean simulateCallContext = args.length == 3 &&
            "--simulate-call-context".equals(args[2]);
        if (args.length == 3 && !simulateCallContext) {
            throw new IllegalArgumentException("Unknown option: " + args[2]);
        }

        Path datasetDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Path reportPath = Path.of(args[1]).toAbsolutePath().normalize();
        if (!Files.isDirectory(datasetDirectory)) {
            throw new IllegalArgumentException("Dataset directory does not exist: " + datasetDirectory);
        }

        List<Path> traces = new ArrayList<>();
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(datasetDirectory, "*.csv")) {
            entries.forEach(traces::add);
        }
        traces.sort(Comparator.comparing(path -> path.getFileName().toString()));

        Summary summary = new Summary(simulateCallContext);
        List<TraceResult> details = new ArrayList<>();
        for (Path trace : traces) {
            TraceResult result = evaluate(trace, simulateCallContext);
            details.add(result);
            summary.accept(result);
        }

        Files.createDirectories(reportPath.getParent());
        Path detailPath = reportPath.resolveSibling(
            simulateCallContext
                ? "umafall-call-context-trace-results.csv"
                : "umafall-trace-results.csv"
        );
        Files.writeString(reportPath, summary.toMarkdown(detailPath.getFileName().toString()), StandardCharsets.UTF_8);
        writeDetails(detailPath, details);

        System.out.printf(
            Locale.US,
            "falls=%d/%d (%.2f%%), falseAlarms=%d/%d, skipped=%d, callContextSimulation=%s%nreport=%s%ndetails=%s%n",
            summary.detectedFalls,
            summary.fallTraces,
            summary.sensitivity() * 100.0,
            summary.falseAlarms,
            summary.adlTraces,
            summary.skipped,
            simulateCallContext,
            reportPath,
            detailPath
        );
    }

    private static TraceResult evaluate(Path path, boolean simulateCallContext) throws IOException {
        String fileName = path.getFileName().toString();
        Matcher subjectMatcher = SUBJECT_PATTERN.matcher(fileName);
        if (!subjectMatcher.find()) {
            throw new IllegalArgumentException("Missing subject number: " + fileName);
        }
        int subject = Integer.parseInt(subjectMatcher.group(1));
        boolean fall = fileName.toLowerCase(Locale.ROOT).contains("_fall_");
        String movement = movementName(fileName);
        FallDetectionContext detectionContext = simulateCallContext &&
            !fall && movement.equalsIgnoreCase("MakingACall")
                ? FallDetectionContext.CallTransition
                : FallDetectionContext.Normal;
        int phoneSensorId = -1;
        int samples = 0;
        boolean detected = false;
        FallDetectionEngine engine = new FallDetectionEngine();

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(";", -1);
                if (line.startsWith("%") && columns.length >= 3 &&
                    columns[2].trim().equalsIgnoreCase("RIGHTPOCKET")) {
                    phoneSensorId = Integer.parseInt(columns[1].trim());
                    continue;
                }
                if (detected || line.isEmpty() || !Character.isDigit(line.charAt(0)) || columns.length < 7) {
                    continue;
                }

                int sensorType = Integer.parseInt(columns[5].trim());
                int sensorId = Integer.parseInt(columns[6].trim());
                if (sensorType != 0 || sensorId != phoneSensorId) {
                    continue;
                }

                long timestampMs = Long.parseLong(columns[0].trim());
                float xG = Float.parseFloat(columns[2].trim());
                float yG = Float.parseFloat(columns[3].trim());
                float zG = Float.parseFloat(columns[4].trim());
                samples += 1;
                detected = engine.acceptG$app(timestampMs, xG, yG, zG, detectionContext) ==
                    FallDetectionEvent.PossibleFall;
            }
        }

        return new TraceResult(fileName, subject, fall, movement, detected, samples);
    }

    private static String movementName(String fileName) {
        Matcher matcher = MOVEMENT_PATTERN.matcher(fileName);
        return matcher.find() ? matcher.group(2) : "Unknown";
    }

    private static void writeDetails(Path path, List<TraceResult> details) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("file,subject,label,movement,detected,phone_samples");
            writer.newLine();
            for (TraceResult result : details) {
                writer.write(csv(result.fileName));
                writer.write(',');
                writer.write(Integer.toString(result.subject));
                writer.write(',');
                writer.write(result.fall ? "FALL" : "ADL");
                writer.write(',');
                writer.write(csv(result.movement));
                writer.write(',');
                writer.write(Boolean.toString(result.detected));
                writer.write(',');
                writer.write(Integer.toString(result.samples));
                writer.newLine();
            }
        }
    }

    private static String csv(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static final class TraceResult {
        final String fileName;
        final int subject;
        final boolean fall;
        final String movement;
        final boolean detected;
        final int samples;

        TraceResult(
            String fileName,
            int subject,
            boolean fall,
            String movement,
            boolean detected,
            int samples
        ) {
            this.fileName = fileName;
            this.subject = subject;
            this.fall = fall;
            this.movement = movement;
            this.detected = detected;
            this.samples = samples;
        }
    }

    private static final class MovementStats {
        int total;
        int detected;
    }

    private static final class Summary {
        final boolean simulateCallContext;
        int fallTraces;
        int detectedFalls;
        int adlTraces;
        int falseAlarms;
        int skipped;
        final CohortStats development = new CohortStats();
        final CohortStats heldOut = new CohortStats();
        final Map<String, MovementStats> fallTypes = new LinkedHashMap<>();
        final Map<String, MovementStats> adlTypes = new LinkedHashMap<>();

        Summary(boolean simulateCallContext) {
            this.simulateCallContext = simulateCallContext;
        }

        void accept(TraceResult result) {
            if (result.samples == 0) {
                skipped += 1;
            }
            Map<String, MovementStats> target = result.fall ? fallTypes : adlTypes;
            MovementStats stats = target.computeIfAbsent(result.movement, ignored -> new MovementStats());
            stats.total += 1;
            if (result.detected) stats.detected += 1;

            if (result.fall) {
                fallTraces += 1;
                if (result.detected) detectedFalls += 1;
            } else {
                adlTraces += 1;
                if (result.detected) falseAlarms += 1;
            }
            (result.subject <= 13 ? development : heldOut).accept(result);
        }

        double sensitivity() {
            return fallTraces == 0 ? 0.0 : (double) detectedFalls / fallTraces;
        }

        double specificity() {
            return adlTraces == 0 ? 0.0 : (double) (adlTraces - falseAlarms) / adlTraces;
        }

        String toMarkdown(String detailFileName) {
            StringBuilder report = new StringBuilder();
            report.append("# 跌倒检测公开数据基准报告\n\n")
                .append("- 数据集：UMAFall corrected version（CC BY 4.0）\n")
                .append("- 生成时间：").append(Instant.now()).append("\n")
                .append("- 数据位置：手机位于右裤袋\n")
                .append("- 检测逻辑：撞击峰值 → 瞬时方向变化 → 前后姿态变化\n")
                .append("- 通话场景门控模拟：")
                .append(simulateCallContext ? "开启（MakingACall 标签注入通话过渡状态）" : "关闭")
                .append("\n\n")
                .append("| 指标 | 结果 |\n| --- | ---: |\n")
                .append("| 跌倒记录 | ").append(fallTraces).append(" |\n")
                .append("| 检出跌倒 | ").append(detectedFalls).append(" |\n")
                .append("| 漏报记录 | ").append(fallTraces - detectedFalls).append(" |\n")
                .append("| 检出率 | ").append(percent(sensitivity())).append(" |\n")
                .append("| 日常活动记录 | ").append(adlTraces).append(" |\n")
                .append("| 误报记录 | ").append(falseAlarms).append(" |\n")
                .append("| 特异度 | ").append(percent(specificity())).append(" |\n")
                .append("| 无有效手机样本 | ").append(skipped).append(" |\n\n");

            report.append("## 按受试者留出验证\n\n")
                .append("| 人群 | 跌倒检出 | 检出率 | 日常活动误报 | 特异度 |\n")
                .append("| --- | ---: | ---: | ---: | ---: |\n");
            appendCohort(report, "调参组（1–13）", development);
            appendCohort(report, "留出组（14–19）", heldOut);
            report.append('\n');

            appendMovementTable(report, "跌倒类型", fallTypes, true);
            appendMovementTable(report, "日常活动误报", adlTypes, false);
            report.append("## 明细\n\n每条记录的结果见 `")
                .append(detailFileName)
                .append("`。\n\n## 验证边界\n\n")
                .append("这是公开实验数据的离线回放结果，不能代表真机准确率，也不能替代真实携带位置和长期误报测试。\n");
            if (simulateCallContext) {
                report.append("通话状态来自数据标签注入，仅验证场景门控效果，不能视为数据集自身提供了真实电话系统状态。\n");
            }
            return report.toString();
        }

        private static void appendMovementTable(
            StringBuilder report,
            String title,
            Map<String, MovementStats> values,
            boolean fall
        ) {
            report.append("## ").append(title).append("\n\n")
                .append(fall ? "| 类型 | 总数 | 检出 | 漏报 | 检出率 |\n| --- | ---: | ---: | ---: | ---: |\n"
                    : "| 类型 | 总数 | 误报 | 特异度 |\n| --- | ---: | ---: | ---: |\n");
            values.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                MovementStats stats = entry.getValue();
                report.append("| ").append(entry.getKey()).append(" | ").append(stats.total).append(" | ")
                    .append(stats.detected).append(" | ");
                if (fall) {
                    report.append(stats.total - stats.detected).append(" | ")
                        .append(percent((double) stats.detected / stats.total)).append(" |\n");
                } else {
                    report.append(percent((double) (stats.total - stats.detected) / stats.total)).append(" |\n");
                }
            });
            report.append('\n');
        }

        private static String percent(double value) {
            return String.format(Locale.US, "%.2f%%", value * 100.0);
        }

        private static void appendCohort(StringBuilder report, String name, CohortStats cohort) {
            report.append("| ").append(name).append(" | ")
                .append(cohort.detectedFalls).append('/').append(cohort.falls).append(" | ")
                .append(percent(cohort.sensitivity())).append(" | ")
                .append(cohort.falseAlarms).append('/').append(cohort.adl).append(" | ")
                .append(percent(cohort.specificity())).append(" |\n");
        }
    }

    private static final class CohortStats {
        int falls;
        int detectedFalls;
        int adl;
        int falseAlarms;

        void accept(TraceResult result) {
            if (result.fall) {
                falls += 1;
                if (result.detected) detectedFalls += 1;
            } else {
                adl += 1;
                if (result.detected) falseAlarms += 1;
            }
        }

        double sensitivity() {
            return falls == 0 ? 0.0 : (double) detectedFalls / falls;
        }

        double specificity() {
            return adl == 0 ? 0.0 : (double) (adl - falseAlarms) / adl;
        }
    }
}
