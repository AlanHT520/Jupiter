package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.check.SubmitRoomCheckService;
import net.alan.jupiter.application.report.ExportReportService;
import net.alan.jupiter.application.report.ReportQueryService;
import net.alan.jupiter.application.report.StatisticsService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.domain.repository.TaskRepository;
import net.alan.jupiter.infrastructure.excel.CollegeReportExcelWriter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-报表", description = "管理端统计报表接口")
@RestController
@RequestMapping("/admin-api/reports")
@RequiredArgsConstructor
@Slf4j
public class AdminReportController {

    private final StatisticsService statisticsService;
    private final ExportReportService exportReportService;
    private final SubmitRoomCheckService submitRoomCheckService;
    private final TaskRepository taskRepository;
    private final ReportQueryService reportQueryService;
    private final CollegeReportExcelWriter collegeReportExcelWriter;

    @Operation(summary = "生成统计报表")
    @GetMapping("/statistics/{taskId}")
    public ApiResult<StatisticsService.ReportData> getStatistics(@PathVariable String taskId) {
        return ApiResult.success(statisticsService.generateReport(taskId));
    }

    @Operation(summary = "生成全量统计报表（所有已完成任务）")
    @GetMapping("/statistics")
    public ApiResult<StatisticsService.ReportData> getAllStatistics() {
        return ApiResult.success(statisticsService.generateAggregatedReport());
    }

    @Operation(summary = "生成学院汇总上报")
    @GetMapping("/college/{taskId}")
    public ApiResult<ReportQueryService.CollegeReportData> getCollegeReport(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "") String collegeName) {
        ReportQueryService.CollegeReportData reportData =
                (ReportQueryService.CollegeReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.COLLEGE, taskId, collegeName);
        return ApiResult.success(reportData);
    }

    @Operation(summary = "生成全量学院汇总上报（所有已完成任务）")
    @GetMapping("/college")
    public ApiResult<ReportQueryService.CollegeReportData> getAllCollegeReport(
            @RequestParam(defaultValue = "") String collegeName) {
        ReportQueryService.CollegeReportData reportData =
                (ReportQueryService.CollegeReportData) reportQueryService.generateAggregatedReport(
                        ReportQueryService.ReportType.COLLEGE, collegeName);
        return ApiResult.success(reportData);
    }

    @Operation(summary = "导出学院汇总上报 Excel")
    @GetMapping("/college/{taskId}/export")
    public ResponseEntity<byte[]> exportCollegeReport(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "") String collegeName) {
        ReportQueryService.CollegeReportData reportData =
                (ReportQueryService.CollegeReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.COLLEGE, taskId, collegeName);

        return buildCollegeExcelResponse(reportData, collegeName);
    }

    @Operation(summary = "导出全量学院汇总上报 Excel（所有已完成任务）")
    @GetMapping("/college/export")
    public ResponseEntity<byte[]> exportAllCollegeReport(
            @RequestParam(defaultValue = "") String collegeName) {
        ReportQueryService.CollegeReportData reportData =
                (ReportQueryService.CollegeReportData) reportQueryService.generateAggregatedReport(
                        ReportQueryService.ReportType.COLLEGE, collegeName);

        return buildCollegeExcelResponse(reportData, collegeName);
    }

    private ResponseEntity<byte[]> buildCollegeExcelResponse(
            ReportQueryService.CollegeReportData reportData, String collegeName) {
        CollegeReportExcelWriter.CollegeReportData writerData =
                convertToWriterData(reportData, collegeName);
        byte[] data = collegeReportExcelWriter.exportCollegeReport(writerData);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(collegeName + "_晚查寝汇总.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    private CollegeReportExcelWriter.CollegeReportData convertToWriterData(
            ReportQueryService.CollegeReportData reportData, String collegeName) {
        CollegeReportExcelWriter.CollegeReportData data =
                new CollegeReportExcelWriter.CollegeReportData();
        data.setDate(reportData.getDate());
        data.setCollegeName(collegeName);

        List<CollegeReportExcelWriter.CollegeReportData.BuildingRow> buildingRows =
                reportData.getBuildingSummaries().stream()
                        .map(bs -> {
                            var br = new CollegeReportExcelWriter.CollegeReportData.BuildingRow();
                            br.setBuildingName(bs.getBuildingName());
                            br.setShould(bs.getShould());
                            br.setActual(bs.getActual());
                            br.setPersonalLeave(bs.getPersonalLeave());
                            br.setSickLeave(bs.getSickLeave());
                            br.setLateReturn(bs.getLateReturn());
                            br.setMilitary(bs.getMilitary());
                            br.setIntern(bs.getIntern());
                            br.setDayStudent(bs.getDayStudent());
                            br.setLongSickLeave(bs.getLongSickLeave());
                            br.setAbsent(bs.getAbsent());
                            br.setUnchecked(bs.getUnchecked());
                            br.setAbsentNames(bs.getAbsentNames());
                            return br;
                        })
                        .toList();
        data.setBuildings(buildingRows);

        List<CollegeReportExcelWriter.CollegeReportData.FloorDetailRow> floorRows =
                reportData.getFloorDetails().stream()
                        .map(fd -> {
                            var fr = new CollegeReportExcelWriter.CollegeReportData.FloorDetailRow();
                            fr.setBuildingName(fd.getBuildingName());
                            fr.setFloor(fd.getFloor());
                            fr.setShould(fd.getShould());
                            fr.setActual(fd.getActual());
                            fr.setPersonalLeave(fd.getPersonalLeave());
                            fr.setSickLeave(fd.getSickLeave());
                            fr.setLateReturn(fd.getLateReturn());
                            fr.setMilitary(fd.getMilitary());
                            fr.setIntern(fd.getIntern());
                            fr.setAbsent(fd.getAbsent());
                            fr.setUnchecked(fd.getUnchecked());
                            fr.setAbsentNames(fd.getAbsentNames());
                            return fr;
                        })
                        .toList();
        data.setFloorDetails(floorRows);

        if (reportData.getNewStudent() != null) {
            var ns = reportData.getNewStudent();
            var newStudentRow = new CollegeReportExcelWriter.CollegeReportData.NewStudentRow();
            newStudentRow.setShould(ns.getShould());
            newStudentRow.setActual(ns.getActual());
            newStudentRow.setMale(ns.getMale());
            newStudentRow.setFemale(ns.getFemale());
            newStudentRow.setMilitaryMale(ns.getMilitaryMale());
            newStudentRow.setLeave(ns.getLeave());
            newStudentRow.setIntern(ns.getIntern());
            newStudentRow.setDayStudent(ns.getDayStudent());
            newStudentRow.setLongSickLeave(ns.getLongSickLeave());
            newStudentRow.setAbsent(ns.getAbsent());
            newStudentRow.setLateReturn(ns.getLateReturn());
            data.setNewStudent(newStudentRow);
        }

        return data;
    }

    @Operation(summary = "导出HTML报表")
    @GetMapping("/export/html/{taskId}")
    public ResponseEntity<byte[]> exportHtml(
            @PathVariable String taskId,
            @RequestParam(defaultValue = "dorm_report.ftl") String template) {
        validateTemplate(template);
        try {
            String html = exportReportService.exportToHtml(taskId, template);
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ContentDisposition htmlCd = ContentDisposition.attachment()
                    .filename("report_" + taskId + ".html", java.nio.charset.StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, htmlCd.toString())
                    .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                    .body(bytes);
        } catch (Exception e) {
            log.error("导出HTML报表失败", e);
            throw new RuntimeException("导出报表失败，请稍后再试");
        }
    }

    @Operation(summary = "导出全量HTML报表（所有已完成任务）")
    @GetMapping("/export/html")
    public ResponseEntity<byte[]> exportAllHtml(
            @RequestParam(defaultValue = "dorm_report.ftl") String template) {
        validateTemplate(template);
        try {
            String html = exportReportService.exportToHtml(null, template);
            byte[] bytes = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            ContentDisposition htmlCd = ContentDisposition.attachment()
                    .filename("report_all.html", java.nio.charset.StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, htmlCd.toString())
                    .contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                    .body(bytes);
        } catch (Exception e) {
            log.error("导出全量HTML报表失败", e);
            throw new RuntimeException("导出报表失败，请稍后再试");
        }
    }

    @Operation(summary = "导出Excel查寝记录")
    @GetMapping("/export/excel/{taskId}")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String taskId) {
        List<CheckRecord> records = submitRoomCheckService.getTaskCheckRecords(taskId);
        byte[] data = exportReportService.exportCheckRecordsToExcel(records, "查寝记录_" + taskId);
        ContentDisposition cd = ContentDisposition.attachment()
                .filename("check_records_" + taskId + ".xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    @Operation(summary = "导出全量Excel查寝记录（所有已完成任务）")
    @GetMapping("/export/excel")
    public ResponseEntity<byte[]> exportAllExcel() {
        List<Task> completedTasks = taskRepository.findCompletedTasks(
                net.alan.jupiter.domain.enums.TaskStatus.COMPLETED);
        List<CheckRecord> allRecords = new java.util.ArrayList<>();
        for (Task task : completedTasks) {
            allRecords.addAll(submitRoomCheckService.getTaskCheckRecords(task.getId()));
        }
        byte[] data = exportReportService.exportCheckRecordsToExcel(allRecords, "全部查寝记录");
        ContentDisposition cd = ContentDisposition.attachment()
                .filename("check_records_all.xlsx", java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, cd.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }

    private void validateTemplate(String template) {
        if (template.contains("/") || template.contains("\\") || template.contains("..")) {
            throw new net.alan.jupiter.common.exception.BizException(
                    net.alan.jupiter.common.exception.ErrorCode.BAD_REQUEST, "无效的模板名称");
        }
    }
}