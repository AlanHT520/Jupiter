package net.alan.jupiter.infrastructure.excel;

import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Student;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ExcelImportHelper {

    /**
     * 班级元数据：从「班级明细」Sheet 解析得到。
     */
    public record ClassMeta(String className, String college, String headTeacher) {}

    /**
     * 导入解析结果。
     */
    public record ImportData(List<Student> students, Map<String, ClassMeta> classMetas) {}

    /**
     * 解析班级花名册 Excel。<p>
     * Sheet 1：学生列表（与旧版本兼容）；Sheet 2（可选）：「班级明细」Sheet。
     */
    public ImportData parseClassRoster(byte[] excelData) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            List<Student> students = parseStudentSheet(workbook.getSheetAt(0));
            Map<String, ClassMeta> classMetas = parseClassDetailSheet(workbook.getSheet("班级明细"));
            return new ImportData(students, classMetas);
        } catch (IOException e) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR, "无法读取Excel文件");
        }
    }

    /**
     * 解析 Sheet 1 的学生列表（与旧版本 parseClassRoster 逻辑一致）。
     */
    private List<Student> parseStudentSheet(Sheet sheet) {
        if (sheet == null) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR, "Excel文件中未找到工作表");
        }

        int headerRowIdx = findHeaderRow(sheet);
        ColumnMapping mapping = detectColumnMapping(sheet, headerRowIdx);

        List<Student> students = new ArrayList<>();
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String studentNo = mapping.studentNoIdx >= 0
                            ? getCellString(row, mapping.studentNoIdx) : null;
                    if (studentNo == null || studentNo.isBlank()) continue;

                    // 跳过模板中的示例数据行
                    String name = mapping.nameIdx >= 0 ? getCellString(row, mapping.nameIdx) : null;
                    if (isSampleRow(studentNo, name)) {
                        log.debug("跳过模板示例行: 学号={}, 姓名={}", studentNo, name);
                        continue;
                    }

                    Student student = Student.builder()
                            .studentNo(studentNo)
                            .name(name)
                            .gender(mapping.genderIdx >= 0 ? getCellString(row, mapping.genderIdx) : null)
                            .className(mapping.classNameIdx >= 0 ? getCellString(row, mapping.classNameIdx) : null)
                            .buildingName(mapping.buildingIdx >= 0 ? getCellString(row, mapping.buildingIdx) : null)
                            .floorNumber(mapping.floorIdx >= 0
                                    ? parseInteger(getCellString(row, mapping.floorIdx)) : null)
                            .roomNumber(mapping.roomIdx >= 0 ? getCellString(row, mapping.roomIdx) : null)
                            .bedNumber(mapping.bedIdx >= 0 ? getCellString(row, mapping.bedIdx) : null)
                            .phone(mapping.phoneIdx >= 0 ? getCellString(row, mapping.phoneIdx) : null)
                            .remark(mapping.remarkIdx >= 0 ? getCellString(row, mapping.remarkIdx) : null)
                            .build();

                    if (mapping.statusIdx >= 0) {
                        applyStatus(student, getCellString(row, mapping.statusIdx));
                    }

                    students.add(student);
                } catch (Exception e) {
                    log.warn("解析花名册第{}行失败: {}", i + 1, e.getMessage());
                }
            }
            return students;
    }

    /**
     * 解析「班级明细」Sheet（可选）。<p>
     * 模板是横向按学院分组的：每两列一组，(学院名, "班主任") 为表头，下面各行是 (班级, 班主任)。<br>
     * 同一学院下班级数不等长时，遇到空单元格跳过该组的当前行。<br>
     * 如果没有「班级明细」Sheet，返回空 Map，不影响学生导入。
     */
    private Map<String, ClassMeta> parseClassDetailSheet(Sheet sheet) {
        if (sheet == null) return Map.of();

        Map<String, ClassMeta> result = new LinkedHashMap<>();

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return result;

        int groupCount = headerRow.getLastCellNum() / 2;

        for (int g = 0; g < groupCount; g++) {
            int nameCol = g * 2;
            int teacherCol = nameCol + 1;

            String college = getCellString(headerRow, nameCol);
            if (college == null || college.isBlank()) continue;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String className = getCellString(row, nameCol);
                if (className == null || className.isBlank()) continue;

                String teacher = getCellString(row, teacherCol);
                result.put(className, new ClassMeta(className, college, teacher));
            }
        }
        return result;
    }

    private void applyStatus(Student student, String status) {
        if (status == null || status.isBlank()) return;
        String trimmed = status.trim();
        if (trimmed.equals("在校") || trimmed.equals("在寝")) return;

        java.time.LocalDate today = java.time.LocalDate.now();
        switch (trimmed) {
            case "征兵", "当兵" -> student.setMilitaryStartDate(today);
            case "实习" -> student.setInternStartDate(today);
            case "走读" -> student.setDayStudentStartDate(today);
            case "长期病假" -> student.setLongSickLeaveStartDate(today);
            default -> log.warn("未知状态值: {}，学生: {}", trimmed, student.getStudentNo());
        }
    }

    /**
     * 检测是否为模板中的示例数据行。
     */
    private boolean isSampleRow(String studentNo, String name) {
        return "2602531414".equals(studentNo) && "张浩天".equals(name);
    }

    private int findHeaderRow(Sheet sheet) {
        for (int rowIdx = 0; rowIdx <= Math.min(sheet.getLastRowNum(), 5); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            for (int colIdx = 0; colIdx < row.getLastCellNum(); colIdx++) {
                Cell cell = row.getCell(colIdx);
                if (cell != null && "学号".equals(getCellStringDirect(cell))) {
                    return rowIdx;
                }
            }
        }
        throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR,
                "未找到表头行，请确保 Excel 包含「学号」列");
    }

    private ColumnMapping detectColumnMapping(Sheet sheet, int headerRowIdx) {
        Row headerRow = sheet.getRow(headerRowIdx);
        ColumnMapping mapping = new ColumnMapping();

        for (int colIdx = 0; colIdx < headerRow.getLastCellNum(); colIdx++) {
            String header = getCellString(headerRow, colIdx);
            if (header == null) continue;

            switch (header) {
                case "学号" -> mapping.studentNoIdx = colIdx;
                case "姓名" -> mapping.nameIdx = colIdx;
                case "性别" -> mapping.genderIdx = colIdx;
                case "班级" -> mapping.classNameIdx = colIdx;
                case "楼栋", "宿舍楼", "宿舍楼编号" -> mapping.buildingIdx = colIdx;
                case "楼层" -> mapping.floorIdx = colIdx;
                case "房间", "宿舍号", "房间号" -> mapping.roomIdx = colIdx;
                case "床位", "床位号" -> mapping.bedIdx = colIdx;
                case "手机号" -> mapping.phoneIdx = colIdx;
                case "状态" -> mapping.statusIdx = colIdx;
                case "备注" -> mapping.remarkIdx = colIdx;
            }
        }

        if (mapping.studentNoIdx < 0 || mapping.nameIdx < 0) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR,
                    "表头必须包含「学号」和「姓名」列");
        }
        return mapping;
    }

    private static class ColumnMapping {
        int studentNoIdx = -1;
        int nameIdx = -1;
        int genderIdx = -1;
        int classNameIdx = -1;
        int buildingIdx = -1;
        int floorIdx = -1;
        int roomIdx = -1;
        int bedIdx = -1;
        int phoneIdx = -1;
        int statusIdx = -1;
        int remarkIdx = -1;
    }

    private String getCellStringDirect(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } catch (Exception e) {
                        yield NumberToTextConverter.toText(cell.getNumericCellValue());
                    }
                }
                yield NumberToTextConverter.toText(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e1) {
                    try {
                        yield NumberToTextConverter.toText(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            case BLANK -> "";
            default -> "";
        };
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 兼容「4」「4层」「F4」等写法；非整数（如 3.9）直接判为无效，避免静默截断
        String normalized = value.trim().replaceAll("^[Ff]", "").replaceAll("[层楼]$", "").trim();
        try {
            return new java.math.BigDecimal(normalized).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            log.warn("楼层字段不是整数，已跳过: {}", value);
            return null;
        }
    }

    private String getCellString(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    try {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } catch (Exception e) {
                        yield NumberToTextConverter.toText(cell.getNumericCellValue());
                    }
                }
                yield NumberToTextConverter.toText(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e1) {
                    try {
                        yield NumberToTextConverter.toText(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            case BLANK -> null;
            default -> null;
        };
    }
}