package net.alan.jupiter.infrastructure.excel;

import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.CheckRecord;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Slf4j
@Component
public class ExcelExportHelper {

    @Value("${jupiter.storage.upload-dir:uploads}")
    private String uploadDir;

    private static final String[] CLASS_REPORT_HEADERS = {
            "学号", "姓名", "性别", "宿舍楼", "楼层", "宿舍号", "状态", "请假类型", "证明", "查宿备注", "档案备注"
    };

    private static final String[] BUILDING_DETAIL_HEADERS = {
            "楼层", "宿舍号", "学号", "姓名", "班级", "状态", "证明", "查宿备注", "档案备注"
    };

    private static final String[] BUILDING_FLOOR_REPORT_HEADERS = {
            "宿舍号", "班级", "姓名", "状态", "请假类型", "证明", "查宿备注", "档案备注"
    };

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ClassReportExportRow {
        private String studentNo;
        private String studentName;
        private String gender;
        private String buildingName;
        private Integer floorNumber;
        private String roomNumber;
        private String checkStatus;
        private String leaveType;
        private String proofFilePath;
        private String remark;
        private String studentRemark;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BuildingDetailExportRow {
        private String floor;
        private String roomNumber;
        private String studentNo;
        private String studentName;
        private String className;
        private String checkStatus;
        private String leaveType;
        private String proofFilePath;
        private String remark;
        private String studentRemark;
    }

    /**
     * 导出班级查宿汇总 Excel
     * @param proofExportRoot 证明图片导出根目录（请假截图文件夹），为 null 时仅显示文字
     */
    public byte[] exportClassReport(List<ClassReportExportRow> rows, String className, String buildingName,
                                    Path proofExportRoot) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(className != null ? className : "班级查宿汇总");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < CLASS_REPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(CLASS_REPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int proofCol = 8;
            int rowIdx = 1;
            for (ClassReportExportRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getStudentNo() != null ? r.getStudentNo() : "");
                row.createCell(1).setCellValue(r.getStudentName() != null ? r.getStudentName() : "");
                row.createCell(2).setCellValue(r.getGender() != null ? r.getGender() : "");
                row.createCell(3).setCellValue(r.getBuildingName() != null ? r.getBuildingName() : "");
                row.createCell(4).setCellValue(r.getFloorNumber() != null ? String.valueOf(r.getFloorNumber()) : "");
                row.createCell(5).setCellValue(r.getRoomNumber() != null ? r.getRoomNumber() : "");
                row.createCell(6).setCellValue(r.getCheckStatus() != null ? r.getCheckStatus() : "");
                row.createCell(7).setCellValue(r.getLeaveType() != null ? r.getLeaveType() : "");
                row.createCell(9).setCellValue(r.getRemark() != null ? r.getRemark() : "");
                row.createCell(10).setCellValue(r.getStudentRemark() != null ? r.getStudentRemark() : "");

                addProofLink(workbook, sheet, rowIdx - 1, proofCol,
                        r.getProofFilePath(), r.getRoomNumber(), proofExportRoot,
                        "../请假截图/");
            }

            autoSizeColumns(sheet, CLASS_REPORT_HEADERS.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出班级查宿汇总失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出班级查宿汇总失败，请稍后再试");
        }
    }

    /**
     * 导出楼栋查宿汇总 Excel（楼层汇总 Sheet + 宿舍明细 Sheet）
     * @param proofExportRoot 证明图片导出根目录（请假截图文件夹），为 null 时仅显示文字
     */
    public byte[] exportBuildingReport(
            List<BuildingFloorSummary> floorSummaries,
            List<BuildingDetailExportRow> detailRows,
            String buildingName,
            Path proofExportRoot) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet summarySheet = workbook.createSheet("楼层汇总");
            Row summaryHeader = summarySheet.createRow(0);
            String[] summaryHeaders = {"楼层", "应到", "实到", "事假", "病假", "请假总数",
                    "晚归", "征兵", "实习", "旷宿", "未查", "旷宿人员"};
            for (int i = 0; i < summaryHeaders.length; i++) {
                Cell cell = summaryHeader.createCell(i);
                cell.setCellValue(summaryHeaders[i]);
                cell.setCellStyle(headerStyle);
            }
            int rowIdx = 1;
            for (BuildingFloorSummary fs : floorSummaries) {
                Row row = summarySheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(fs.getFloor());
                row.createCell(1).setCellValue(fs.getShould());
                row.createCell(2).setCellValue(fs.getActual());
                row.createCell(3).setCellValue(fs.getPersonalLeave());
                row.createCell(4).setCellValue(fs.getSickLeave());
                row.createCell(5).setCellValue(fs.getPersonalLeave() + fs.getSickLeave());
                row.createCell(6).setCellValue(fs.getLateReturn());
                row.createCell(7).setCellValue(fs.getMilitary());
                row.createCell(8).setCellValue(fs.getIntern());
                row.createCell(9).setCellValue(fs.getAbsent());
                row.createCell(10).setCellValue(fs.getUnchecked());
                row.createCell(11).setCellValue(fs.getAbsentNames() != null ? fs.getAbsentNames() : "");
            }
            autoSizeColumns(summarySheet, summaryHeaders.length);

            Sheet detailSheet = workbook.createSheet("宿舍明细");
            Row detailHeader = detailSheet.createRow(0);
            for (int i = 0; i < BUILDING_DETAIL_HEADERS.length; i++) {
                Cell cell = detailHeader.createCell(i);
                cell.setCellValue(BUILDING_DETAIL_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }
            int proofCol = 6;
            int detailIdx = 1;
            String safeName = buildingName.replaceAll("[\\\\/:*?\"<>|]", "_");
            for (BuildingDetailExportRow row : detailRows) {
                Row excelRow = detailSheet.createRow(detailIdx++);
                excelRow.createCell(0).setCellValue(row.getFloor() != null ? row.getFloor() : "");
                excelRow.createCell(1).setCellValue(row.getRoomNumber() != null ? row.getRoomNumber() : "");
                excelRow.createCell(2).setCellValue(row.getStudentNo() != null ? row.getStudentNo() : "");
                excelRow.createCell(3).setCellValue(row.getStudentName() != null ? row.getStudentName() : "");
                excelRow.createCell(4).setCellValue(row.getClassName() != null ? row.getClassName() : "");
                excelRow.createCell(5).setCellValue(row.getCheckStatus() != null ? row.getCheckStatus() : "");
                excelRow.createCell(7).setCellValue(row.getRemark() != null ? row.getRemark() : "");
                excelRow.createCell(8).setCellValue(row.getStudentRemark() != null ? row.getStudentRemark() : "");

                addProofLink(workbook, detailSheet, detailIdx - 1, proofCol,
                        row.getProofFilePath(), row.getRoomNumber(), proofExportRoot,
                        "../../请假截图/楼栋/" + safeName + "/");
            }
            autoSizeColumns(detailSheet, BUILDING_DETAIL_HEADERS.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出楼栋查宿汇总失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出楼栋查宿汇总失败，请稍后再试");
        }
    }

    /**
     * 导出单个宿舍楼层的查宿明细 Excel（类似班级报表格式）
     * @param rows 该楼层所有学生明细行
     * @param buildingName 楼栋名称
     * @param floorLabel 楼层标签（如 "1层"）
     * @param proofExportRoot 证明图片导出根目录
     */
    public byte[] exportBuildingFloorReport(List<BuildingDetailExportRow> rows, String buildingName,
                                             String floorLabel, Path proofExportRoot) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(floorLabel);
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < BUILDING_FLOOR_REPORT_HEADERS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(BUILDING_FLOOR_REPORT_HEADERS[i]);
                cell.setCellStyle(headerStyle);
            }

            int proofCol = 5;
            int rowIdx = 1;
            String safeName = buildingName.replaceAll("[\\\\/:*?\"<>|]", "_");
            for (BuildingDetailExportRow r : rows) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(r.getRoomNumber() != null ? r.getRoomNumber() : "");
                row.createCell(1).setCellValue(r.getClassName() != null ? r.getClassName() : "");
                row.createCell(2).setCellValue(r.getStudentName() != null ? r.getStudentName() : "");
                row.createCell(3).setCellValue(r.getCheckStatus() != null ? r.getCheckStatus() : "");
                row.createCell(4).setCellValue(r.getLeaveType() != null ? r.getLeaveType() : "");
                row.createCell(6).setCellValue(r.getRemark() != null ? r.getRemark() : "");
                row.createCell(7).setCellValue(r.getStudentRemark() != null ? r.getStudentRemark() : "");

                addProofLink(workbook, sheet, rowIdx - 1, proofCol,
                        r.getProofFilePath(), r.getRoomNumber(), proofExportRoot,
                        "../../../请假截图/楼栋/" + safeName + "/" + floorLabel + "/");
            }

            autoSizeColumns(sheet, BUILDING_FLOOR_REPORT_HEADERS.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出楼层报表失败: {}-{}", buildingName, floorLabel, e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出楼层报表失败，请稍后再试");
        }
    }

    /**
     * 导出全楼栋汇总 Excel：将所有楼栋的楼层数据聚合在一个表格中
     */
    public byte[] exportBuildingSummary(List<BuildingSummaryData> buildingSummaryDataList) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle subtotalStyle = createSubtotalStyle(workbook);

            Sheet sheet = workbook.createSheet("楼栋汇总");
            String[] summaryHeaders = {"楼栋", "楼层", "应到", "实到", "事假", "病假", "请假总数",
                    "晚归", "征兵", "实习", "旷宿", "未查", "旷宿人员"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < summaryHeaders.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(summaryHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (BuildingSummaryData bsd : buildingSummaryDataList) {
                for (BuildingFloorSummary fs : bsd.getFloorSummaries()) {
                    Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(bsd.getBuildingName());
                    row.createCell(1).setCellValue(fs.getFloor());
                    row.createCell(2).setCellValue(fs.getShould());
                    row.createCell(3).setCellValue(fs.getActual());
                    row.createCell(4).setCellValue(fs.getPersonalLeave());
                    row.createCell(5).setCellValue(fs.getSickLeave());
                    row.createCell(6).setCellValue(fs.getPersonalLeave() + fs.getSickLeave());
                    row.createCell(7).setCellValue(fs.getLateReturn());
                    row.createCell(8).setCellValue(fs.getMilitary());
                    row.createCell(9).setCellValue(fs.getIntern());
                    row.createCell(10).setCellValue(fs.getAbsent());
                    row.createCell(11).setCellValue(fs.getUnchecked());
                    row.createCell(12).setCellValue(
                            fs.getAbsentNames() != null ? fs.getAbsentNames() : "");
                }

                Row subtotalRow = sheet.createRow(rowIdx++);
                subtotalRow.createCell(0).setCellValue("");
                subtotalRow.createCell(1).setCellValue("小计");
                subtotalRow.createCell(2).setCellValue(bsd.getTotalShould());
                subtotalRow.createCell(3).setCellValue(bsd.getTotalActual());
                subtotalRow.createCell(4).setCellValue(bsd.getTotalPersonalLeave());
                subtotalRow.createCell(5).setCellValue(bsd.getTotalSickLeave());
                subtotalRow.createCell(6).setCellValue(
                        bsd.getTotalPersonalLeave() + bsd.getTotalSickLeave());
                subtotalRow.createCell(7).setCellValue(bsd.getTotalLateReturn());
                subtotalRow.createCell(8).setCellValue(bsd.getTotalMilitary());
                subtotalRow.createCell(9).setCellValue(bsd.getTotalIntern());
                subtotalRow.createCell(10).setCellValue(bsd.getTotalAbsent());
                subtotalRow.createCell(11).setCellValue(bsd.getTotalUnchecked());
                subtotalRow.createCell(12).setCellValue("");
                for (int i = 0; i < summaryHeaders.length; i++) {
                    subtotalRow.getCell(i).setCellStyle(subtotalStyle);
                }
            }

            autoSizeColumns(sheet, summaryHeaders.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出楼栋汇总Excel失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出楼栋汇总失败，请稍后再试");
        }
    }

    /**
     * 导出原始查寝记录 Excel（管理端通用导出）
     * @param proofExportRoot 证明图片导出根目录（请假截图文件夹），为 null 时仅显示文字
     */
    public byte[] exportCheckRecords(List<CheckRecord> records, String title, Path proofExportRoot) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(title != null ? title : "查寝记录");
            CellStyle headerStyle = createHeaderStyle(workbook);
            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "房间号", "查寝状态", "状态来源", "证明", "备注", "提交人", "提交时间"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int proofCol = 5;
            int rowIdx = 1;
            for (CheckRecord record : records) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(record.getStudentNo() != null ? record.getStudentNo() : "");
                row.createCell(1).setCellValue(record.getStudentName() != null ? record.getStudentName() : "");
                row.createCell(2).setCellValue(record.getRoomNumber() != null ? record.getRoomNumber() : "");
                row.createCell(3).setCellValue(record.getCheckStatus() != null
                        ? record.getCheckStatus().getLabel() : "");
                row.createCell(4).setCellValue(record.getStatusSource() != null ? record.getStatusSource() : "");
                row.createCell(6).setCellValue(record.getRemark() != null ? record.getRemark() : "");
                row.createCell(7).setCellValue(record.getSubmittedBy() != null ? record.getSubmittedBy() : "");
                row.createCell(8).setCellValue(record.getSubmittedAt() != null
                        ? record.getSubmittedAt().toString() : "");

                addProofLink(workbook, sheet, rowIdx - 1, proofCol,
                        record.getProofFile(), record.getRoomNumber(), proofExportRoot,
                        "../" + (proofExportRoot != null ? proofExportRoot.getFileName().toString() + "/" : ""));
            }

            autoSizeColumns(sheet, headers.length);
            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出Excel失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出Excel失败，请稍后再试");
        }
    }

    private String leaveTypeLabel(CheckRecord record) {
        if (record.getCheckStatus() == null) return "";
        return switch (record.getCheckStatus()) {
            case PERSONAL_LEAVE -> "事假";
            case SICK_LEAVE -> "病假";
            case LONG_SICK_LEAVE -> "长期病假";
            default -> "";
        };
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSubtotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 512, 15 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    private byte[] toByteArray(Workbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        return baos.toByteArray();
    }

    private void addProofLink(Workbook workbook, Sheet sheet, int row, int col,
                               String proofFilePath, String roomNumber,
                               Path proofExportRoot, String relativePathPrefix) {
        Row excelRow = sheet.getRow(row);
        if (excelRow == null) return;
        Cell cell = excelRow.createCell(col);

        if (proofFilePath == null || proofFilePath.isEmpty()) return;

        Path sourceFile = Path.of(uploadDir, proofFilePath);
        if (!Files.exists(sourceFile)) {
            log.warn("证明文件不存在: {}", sourceFile);
            cell.setCellValue("文件缺失");
            return;
        }

        if (proofExportRoot == null) {
            cell.setCellValue("有证明");
            return;
        }

        try {
            String safeRoom = roomNumber != null && !roomNumber.isBlank()
                    ? roomNumber.replaceAll("[\\\\/:*?\"<>|]", "_")
                    : "unknown";
            Path targetDir = proofExportRoot.resolve(safeRoom);
            Files.createDirectories(targetDir);

            String fileName = sourceFile.getFileName().toString();
            Path targetFile = targetDir.resolve(fileName);
            Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);

            CreationHelper helper = workbook.getCreationHelper();
            Hyperlink link = helper.createHyperlink(HyperlinkType.FILE);
            String relativePath = relativePathPrefix + safeRoom + "/" + fileName;
            link.setAddress(relativePath);

            cell.setCellValue("查看证明");
            cell.setHyperlink(link);
        } catch (Exception e) {
            log.warn("添加证明链接失败: row={}, path={}", row, proofFilePath, e);
            cell.setCellValue("复制失败");
        }
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BuildingFloorSummary {
        private String floor;
        private int should;
        private int actual;
        private int personalLeave;
        private int sickLeave;
        private int lateReturn;
        private int military;
        private int intern;
        private int absent;
        private int unchecked;
        private String absentNames;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class BuildingSummaryData {
        private String buildingName;
        private List<BuildingFloorSummary> floorSummaries;
        private int totalShould;
        private int totalActual;
        private int totalPersonalLeave;
        private int totalSickLeave;
        private int totalLateReturn;
        private int totalMilitary;
        private int totalIntern;
        private int totalAbsent;
        private int totalUnchecked;
    }
}