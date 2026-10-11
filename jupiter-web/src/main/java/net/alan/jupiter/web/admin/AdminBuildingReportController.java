package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.report.ReportQueryService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.infrastructure.excel.ExcelExportHelper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "管理端-楼栋报表", description = "管理端楼栋查宿汇总接口")
@RestController
@RequestMapping("/admin-api/reports/building")
@RequiredArgsConstructor
public class AdminBuildingReportController {

    private final ReportQueryService reportQueryService;
    private final ExcelExportHelper excelExportHelper;

    @Operation(summary = "生成楼栋查宿汇总")
    @GetMapping("/{taskId}/{buildingId}")
    public ApiResult<ReportQueryService.BuildingReportData> getBuildingReport(
            @PathVariable String taskId,
            @PathVariable String buildingId) {
        return ApiResult.success(
                (ReportQueryService.BuildingReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.BUILDING, taskId, buildingId));
    }

    @Operation(summary = "导出楼栋查宿汇总 Excel")
    @GetMapping("/{taskId}/{buildingId}/export")
    public ResponseEntity<byte[]> exportBuildingReport(
            @PathVariable String taskId,
            @PathVariable String buildingId,
            @RequestParam(defaultValue = "") String buildingName) {
        ReportQueryService.BuildingReportData reportData =
                (ReportQueryService.BuildingReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.BUILDING, taskId, buildingId);

        var detailRows = reportData.getRoomDetails().stream()
                .map(rd -> ExcelExportHelper.BuildingDetailExportRow.builder()
                        .floor(rd.getFloor())
                        .roomNumber(rd.getRoomNumber())
                        .studentNo(rd.getStudentNo())
                        .studentName(rd.getStudentName())
                        .className(rd.getClassName())
                        .checkStatus(rd.getCheckStatus())
                        .proofFilePath(rd.getProofFile())
                        .remark(rd.getRemark())
                        .studentRemark(rd.getStudentRemark())
                        .build())
                .toList();

        List<ExcelExportHelper.BuildingFloorSummary> summaries = reportData.getFloorSummaries()
                .stream()
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

        byte[] data = excelExportHelper.exportBuildingReport(summaries, detailRows, buildingName, null);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(buildingName + "_查宿汇总.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}