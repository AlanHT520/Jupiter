package net.alan.jupiter.infrastructure.excel;

import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.model.LeaveDetailData;
import net.alan.jupiter.domain.model.NightCheckSummaryData;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
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

    @Value("${Chinan.storage.upload-dir:uploads}")
    private String uploadDir;

    /**
     * 安全解析证明文件路径：只允许 uploadDir 下的文件，防止库中脏数据造成目录穿越。
     */
    private java.util.Optional<Path> resolveProofSource(String proofFilePath) {
        if (proofFilePath == null || proofFilePath.isBlank()) {
            return java.util.Optional.empty();
        }
        try {
            Path root = Path.of(uploadDir).toAbsolutePath().normalize();
            Path source = root.resolve(proofFilePath).normalize();
            if (!source.startsWith(root)) {
                log.warn("证明文件路径非法，已跳过: {}", proofFilePath);
                return java.util.Optional.empty();
            }
            return Files.exists(source) ? java.util.Optional.of(source) : java.util.Optional.empty();
        } catch (Exception e) {
            log.warn("证明文件路径解析失败，已跳过: {}", proofFilePath, e);
            return java.util.Optional.empty();
        }
    }

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
                        r.getProofFilePath(),
                        r.getBuildingName(),
                        r.getFloorNumber() != null ? String.valueOf(r.getFloorNumber()) : null,
                        r.getRoomNumber(),
                        r.getStudentNo(),
                        r.getStudentName(),
                        r.getCheckStatus(),
                        proofExportRoot,
                        ProofPathResolver.prefixFromClassReport());
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
                        row.getProofFilePath(),
                        buildingName,
                        row.getFloor(),
                        row.getRoomNumber(),
                        row.getStudentNo(),
                        row.getStudentName(),
                        row.getCheckStatus(),
                        proofExportRoot,
                        ProofPathResolver.prefixFromBuildingSummary());
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
                        r.getProofFilePath(),
                        buildingName,
                        r.getFloor(),
                        r.getRoomNumber(),
                        r.getStudentNo(),
                        r.getStudentName(),
                        r.getCheckStatus(),
                        proofExportRoot,
                        ProofPathResolver.prefixFromBuildingFloor());
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
                        record.getProofFile(),
                        null, // buildingName not available
                        null,
                        record.getRoomNumber(),
                        record.getStudentNo(),
                        record.getStudentName(),
                        record.getCheckStatus() != null ? record.getCheckStatus().getLabel() : null,
                        proofExportRoot,
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
                               String proofFilePath,
                               String buildingName,
                               String floor,
                               String roomNumber,
                               String studentNo, String studentName,
                               String statusLabel,
                               Path proofRoot,
                               String relativePrefix) {
        Row excelRow = sheet.getRow(row);
        if (excelRow == null) return;
        Cell cell = excelRow.createCell(col);

        if (proofFilePath == null || proofFilePath.isBlank()) return;

        Path source = resolveProofSource(proofFilePath).orElse(null);
        if (source == null) {
            log.warn("证明文件不存在或路径非法: {}", proofFilePath);
            cell.setCellValue("✗ 文件缺失");
            return;
        }

        if (proofRoot == null) {
            cell.setCellValue("✓ 有证明");
            return;
        }

        try {
            String ext = proofFilePath.substring(proofFilePath.lastIndexOf('.'));
            String bldDir   = ProofPathResolver.buildingDir(buildingName);
            String flrDir   = ProofPathResolver.floorDir(floor);
            String rmDir    = ProofPathResolver.roomDir(roomNumber);
            String fileName = ProofPathResolver.buildFileName(
                    roomNumber, studentNo, studentName, statusLabel, ext);

            Path targetDir = proofRoot.resolve(bldDir)
                                       .resolve(flrDir)
                                       .resolve(rmDir);
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(fileName);
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);

            CreationHelper helper = workbook.getCreationHelper();
            Hyperlink link = helper.createHyperlink(HyperlinkType.FILE);
            link.setAddress(relativePrefix + bldDir + "/" + flrDir + "/"
                    + rmDir + "/" + fileName);

            cell.setCellValue("📷 查看");
            cell.setHyperlink(link);
        } catch (Exception e) {
            log.warn("复制证明失败: row={}, path={}", row, proofFilePath, e);
            cell.setCellValue("✗ 复制失败");
        }
    }

    // ========================================================================
    // 晚查寝数据汇总（学院 → 楼栋 → 楼层 三段式）
    // ========================================================================

    public byte[] exportNightCheckSummary(NightCheckSummaryData data) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            titleStyle.setFont(titleFont);
            titleStyle.setAlignment(HorizontalAlignment.CENTER);
            titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

            Sheet sheet = workbook.createSheet("晚查寝汇总");

            // Row 0: 标题（合并 A-P）
            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(data.getCollegeName() + data.getDate() + "晚查寝人数统计");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 15));

            // Row 1: 楼栋表头
            Row buildHeader = sheet.createRow(1);
            buildHeader.createCell(0).setCellValue("学院应到人数");
            buildHeader.createCell(1).setCellValue("楼栋");
            buildHeader.createCell(2).setCellValue("应到");
            buildHeader.createCell(3).setCellValue("实到");
            buildHeader.createCell(4).setCellValue("事假");
            buildHeader.createCell(5).setCellValue("病假");
            buildHeader.createCell(6).setCellValue("请假总数");
            buildHeader.createCell(7).setCellValue("请假汇总");
            buildHeader.createCell(8).setCellValue("征兵");
            buildHeader.createCell(9).setCellValue("走读");
            buildHeader.createCell(10).setCellValue("旷宿");
            buildHeader.createCell(11).setCellValue("旷宿明细");
            buildHeader.createCell(12).setCellValue("长期病假");
            buildHeader.createCell(13).setCellValue("备注");
            for (int i = 0; i < 14; i++) {
                buildHeader.getCell(i).setCellStyle(headerStyle);
            }

            // Rows 2~N: 楼栋数据 + 空行
            int rowIdx = 2;
            for (NightCheckSummaryData.BuildingRow br : data.getBuildingRows()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(data.getTotalShould());
                row.createCell(1).setCellValue(br.getBuildingName());
                row.createCell(2).setCellValue(br.getShouldAmount());
                row.createCell(3).setCellValue(br.getActualAmount());
                row.createCell(4).setCellValue(br.getPersonalLeave());
                row.createCell(5).setCellValue(br.getSickLeave());
                int leaveTotal = br.getPersonalLeave() + br.getSickLeave();
                row.createCell(6).setCellValue(leaveTotal);
                row.createCell(7).setCellValue(leaveTotal > 0
                        ? "事假" + br.getPersonalLeave() + "人，病假" + br.getSickLeave() + "人"
                        : "");
                row.createCell(8).setCellValue(br.getMilitary());
                row.createCell(9).setCellValue(br.getDayStudent());
                row.createCell(10).setCellValue(br.getAbsent());
                row.createCell(11).setCellValue(br.getAbsentNames());
                row.createCell(12).setCellValue(br.getLongSickLeave());
                row.createCell(13).setCellValue(br.getRemark() != null ? br.getRemark() : "");
                // 学院应到仅在首行显示
                if (rowIdx > 3) {
                    row.getCell(0).setCellValue("");
                }
            }

            // 空两行
            rowIdx++;
            rowIdx++;

            // 楼层表头
            Row floorHeader = sheet.createRow(rowIdx++);
            String[] floorHeaders = {"楼栋", "楼层", "应到", "实到", "事假", "病假",
                    "请假总数", "请假汇总", "征兵", "旷宿"};
            for (int i = 0; i < floorHeaders.length; i++) {
                Cell cell = floorHeader.createCell(i);
                cell.setCellValue(floorHeaders[i]);
                cell.setCellStyle(headerStyle);
            }

            // 楼层数据
            for (NightCheckSummaryData.FloorRow fr : data.getFloorRows()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(fr.getBuildingName());
                row.createCell(1).setCellValue(fr.getFloorLabel());
                row.createCell(2).setCellValue(fr.getShouldAmount());
                row.createCell(3).setCellValue(fr.getActualAmount());
                row.createCell(4).setCellValue(fr.getPersonalLeave());
                row.createCell(5).setCellValue(fr.getSickLeave());
                int fLeaveTotal = fr.getPersonalLeave() + fr.getSickLeave();
                row.createCell(6).setCellValue(fLeaveTotal);
                row.createCell(7).setCellValue(fLeaveTotal > 0
                        ? "事假" + fr.getPersonalLeave() + "人，病假" + fr.getSickLeave() + "人"
                        : "");
                row.createCell(8).setCellValue(fr.getMilitary());
                row.createCell(9).setCellValue(fr.getAbsent());
            }

            // 列宽
            sheet.setColumnWidth(0, 12 * 256);
            sheet.setColumnWidth(1, 12 * 256);
            for (int i = 2; i <= 10; i++) {
                sheet.setColumnWidth(i, 8 * 256);
            }
            sheet.setColumnWidth(11, 30 * 256);
            sheet.setColumnWidth(12, 30 * 256);
            sheet.setColumnWidth(13, 30 * 256);

            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出晚查寝数据汇总失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出晚查寝数据汇总失败，请稍后再试");
        }
    }

    // ========================================================================
    // 请假明细（班级维度，含富文本走读蓝/长期病假红）
    // ========================================================================

    public byte[] exportLeaveDetail(LeaveDetailData data, Path proofExportRoot) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);

            Sheet sheet = workbook.createSheet("请假明细");

            // Row 0: 顶部概要文字
            LeaveDetailData.HeaderSummary h = data.getHeader();
            String titleText = data.getDate() + " " + data.getCollegeName()
                    + "在籍人数情况：应到:" + h.getTotalStudents() + "人（男" + h.getMale()
                    + "人，女" + h.getFemale() + "人）；实到:" + h.getActual()
                    + "人（男" + h.getActualMale() + "人，女" + h.getActualFemale()
                    + "人）";
            if (h.getLongSickLeave() > 0) {
                titleText += "；长期病假:" + h.getLongSickLeave() + "人";
            }
            if (h.getDayStudent() > 0) {
                titleText += "；走读:" + h.getDayStudent() + "人";
            }
            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(titleText);

            // Row 1~5 空行，Row 6 表头
            int headerRowIdx = 6;
            Row headerRow = sheet.createRow(headerRowIdx);
            String[] headers = {"序号", "班级", "班主任", "在籍人数", "实到人数", "请假",
                    "征兵", "转学", "走读", "旷宿", "休学", "退学", "特殊情况", "请假明细（📎=有证明）"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 预创建富文本字体（避免循环中重复创建）
            org.apache.poi.xssf.usermodel.XSSFFont blueFont =
                    (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
            blueFont.setColor(IndexedColors.BLUE.getIndex());
            org.apache.poi.xssf.usermodel.XSSFFont redFont =
                    (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());
            org.apache.poi.xssf.usermodel.XSSFFont greenFont =
                    (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
            greenFont.setColor(IndexedColors.GREEN.getIndex());
            org.apache.poi.xssf.usermodel.XSSFFont normalFont =
                    (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();

            CreationHelper helper = workbook.getCreationHelper();

            // 班级数据行
            int rowIdx = headerRowIdx + 1;
            for (int ci = 0; ci < data.getRows().size(); ci++) {
                LeaveDetailData.ClassLeaveRow r = data.getRows().get(ci);
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(ci + 1);
                row.createCell(1).setCellValue(r.getClassName());
                row.createCell(2).setCellValue(r.getHeadTeacher());
                row.createCell(3).setCellValue(r.getEnrolled());
                row.createCell(4).setCellValue(r.getActual());
                row.createCell(5).setCellValue(r.getLeave());
                row.createCell(6).setCellValue(r.getMilitary());
                row.createCell(7).setCellValue(r.getTransferOut());
                row.createCell(8).setCellValue(r.getDayStudent());
                row.createCell(9).setCellValue(r.getAbsent());
                row.createCell(10).setCellValue(r.getSuspend());
                row.createCell(11).setCellValue(r.getDropOut());
                row.createCell(12).setCellValue(r.getSpecialCase());

                // 请假明细列：富文本渲染
                buildLeaveDetailCell(row, 13, r.getDetails(),
                        helper, blueFont, redFont, greenFont, normalFont);
            }

            // 合计行
            Row totalRow = sheet.createRow(rowIdx);
            CellStyle subtotalStyle = createSubtotalStyle(workbook);
            int sumEnrolled = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getEnrolled).sum();
            int sumActual = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getActual).sum();
            int sumLeave = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getLeave).sum();
            int sumMilitary = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getMilitary).sum();
            int sumDayStudent = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getDayStudent).sum();
            int sumAbsent = data.getRows().stream()
                    .mapToInt(LeaveDetailData.ClassLeaveRow::getAbsent).sum();

            totalRow.createCell(0).setCellValue("合计");
            totalRow.createCell(1).setCellValue("");
            totalRow.createCell(2).setCellValue("");
            totalRow.createCell(3).setCellValue(sumEnrolled);
            totalRow.createCell(4).setCellValue(sumActual);
            totalRow.createCell(5).setCellValue(sumLeave);
            totalRow.createCell(6).setCellValue(sumMilitary);
            totalRow.createCell(7).setCellValue("");
            totalRow.createCell(8).setCellValue(sumDayStudent);
            totalRow.createCell(9).setCellValue(sumAbsent);
            totalRow.createCell(10).setCellValue("");
            totalRow.createCell(11).setCellValue("");
            totalRow.createCell(12).setCellValue("");
            totalRow.createCell(13).setCellValue("");

            for (int i = 0; i < headers.length; i++) {
                totalRow.getCell(i).setCellStyle(subtotalStyle);
            }

            // 列宽
            sheet.setColumnWidth(0, 6 * 256);
            sheet.setColumnWidth(1, 15 * 256);
            sheet.setColumnWidth(2, 10 * 256);
            for (int i = 3; i <= 12; i++) {
                sheet.setColumnWidth(i, 8 * 256);
            }
            sheet.setColumnWidth(13, 45 * 256);

            // 证明照片详情Sheet（仅当有证明文件且提供了导出目录时）
            if (proofExportRoot != null) {
                addLeaveDetailProofSheet(workbook, data, proofExportRoot);
            }

            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出请假明细失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出请假明细失败，请稍后再试");
        }
    }

    /**
     * 构建「请假明细」列的富文本单元格：走读男蓝 / 走读女绿 / 长期病假红 / 中文分号分隔。
     */
    private void buildLeaveDetailCell(
            Row row, int col,
            List<LeaveDetailData.LeaveDetailItem> details,
            CreationHelper helper,
            org.apache.poi.xssf.usermodel.XSSFFont blueFont,
            org.apache.poi.xssf.usermodel.XSSFFont redFont,
            org.apache.poi.xssf.usermodel.XSSFFont greenFont,
            org.apache.poi.xssf.usermodel.XSSFFont normalFont) {
        Cell cell = row.createCell(col);

        if (details == null || details.isEmpty()) {
            cell.setCellValue("");
            return;
        }

        XSSFRichTextString rich = (XSSFRichTextString) helper.createRichTextString(null);

        for (int i = 0; i < details.size(); i++) {
            LeaveDetailData.LeaveDetailItem item = details.get(i);
            String proofLabel = (item.getProofFile() != null && !item.getProofFile().isBlank())
                    ? "📎" : "";
            String text = item.getStudentName() + "（" + item.getStatus().getLabel() + "）" + proofLabel;

            if (item.getStatus() == CheckStatus.DAY_STUDENT) {
                boolean female = "女".equals(item.getGender());
                rich.append(text, female ? greenFont : blueFont);
            } else if (item.getStatus() == CheckStatus.LONG_SICK_LEAVE) {
                rich.append(text, redFont);
            } else {
                rich.append(text, normalFont);
            }

            if (i < details.size() - 1) {
                rich.append("\uFF1B", normalFont);
            }
        }

        cell.setCellValue(rich);
    }

    /**
     * 为请假明细导出添加「证明照片」详情Sheet，包含超链接。
     */
    private void addLeaveDetailProofSheet(
            Workbook workbook, LeaveDetailData data, Path proofExportRoot) {
        // 收集所有有证明文件的学生
        java.util.LinkedHashMap<String, String> proofMap = new java.util.LinkedHashMap<>();
        for (LeaveDetailData.ClassLeaveRow row : data.getRows()) {
            if (row.getDetails() == null) continue;
            for (LeaveDetailData.LeaveDetailItem item : row.getDetails()) {
                if (item.getProofFile() != null && !item.getProofFile().isBlank()) {
                    String key = item.getStudentName() + "_" + item.getProofFile().hashCode();
                    proofMap.putIfAbsent(key, item.getProofFile());
                }
            }
        }

        if (proofMap.isEmpty()) return;

        CellStyle headerStyle = createHeaderStyle(workbook);
        Sheet proofSheet = workbook.createSheet("证明照片");
        Row headerRow = proofSheet.createRow(0);
        String[] headers = {"序号", "学生姓名", "状态", "查看证明"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CreationHelper helper = workbook.getCreationHelper();
        CellStyle linkStyle = workbook.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFFont linkFont =
                (org.apache.poi.xssf.usermodel.XSSFFont) workbook.createFont();
        linkFont.setUnderline(org.apache.poi.xssf.usermodel.XSSFFont.U_SINGLE);
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        linkStyle.setFont(linkFont);

        int proofIdx = 0;
        String prevStudentName = null;
        for (LeaveDetailData.ClassLeaveRow row : data.getRows()) {
            if (row.getDetails() == null) continue;
            for (LeaveDetailData.LeaveDetailItem item : row.getDetails()) {
                if (item.getProofFile() == null || item.getProofFile().isBlank()) continue;
                // 同一学生多次出现只取第一次
                if (prevStudentName != null && prevStudentName.equals(item.getStudentName())) continue;
                prevStudentName = item.getStudentName();

                Row proofRow = proofSheet.createRow(++proofIdx);
                proofRow.createCell(0).setCellValue(proofIdx);
                proofRow.createCell(1).setCellValue(item.getStudentName());
                proofRow.createCell(2).setCellValue(item.getStatus() != null
                        ? item.getStatus().getLabel() : "");

                // 复制证明文件到导出目录（只允许 uploadDir 内的文件）
                Path source = resolveProofSource(item.getProofFile()).orElse(null);
                String copiedLink = "";
                try {
                    if (source != null) {
                        Path targetDir = proofExportRoot.resolve("证明照片");
                        Files.createDirectories(targetDir);
                        String safeName = proofIdx + "_" + item.getStudentName()
                                + item.getProofFile().substring(
                                        item.getProofFile().lastIndexOf('.'));
                        Path target = targetDir.resolve(safeName);
                        Files.copy(source, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        copiedLink = "./证明照片/" + safeName;
                    }
                } catch (IOException e) {
                    log.warn("复制证明文件失败: {}", source, e);
                }

                if (!copiedLink.isEmpty()) {
                    Hyperlink link = helper.createHyperlink(HyperlinkType.FILE);
                    link.setAddress(copiedLink);
                    Cell proofCell = proofRow.createCell(3);
                    proofCell.setCellValue("📷 查看");
                    proofCell.setHyperlink(link);
                    proofCell.setCellStyle(linkStyle);
                } else {
                    proofRow.createCell(3).setCellValue("✗ 文件缺失");
                }
            }
        }

        proofSheet.setColumnWidth(0, 6 * 256);
        proofSheet.setColumnWidth(1, 15 * 256);
        proofSheet.setColumnWidth(2, 12 * 256);
        proofSheet.setColumnWidth(3, 15 * 256);
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