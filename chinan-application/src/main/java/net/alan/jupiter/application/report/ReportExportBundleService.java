package net.alan.jupiter.application.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.ClassInfo;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.model.LeaveDetailData;
import net.alan.jupiter.domain.model.NightCheckSummaryData;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.ClassInfoRepository;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.infrastructure.excel.ExcelExportHelper;
import net.alan.jupiter.infrastructure.excel.ProofPathResolver;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportExportBundleService {

    private final ReportQueryService reportQueryService;
    private final ExcelExportHelper excelExportHelper;
    private final DormBuildingRepository buildingRepository;
    private final ClassInfoRepository classInfoRepository;
    private final StudentRepository studentRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final TaskRepository taskRepository;

    static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    public ExportResult exportBundle(String taskId, String outputDir) throws IOException {
        boolean allTasks = (taskId == null || taskId.isBlank());
        String dateStr = allTasks
                ? LocalDate.now().toString()
                : resolveSingleTaskDate(taskId);
        // 保留原有导出目录结构，只去掉时间后缀（不再出现 _HHmmss 一串数字）
        Path dir = Path.of(outputDir, "晚查寝_" + dateStr);
        Files.createDirectories(dir);

        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();
        List<String> failedBuildings = new ArrayList<>();
        List<String> failedClasses = new ArrayList<>();

        ReportQueryService.ExportContext ctx = null;
        if (allTasks) {
            ctx = reportQueryService.prepareExportContext();
        }

        Path buildingDir = dir.resolve("楼栋报表");
        Path classDir = dir.resolve("班级报表");
        Path proofDir = dir.resolve(ProofPathResolver.PROOF_ROOT)
                .resolve(ProofPathResolver.BUILDING_SUB);
        Files.createDirectories(proofDir);

        List<ExcelExportHelper.BuildingSummaryData> summaryDataList = new ArrayList<>();
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">")
                .append("<title>查寝汇总 - ").append(dateStr).append("</title>")
                .append("<style>body{font-family:sans-serif;margin:20px;}")
                .append("h2{color:#333;margin-top:24px;}h3{margin-left:24px;color:#555;margin-top:16px;}")
                .append("p{margin:4px 0;padding-left:12px;}</style></head>\n<body>\n")
                .append("<h1>查寝汇总报告 - ").append(esc(dateStr)).append("</h1>\n")
                .append("<p>生成时间: ")
                .append(esc(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))))
                .append("</p>\n");

        writeBuildingReports(buildingDir, taskId, buildings, allTasks,
                proofDir, dateStr, ctx,
                summaryDataList, htmlContent, failedBuildings);
        writeClassReports(classDir, taskId, buildings, allTasks,
                proofDir, ctx, failedClasses);

        htmlContent.append("</body>\n</html>");

        if (!summaryDataList.isEmpty()) {
            byte[] summaryExcel = excelExportHelper.exportBuildingSummary(summaryDataList);
            Files.write(dir.resolve("楼栋汇总表.xlsx"), summaryExcel);
            Files.write(dir.resolve("查寝汇总报告.html"),
                    htmlContent.toString().getBytes(StandardCharsets.UTF_8));
        }

        // 只替换「汇总」和「请假明细」两份文件：全量口径、不带学院名、不带时间后缀
        Map<String, CheckRecord> recordMap = getRecordMap(taskId, allTasks, ctx);
        if (recordMap != null) {
            NightCheckSummaryData summary =
                    reportQueryService.buildNightCheckSummary("", recordMap, dateStr);
            if (!summary.getBuildingRows().isEmpty()) {
                Files.write(dir.resolve("晚查寝数据汇总.xlsx"),
                        excelExportHelper.exportNightCheckSummary(summary));
            }
            LeaveDetailData leaveDetail =
                    reportQueryService.buildLeaveDetail("", recordMap, dateStr);
            if (!leaveDetail.getRows().isEmpty()) {
                Files.write(dir.resolve("请假明细.xlsx"),
                        excelExportHelper.exportLeaveDetail(leaveDetail, null));
            }
        }

        log.info("导出报表文件夹: {} (楼栋:{}个成功/{}个失败, 班级:{}个失败)",
                dir, buildings.size() - failedBuildings.size(), failedBuildings.size(),
                failedClasses.size());
        return new ExportResult(dir, failedBuildings, failedClasses);
    }

    private void writeBuildingReports(Path buildingDir, String taskId,
                                      List<DormBuilding> buildings, boolean allTasks,
                                      Path proofDir, String dateStr,
                                      ReportQueryService.ExportContext ctx,
                                      List<ExcelExportHelper.BuildingSummaryData> summaryDataList,
                                      StringBuilder htmlContent,
                                      List<String> failedBuildings) throws IOException {
        for (DormBuilding building : buildings) {
            try {
                ReportQueryService.BuildingReportData reportData =
                        (ReportQueryService.BuildingReportData) (allTasks
                                ? ctx != null
                                ? reportQueryService.generateAggregatedReport(
                                ReportQueryService.ReportType.BUILDING, building.getId(),
                                ctx.mergedRecordMap(), ctx.dateRange())
                                : reportQueryService.generateAggregatedReport(
                                ReportQueryService.ReportType.BUILDING, building.getId())
                                : reportQueryService.generateReport(
                                ReportQueryService.ReportType.BUILDING, taskId, building.getId()));

                String safeName = building.getName().replaceAll("[\\\\/:*?\"<>|]", "_");

                exportBuildingFloorReports(buildingDir, building.getName(), safeName,
                        reportData, proofDir);

                collectBuildingSummary(dateStr, building.getName(), reportData,
                        summaryDataList, htmlContent);
                writeBuildingSummaryPage(buildingDir, dateStr, building.getName(),
                        reportData, safeName);
            } catch (Exception e) {
                log.warn("导出楼栋报表失败: {}, 错误: {}", building.getName(), e.getMessage());
                failedBuildings.add(building.getName());
            }
        }
    }

    private void exportBuildingFloorReports(Path buildingDir, String buildingName,
                                            String safeName,
                                            ReportQueryService.BuildingReportData reportData,
                                            Path proofDir) throws IOException {
        Map<String, List<ReportQueryService.BuildingReportData.RoomDetail>> byFloor =
                reportData.getRoomDetails().stream()
                        .collect(Collectors.groupingBy(
                                rd -> rd.getFloor() != null ? rd.getFloor() : "未知",
                                LinkedHashMap::new,
                                Collectors.toList()));

        for (var entry : byFloor.entrySet()) {
            String floor = entry.getKey();
            String floorLabel = safeLabel(floor) + "层";
            Path floorOutputDir = buildingDir.resolve(safeName).resolve(floorLabel);
            Files.createDirectories(floorOutputDir);

            var detailRows = entry.getValue().stream()
                    .map(rd -> ExcelExportHelper.BuildingDetailExportRow.builder()
                            .floor(rd.getFloor())
                            .roomNumber(rd.getRoomNumber())
                            .studentNo(rd.getStudentNo())
                            .studentName(rd.getStudentName())
                            .className(rd.getClassName())
                            .checkStatus(rd.getCheckStatus())
                            .leaveType(rd.getLeaveType())
                            .proofFilePath(rd.getProofFile())
                            .remark(rd.getRemark())
                            .studentRemark(rd.getStudentRemark())
                            .build())
                    .toList();

            byte[] data = excelExportHelper.exportBuildingFloorReport(
                    detailRows, buildingName, floorLabel, proofDir);
            Files.write(floorOutputDir.resolve(floorLabel + ".xlsx"), data);
        }

        List<ExcelExportHelper.BuildingFloorSummary> summaries =
                reportData.getFloorSummaries().stream()
                        .map(fs -> ExcelExportHelper.BuildingFloorSummary.builder()
                                .floor(fs.getFloor())
                                .should(fs.getShould())
                                .actual(fs.getActual())
                                .personalLeave(fs.getPersonalLeave())
                                .sickLeave(fs.getSickLeave())
                                .lateReturn(fs.getLateReturn())
                                .military(fs.getMilitary())
                                .intern(fs.getIntern())
                                .absent(fs.getAbsent())
                                .unchecked(fs.getUnchecked())
                                .absentNames(fs.getAbsentNames())
                                .build())
                        .toList();

        List<ExcelExportHelper.BuildingDetailExportRow> allDetailRows =
                reportData.getRoomDetails().stream()
                        .map(rd -> ExcelExportHelper.BuildingDetailExportRow.builder()
                                .floor(rd.getFloor())
                                .roomNumber(rd.getRoomNumber())
                                .studentNo(rd.getStudentNo())
                                .studentName(rd.getStudentName())
                                .className(rd.getClassName())
                                .checkStatus(rd.getCheckStatus())
                                .leaveType(rd.getLeaveType())
                                .proofFilePath(rd.getProofFile())
                                .remark(rd.getRemark())
                                .studentRemark(rd.getStudentRemark())
                                .build())
                        .toList();

        byte[] oldFormatData = excelExportHelper.exportBuildingReport(
                summaries, allDetailRows, buildingName, proofDir);
        Files.write(buildingDir.resolve(safeName).resolve("汇总.xlsx"), oldFormatData);
    }

    private void collectBuildingSummary(String dateStr, String buildingName,
                                        ReportQueryService.BuildingReportData reportData,
                                        List<ExcelExportHelper.BuildingSummaryData> summaryDataList,
                                        StringBuilder htmlContent) {
        List<ExcelExportHelper.BuildingFloorSummary> floorSummaries = new ArrayList<>();
        int totalShould = 0, totalActual = 0, totalPersonalLeave = 0, totalSickLeave = 0;
        int totalLateReturn = 0, totalMilitary = 0, totalIntern = 0, totalAbsent = 0, totalUnchecked = 0;

        Map<String, List<String>> floorLeaveNames = new LinkedHashMap<>();
        Map<String, List<String>> floorAbsentNames = new LinkedHashMap<>();

        for (ReportQueryService.BuildingReportData.RoomDetail rd : reportData.getRoomDetails()) {
            String floor = rd.getFloor() != null ? rd.getFloor() : "未知";
            String checkStatus = rd.getCheckStatus();
            String className = rd.getClassName() != null ? rd.getClassName() : "";
            String label = esc(rd.getStudentName()) + "(" + esc(className)
                    + "|" + esc(rd.getRoomNumber()) + ")";

            if ("事假".equals(checkStatus) || "病假".equals(checkStatus)
                    || "长期病假".equals(checkStatus)) {
                floorLeaveNames.computeIfAbsent(floor, k -> new ArrayList<>()).add(label);
            } else if ("旷宿".equals(checkStatus)) {
                floorAbsentNames.computeIfAbsent(floor, k -> new ArrayList<>()).add(label);
            }
        }

        for (ReportQueryService.BuildingReportData.FloorSummary fs : reportData.getFloorSummaries()) {
            floorSummaries.add(ExcelExportHelper.BuildingFloorSummary.builder()
                    .floor(fs.getFloor())
                    .should(fs.getShould())
                    .actual(fs.getActual())
                    .personalLeave(fs.getPersonalLeave())
                    .sickLeave(fs.getSickLeave())
                    .lateReturn(fs.getLateReturn())
                    .military(fs.getMilitary())
                    .intern(fs.getIntern())
                    .absent(fs.getAbsent())
                    .unchecked(fs.getUnchecked())
                    .absentNames(fs.getAbsentNames())
                    .build());

            totalShould += fs.getShould();
            totalActual += fs.getActual();
            totalPersonalLeave += fs.getPersonalLeave();
            totalSickLeave += fs.getSickLeave();
            totalLateReturn += fs.getLateReturn();
            totalMilitary += fs.getMilitary();
            totalIntern += fs.getIntern();
            totalAbsent += fs.getAbsent();
            totalUnchecked += fs.getUnchecked();
        }

        summaryDataList.add(ExcelExportHelper.BuildingSummaryData.builder()
                .buildingName(buildingName)
                .floorSummaries(floorSummaries)
                .totalShould(totalShould)
                .totalActual(totalActual)
                .totalPersonalLeave(totalPersonalLeave)
                .totalSickLeave(totalSickLeave)
                .totalLateReturn(totalLateReturn)
                .totalMilitary(totalMilitary)
                .totalIntern(totalIntern)
                .totalAbsent(totalAbsent)
                .totalUnchecked(totalUnchecked)
                .build());

        int totalLeave = totalPersonalLeave + totalSickLeave;
        htmlContent.append("<h2>").append(esc(dateStr)).append(" ")
                .append(esc(buildingName)).append("查宿情况</h2>\n");
        htmlContent.append("<p>应到").append(totalShould)
                .append("，实到").append(totalActual)
                .append("，请假").append(totalLeave)
                .append("，旷宿").append(totalAbsent).append("</p>\n");
        for (ExcelExportHelper.BuildingFloorSummary fs : floorSummaries) {
            int floorLeave = fs.getPersonalLeave() + fs.getSickLeave();
            htmlContent.append("<h3>").append(esc(fs.getFloor())).append("层：</h3>\n");
            htmlContent.append("<p>应到").append(fs.getShould())
                    .append("，实到").append(fs.getActual())
                    .append("，请假").append(floorLeave)
                    .append("，旷宿").append(fs.getAbsent()).append("</p>\n");

            List<String> leaves = floorLeaveNames.get(fs.getFloor());
            if (leaves != null && !leaves.isEmpty()) {
                htmlContent.append("<p>请假人员：");
                htmlContent.append(String.join("，", leaves));
                htmlContent.append("</p>\n");
            }
            List<String> absents = floorAbsentNames.get(fs.getFloor());
            if (absents != null && !absents.isEmpty()) {
                htmlContent.append("<p>旷宿人员：");
                htmlContent.append(String.join("，", absents));
                htmlContent.append("</p>\n");
            }
        }
    }

    private void writeBuildingSummaryPage(Path buildingDir, String dateStr, String buildingName,
                                          ReportQueryService.BuildingReportData reportData,
                                          String safeName) throws IOException {
        int totalShould = 0, totalActual = 0, totalLeave = 0, totalAbsent = 0;
        int totalMilitary = 0, totalIntern = 0;
        for (ReportQueryService.BuildingReportData.FloorSummary fs : reportData.getFloorSummaries()) {
            totalShould += fs.getShould();
            totalActual += fs.getActual();
            totalLeave += fs.getPersonalLeave() + fs.getSickLeave();
            totalAbsent += fs.getAbsent();
            totalMilitary += fs.getMilitary();
            totalIntern += fs.getIntern();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">")
                .append("<title>").append(esc(buildingName)).append("查宿情况 - ")
                .append(esc(dateStr)).append("</title>")
                .append("<style>body{font-family:sans-serif;margin:20px;}")
                .append("h1{color:#333;}p{margin:8px 0;font-size:16px;}</style></head>\n<body>\n")
                .append("<h1>").append(esc(dateStr)).append(" ")
                .append(esc(buildingName)).append("查宿情况：</h1>\n")
                .append("<p>应到学生数：").append(totalShould).append("人</p>\n")
                .append("<p>实到学生数：").append(totalActual).append("人</p>\n")
                .append("<p>请假学生数：").append(totalLeave).append("人</p>\n")
                .append("<p>旷宿学生数：").append(totalAbsent).append("人</p>\n")
                .append("<p>征兵学生数：").append(totalMilitary).append("人</p>\n")
                .append("<p>实习学生数：").append(totalIntern).append("人</p>\n")
                .append("</body>\n</html>");

        Files.write(buildingDir.resolve(safeName).resolve("查宿情况.html"),
                sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    // P0#2: 用投影查询替代 findAll()
    private void writeClassReports(Path classDir, String taskId,
                                   List<DormBuilding> buildings, boolean allTasks,
                                   Path proofDir,
                                   ReportQueryService.ExportContext ctx,
                                   List<String> failedClasses) throws IOException {
        Files.createDirectories(classDir);

        Set<String> buildingIds = buildings.stream()
                .map(DormBuilding::getId).collect(Collectors.toSet());
        Set<String> classIds = new LinkedHashSet<>(
                studentRepository.findDistinctClassIdsByBuildingIds(
                        new ArrayList<>(buildingIds)));

        for (String classId : classIds) {
            try {
                ClassInfo classInfo = classInfoRepository.findById(classId).orElse(null);
                if (classInfo == null) continue;

                ReportQueryService.ClassReportData reportData =
                        (ReportQueryService.ClassReportData) (allTasks
                                ? ctx != null
                                ? reportQueryService.generateAggregatedReport(
                                ReportQueryService.ReportType.CLASS, classId,
                                ctx.mergedRecordMap(), ctx.dateRange())
                                : reportQueryService.generateAggregatedReport(
                                ReportQueryService.ReportType.CLASS, classId)
                                : reportQueryService.generateReport(
                                ReportQueryService.ReportType.CLASS, taskId, classId));

                if (reportData.getRows() == null || reportData.getRows().isEmpty()) continue;

                var exportRows = reportData.getRows().stream()
                        .map(row -> ExcelExportHelper.ClassReportExportRow.builder()
                                .studentNo(row.getStudentNo())
                                .studentName(row.getStudentName())
                                .gender(row.getGender())
                                .buildingName(row.getBuildingName())
                                .floorNumber(row.getFloorNumber())
                                .roomNumber(row.getRoomNumber())
                                .checkStatus(row.getCheckStatus())
                                .leaveType(row.getLeaveType())
                                .proofFilePath(row.getProofFilePath())
                                .remark(row.getRemark())
                                .studentRemark(row.getStudentRemark())
                                .build())
                        .toList();

                String safeClassName = classInfo.getName().replaceAll("[\\\\/:*?\"<>|]", "_");

                // P1#8: 班级报表证明文件统一存入楼栋证明目录，避免跨楼栋冲突
                byte[] data = excelExportHelper.exportClassReport(
                        exportRows, classInfo.getName(), "", proofDir);
                Files.write(classDir.resolve(safeClassName + ".xlsx"), data);
            } catch (Exception e) {
                log.warn("导出班级报表失败: classId={}, 错误: {}", classId, e.getMessage());
                failedClasses.add(classId);
            }
        }
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String safeLabel(String s) {
        if (s == null) return "";
        return s.replaceAll("[\\\\/?*\\[\\]:$]", "_");
    }

    private String resolveSingleTaskDate(String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BizException(ErrorCode.TASK_NOT_FOUND));
        if (task.getTaskDate() != null) return task.getTaskDate().toString();
        if (task.getCreatedAt() != null) return task.getCreatedAt().toLocalDate().toString();
        return java.time.LocalDate.now().toString();
    }

    // ========================================================================
    // 学院级导出：晚查寝数据汇总 + 请假明细
    // ========================================================================

    /**
     * 为学院级导出准备查寝记录 Map。
     * @return null 表示没有可用数据，跳过学院级导出
     */
    private Map<String, CheckRecord> getRecordMap(String taskId, boolean allTasks,
                                                    ReportQueryService.ExportContext ctx) {
        if (allTasks) {
            if (ctx == null) return null;
            return ctx.mergedRecordMap();
        }
        // 单任务：加载该任务的查寝记录
        var records = checkRecordRepository.findByTaskId(taskId);
        if (records.isEmpty()) return null;
        return records.stream()
                .filter(r -> r.getStudent() != null)
                .collect(Collectors.toMap(
                        r -> r.getStudent().getId(),
                        r -> r,
                        (a, b) -> a));
    }

    private void writeCollegeLevelReports(Path dir, String dateStr,
                                           Map<String, CheckRecord> recordMap,
                                           boolean allTasks) throws IOException {
        Set<String> departments = classInfoRepository.findAll().stream()
                .map(ClassInfo::getDepartment)
                .filter(d -> d != null && !d.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (departments.isEmpty()) {
            log.warn("学院级报表: 没有可用的部门数据（class_info 表中 department 字段均为空）");
            return;
        }

        int summaryWritten = 0;
        int leaveWritten = 0;

        for (String dept : departments) {
            try {
                NightCheckSummaryData summary = reportQueryService
                        .buildNightCheckSummary(dept, recordMap, dateStr);
                if (!summary.getBuildingRows().isEmpty()) {
                    byte[] summaryBytes = excelExportHelper.exportNightCheckSummary(summary);
                    String safeDept = safeLabel(dept);
                    Files.write(dir.resolve(safeDept + "_晚查寝数据汇总.xlsx"), summaryBytes);
                    summaryWritten++;
                    log.info("学院级报表: 已生成 [{}] 晚查寝数据汇总", dept);
                } else {
                    log.info("学院级报表: [{}] 晚查寝数据汇总 无匹配学生，跳过", dept);
                }

                LeaveDetailData leaveDetail = reportQueryService
                        .buildLeaveDetail(dept, recordMap, dateStr);
                if (!leaveDetail.getRows().isEmpty()) {
                    String safeDept = safeLabel(dept);
                    Path leaveProofDir = dir.resolve(safeDept + "_请假明细_证明照片");
                    byte[] leaveBytes = excelExportHelper.exportLeaveDetail(
                            leaveDetail, leaveProofDir);
                    Files.write(dir.resolve(safeDept + "_请假明细.xlsx"), leaveBytes);
                    leaveWritten++;
                    log.info("学院级报表: 已生成 [{}] 请假明细", dept);
                } else {
                    log.info("学院级报表: [{}] 请假明细 无匹配学生，跳过", dept);
                }
            } catch (Exception e) {
                log.warn("学院级报表导出失败: department={}, 错误: {}", dept, e.getMessage(), e);
            }
        }

        log.info("学院级报表统计: 共 {} 个部门, 汇总已生成 {} 份, 请假明细已生成 {} 份",
                departments.size(), summaryWritten, leaveWritten);
    }

    // ========================================================================
    // 返回类型
    // ========================================================================

    public record ExportResult(
            Path outputDir,
            List<String> failedBuildings,
            List<String> failedClasses) {

        public boolean hasFailures() {
            return !failedBuildings.isEmpty() || !failedClasses.isEmpty();
        }

        public String failureSummary() {
            if (!hasFailures()) return "";
            StringBuilder sb = new StringBuilder("以下项目导出失败：\n");
            for (String b : failedBuildings) sb.append("  楼栋: ").append(b).append("\n");
            for (String c : failedClasses) sb.append("  班级: ").append(c).append("\n");
            return sb.toString();
        }
    }
}