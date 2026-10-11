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

@Tag(name = "管理端-班级报表", description = "管理端班级查宿汇总接口")
@RestController
@RequestMapping("/admin-api/reports/class")
@RequiredArgsConstructor
public class AdminClassReportController {

    private final ReportQueryService reportQueryService;
    private final ExcelExportHelper excelExportHelper;

    @Operation(summary = "生成班级查宿汇总")
    @GetMapping("/{taskId}/{classId}")
    public ApiResult<ReportQueryService.ClassReportData> getClassReport(
            @PathVariable String taskId,
            @PathVariable String classId) {
        return ApiResult.success(
                (ReportQueryService.ClassReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.CLASS, taskId, classId));
    }

    @Operation(summary = "导出班级查宿汇总 Excel")
    @GetMapping("/{taskId}/{classId}/export")
    public ResponseEntity<byte[]> exportClassReport(
            @PathVariable String taskId,
            @PathVariable String classId,
            @RequestParam(defaultValue = "") String className,
            @RequestParam(defaultValue = "") String buildingName) {
        ReportQueryService.ClassReportData reportData =
                (ReportQueryService.ClassReportData) reportQueryService.generateReport(
                        ReportQueryService.ReportType.CLASS, taskId, classId);

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
                exportRows, className, buildingName, null);

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(className + "_查宿汇总.xlsx", StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}