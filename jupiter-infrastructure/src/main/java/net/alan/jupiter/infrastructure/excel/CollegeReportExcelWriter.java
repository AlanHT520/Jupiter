package net.alan.jupiter.infrastructure.excel;

import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Component
public class CollegeReportExcelWriter {

    /**
     * 导出学院汇总上报 Excel。
     * Sheet 1: 学院汇总; Sheet 2: 各楼层具体情况; Sheet 3: 新生晚查寝
     */
    public byte[] exportCollegeReport(CollegeReportData data) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle totalStyle = createTotalStyle(workbook);

            createCollegeSummarySheet(workbook, data, headerStyle, totalStyle);
            createFloorDetailSheet(workbook, data, headerStyle);
            createNewStudentSheet(workbook, data, headerStyle);

            return toByteArray(workbook);
        } catch (IOException e) {
            log.error("导出学院汇总上报失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出学院汇总上报失败，请稍后再试");
        }
    }

    private void createCollegeSummarySheet(Workbook workbook, CollegeReportData data,
                                            CellStyle headerStyle, CellStyle totalStyle) {
        Sheet sheet = workbook.createSheet("学院汇总");
        Row headerRow = sheet.createRow(0);
        String[] headers = {"学院应到/实到", "楼栋", "应到", "实到", "事假", "病假", "请假总数",
                "晚归", "征兵", "走读", "旷宿", "未查", "旷宿明细", "长期病假", "备注"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int totalShould = 0, totalActual = 0, totalPersonalLeave = 0, totalSickLeave = 0;
        int totalLateReturn = 0, totalMilitary = 0, totalDayStudent = 0, totalAbsent = 0,
            totalUnchecked = 0, totalLongSick = 0;

        int rowIdx = 1;
        for (CollegeReportData.BuildingRow br : data.getBuildings()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(data.getCollegeName() != null ? data.getCollegeName() : "");
            row.createCell(1).setCellValue(br.getBuildingName());
            row.createCell(2).setCellValue(br.getShould());
            row.createCell(3).setCellValue(br.getActual());
            row.createCell(4).setCellValue(br.getPersonalLeave());
            row.createCell(5).setCellValue(br.getSickLeave());
            row.createCell(6).setCellValue(br.getPersonalLeave() + br.getSickLeave());
            row.createCell(7).setCellValue(br.getLateReturn());
            row.createCell(8).setCellValue(br.getMilitary());
            row.createCell(9).setCellValue(br.getDayStudent());
            row.createCell(10).setCellValue(br.getAbsent());
            row.createCell(11).setCellValue(br.getUnchecked());
            row.createCell(12).setCellValue(br.getAbsentNames() != null ? br.getAbsentNames() : "");
            row.createCell(13).setCellValue(br.getLongSickLeave());
            row.createCell(14).setCellValue(br.getRemark() != null ? br.getRemark() : "");

            totalShould += br.getShould();
            totalActual += br.getActual();
            totalPersonalLeave += br.getPersonalLeave();
            totalSickLeave += br.getSickLeave();
            totalLateReturn += br.getLateReturn();
            totalMilitary += br.getMilitary();
            totalDayStudent += br.getDayStudent();
            totalAbsent += br.getAbsent();
            totalUnchecked += br.getUnchecked();
            totalLongSick += br.getLongSickLeave();
        }

        Row totalRow = sheet.createRow(rowIdx);
        totalRow.createCell(0).setCellValue("合计");
        totalRow.createCell(1).setCellValue("");
        totalRow.createCell(2).setCellValue(totalShould);
        totalRow.createCell(3).setCellValue(totalActual);
        totalRow.createCell(4).setCellValue(totalPersonalLeave);
        totalRow.createCell(5).setCellValue(totalSickLeave);
        totalRow.createCell(6).setCellValue(totalPersonalLeave + totalSickLeave);
        totalRow.createCell(7).setCellValue(totalLateReturn);
        totalRow.createCell(8).setCellValue(totalMilitary);
        totalRow.createCell(9).setCellValue(totalDayStudent);
        totalRow.createCell(10).setCellValue(totalAbsent);
        totalRow.createCell(11).setCellValue(totalUnchecked);
        totalRow.createCell(12).setCellValue("");
        totalRow.createCell(13).setCellValue(totalLongSick);
        totalRow.createCell(14).setCellValue("");
        for (int i = 0; i < headers.length; i++) {
            totalRow.getCell(i).setCellStyle(totalStyle);
        }

        autoSizeColumns(sheet, headers.length);
    }

    private void createFloorDetailSheet(Workbook workbook, CollegeReportData data,
                                         CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("各楼层具体情况");
        Row headerRow = sheet.createRow(0);
        String[] headers = {"楼栋", "楼层", "应到", "实到", "事假", "病假", "请假总数",
                "晚归", "征兵", "实习", "旷宿", "未查", "旷宿人员"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (CollegeReportData.FloorDetailRow fr : data.getFloorDetails()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(fr.getBuildingName());
            row.createCell(1).setCellValue(fr.getFloor());
            row.createCell(2).setCellValue(fr.getShould());
            row.createCell(3).setCellValue(fr.getActual());
            row.createCell(4).setCellValue(fr.getPersonalLeave());
            row.createCell(5).setCellValue(fr.getSickLeave());
            row.createCell(6).setCellValue(fr.getPersonalLeave() + fr.getSickLeave());
            row.createCell(7).setCellValue(fr.getLateReturn());
            row.createCell(8).setCellValue(fr.getMilitary());
            row.createCell(9).setCellValue(fr.getIntern());
            row.createCell(10).setCellValue(fr.getAbsent());
            row.createCell(11).setCellValue(fr.getUnchecked());
            row.createCell(12).setCellValue(fr.getAbsentNames() != null ? fr.getAbsentNames() : "");
        }
        autoSizeColumns(sheet, headers.length);
    }

    private void createNewStudentSheet(Workbook workbook, CollegeReportData data,
                                        CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet("新生晚查寝");
        Row headerRow = sheet.createRow(0);
        String[] headers = {"应到总数", "实到总数", "男生", "女生", "征兵（男）", "请假",
                "实习", "走读", "长期病假", "旷宿", "晚归"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CollegeReportData.NewStudentRow ns = data.getNewStudent();
        if (ns != null) {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(ns.getShould());
            row.createCell(1).setCellValue(ns.getActual());
            row.createCell(2).setCellValue(ns.getMale());
            row.createCell(3).setCellValue(ns.getFemale());
            row.createCell(4).setCellValue(ns.getMilitaryMale());
            row.createCell(5).setCellValue(ns.getLeave());
            row.createCell(6).setCellValue(ns.getIntern());
            row.createCell(7).setCellValue(ns.getDayStudent());
            row.createCell(8).setCellValue(ns.getLongSickLeave());
            row.createCell(9).setCellValue(ns.getAbsent());
            row.createCell(10).setCellValue(ns.getLateReturn());
        }
        autoSizeColumns(sheet, headers.length);
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

    private CellStyle createTotalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
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

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CollegeReportData {
        private String date;
        private String collegeName;
        private List<BuildingRow> buildings;
        private List<FloorDetailRow> floorDetails;
        private NewStudentRow newStudent;

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class BuildingRow {
            private String buildingName;
            private int should;
            private int actual;
            private int personalLeave;
            private int sickLeave;
            private int lateReturn;
            private int military;
            private int intern;
            private int dayStudent;
            private int longSickLeave;
            private int absent;
            private int unchecked;
            private String absentNames;
            private String remark;
        }

        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class FloorDetailRow {
            private String buildingName;
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
        public static class NewStudentRow {
            private int should;
            private int actual;
            private int male;
            private int female;
            private int militaryMale;
            private int leave;
            private int intern;
            private int dayStudent;
            private int longSickLeave;
            private int absent;
            private int lateReturn;
        }
    }
}