package net.alan.jupiter.application.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.domain.model.ClassInfo;
import net.alan.jupiter.domain.model.DormBuilding;
import net.alan.jupiter.domain.repository.ClassInfoRepository;
import net.alan.jupiter.domain.repository.DormBuildingRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.infrastructure.excel.ExcelExportHelper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    public Path exportBundle(String taskId, String outputDir) throws IOException {
        boolean allTasks = (taskId == null || taskId.isBlank());
        String dateStr = allTasks ? buildDateStrFromAllTasks() : LocalDate.now().toString();
        Path dir = Path.of(outputDir, "晚查寝_" + dateStr);

        List<DormBuilding> buildings = buildingRepository.findByIsActiveTrue();

        Path buildingDir = dir.resolve("楼栋报表");
        Path classDir = dir.resolve("班级报表");
        Path proofBaseDir = dir.resolve("请假截图");

        List<ExcelExportHelper.BuildingSummaryData> summaryDataList = new ArrayList<>();
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>\n<html><head><meta charset=\"UTF-8\">")
                .append("<title>查寝汇总 - ").append(dateStr).append("</title>")
                .append("<style>body{font-family:sans-serif;margin:20px;}")
                .append("h2{color:#333;margin-top:24px;}h3{margin-left:24px;color:#555;margin-top:16px;}")
                .append("p{margin:4px 0;padding-left:12px;}</style></head>\n<body>\n")
                .append("<h1>查寝汇总报告 - ").append(dateStr).append("</h1>\n")
                .append("<p>生成时间: ")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("</p>\n");

        writeBuildingReports(buildingDir, taskId, buildings, allTasks, proofBaseDir.resolve("楼栋"), dateStr,
                summaryDataList, htmlContent);
        writeClassReports(classDir, taskId, buildings, allTasks, proofBaseDir);

        htmlContent.append("</body>\n</html>");

        if (!summaryDataList.isEmpty()) {
            byte[] summaryExcel = excelExportHelper.exportBuildingSummary(summaryDataList);
            Files.write(dir.resolve("楼栋汇总表.xlsx"), summaryExcel);
            Files.write(dir.resolve("查寝汇总报告.html"),
                    htmlContent.toString().getBytes(StandardCharsets.UTF_8));
        }

        log.info("导出报表文件夹: {} (楼栋:{}个, 班级:{}个)",
                dir, buildings.size(), summaryDataList.size());
        return dir;
    }

    private void writeBuildingReports(Path buildingDir, String taskId,
                                               List<DormBuilding> buildings, boolean allTasks,
                                               Path proofDir, String dateStr,
                                               List<ExcelExportHelper.BuildingSummaryData> summaryDataList,
                                               StringBuilder htmlContent) throws IOException {
        for (DormBuilding building : buildings) {
            try {
                ReportQueryService.BuildingReportData reportData =
                        (ReportQueryService.BuildingReportData) (allTasks
                                ? reportQueryService.generateAggregatedReport(
                                        ReportQueryService.ReportType.BUILDING, building.getId())
                                : reportQueryService.generateReport(
                                        ReportQueryService.ReportType.BUILDING, taskId, building.getId()));

                String safeName = building.getName().replaceAll("[\\\\/:*?\"<>|]", "_");

                exportBuildingFloorReports(buildingDir, building.getName(), safeName, reportData, proofDir);

                collectBuildingSummary(dateStr, building.getName(), reportData, summaryDataList, htmlContent);
                writeBuildingSummaryPage(buildingDir, dateStr, building.getName(), reportData, safeName);
            } catch (Exception e) {
                log.warn("导出楼栋报表失败: {}, 错误: {}", building.getName(), e.getMessage());
            }
        }
    }

    private void exportBuildingFloorReports(Path buildingDir, String buildingName,
                                              String safeName,
                                              ReportQueryService.BuildingReportData reportData,
                                              Path proofDir) throws IOException {
        Map<String, List<ReportQueryService.BuildingReportData.RoomDetail>> byFloor =
                reportData.getRoomDetails().stream()
                        .collect(java.util.stream.Collectors.groupingBy(
                                rd -> rd.getFloor() != null ? rd.getFloor() : "未知",
                                java.util.LinkedHashMap::new,
                                java.util.stream.Collectors.toList()));

        Path buildingProofDir = proofDir.resolve(safeName);
        Files.createDirectories(buildingProofDir);

        for (var entry : byFloor.entrySet()) {
            String floor = entry.getKey();
            String floorLabel = floor + "层";
            Path floorOutputDir = buildingDir.resolve(safeName).resolve(floorLabel);
            Files.createDirectories(floorOutputDir);

            Path floorProofDir = buildingProofDir.resolve(floorLabel);
            Files.createDirectories(floorProofDir);

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
                    detailRows, buildingName, floorLabel, floorProofDir);
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
                summaries, allDetailRows, buildingName, buildingProofDir);
        Files.write(buildingDir.resolve(safeName).resolve("汇总.xlsx"), oldFormatData);
    }

    private void collectBuildingSummary(String dateStr, String buildingName,
                                         ReportQueryService.BuildingReportData reportData,
                                         List<ExcelExportHelper.BuildingSummaryData> summaryDataList,
                                         StringBuilder htmlContent) {
        List<ExcelExportHelper.BuildingFloorSummary> floorSummaries = new ArrayList<>();
        int totalShould = 0, totalActual = 0, totalPersonalLeave = 0, totalSickLeave = 0;
        int totalLateReturn = 0, totalMilitary = 0, totalIntern = 0, totalAbsent = 0, totalUnchecked = 0;

        java.util.Map<String, java.util.List<String>> floorLeaveNames = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<String>> floorAbsentNames = new java.util.LinkedHashMap<>();

        for (ReportQueryService.BuildingReportData.RoomDetail rd : reportData.getRoomDetails()) {
            String floor = rd.getFloor() != null ? rd.getFloor() : "未知";
            String checkStatus = rd.getCheckStatus();
            String className = rd.getClassName() != null ? rd.getClassName() : "";
            String label = rd.getStudentName() + "(" + className + "|" + rd.getRoomNumber() + ")";

            if ("事假".equals(checkStatus) || "病假".equals(checkStatus)
                    || "长期病假".equals(checkStatus)) {
                floorLeaveNames.computeIfAbsent(floor, k -> new java.util.ArrayList<>()).add(label);
            } else if ("旷宿".equals(checkStatus)) {
                floorAbsentNames.computeIfAbsent(floor, k -> new java.util.ArrayList<>()).add(label);
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
        htmlContent.append("<h2>").append(dateStr).append(" ").append(buildingName).append("查宿情况</h2>\n");
        htmlContent.append("<p>应到").append(totalShould)
                .append("，实到").append(totalActual)
                .append("，请假").append(totalLeave)
                .append("，旷宿").append(totalAbsent).append("</p>\n");
        for (ExcelExportHelper.BuildingFloorSummary fs : floorSummaries) {
            int floorLeave = fs.getPersonalLeave() + fs.getSickLeave();
            htmlContent.append("<h3>").append(fs.getFloor()).append("层：</h3>\n");
            htmlContent.append("<p>应到").append(fs.getShould())
                    .append("，实到").append(fs.getActual())
                    .append("，请假").append(floorLeave)
                    .append("，旷宿").append(fs.getAbsent()).append("</p>\n");

            java.util.List<String> leaves = floorLeaveNames.get(fs.getFloor());
            if (leaves != null && !leaves.isEmpty()) {
                htmlContent.append("<p>请假人员：");
                htmlContent.append(String.join("，", leaves));
                htmlContent.append("</p>\n");
            }
            java.util.List<String> absents = floorAbsentNames.get(fs.getFloor());
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
                .append("<title>").append(buildingName).append("查宿情况 - ").append(dateStr).append("</title>")
                .append("<style>body{font-family:sans-serif;margin:20px;}")
                .append("h1{color:#333;}p{margin:8px 0;font-size:16px;}</style></head>\n<body>\n")
                .append("<h1>").append(dateStr).append(" ").append(buildingName).append("查宿情况：</h1>\n")
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

    private void writeClassReports(Path classDir, String taskId,
                                            List<DormBuilding> buildings, boolean allTasks,
                                            Path proofDir) throws IOException {
        Files.createDirectories(classDir);

        Set<String> buildingIds = buildings.stream().map(DormBuilding::getId).collect(Collectors.toSet());
        Set<String> classIds = studentRepository.findAll().stream()
                .filter(s -> s.getBuilding() != null && buildingIds.contains(s.getBuilding().getId()))
                .filter(s -> s.getClassInfo() != null)
                .map(s -> s.getClassInfo().getId())
                .collect(Collectors.toSet());

        for (String classId : classIds) {
            try {
                ClassInfo classInfo = classInfoRepository.findById(classId).orElse(null);
                if (classInfo == null) continue;

                ReportQueryService.ClassReportData reportData =
                        (ReportQueryService.ClassReportData) (allTasks
                                ? reportQueryService.generateAggregatedReport(
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

                byte[] data = excelExportHelper.exportClassReport(
                        exportRows, classInfo.getName(), "", proofDir);
                String safeName = classInfo.getName().replaceAll("[\\\\/:*?\"<>|]", "_");
                Files.write(classDir.resolve(safeName + ".xlsx"), data);
            } catch (Exception e) {
                log.warn("导出班级报表失败: classId={}, 错误: {}", classId, e.getMessage());
            }
        }
    }

    private String buildDateStrFromAllTasks() {
        return LocalDate.now().toString();
    }
}