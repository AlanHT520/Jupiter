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
     * 班级元数据：从「班级信息 / 班级明细」Sheet 解析得到。
     */
    public record ClassMeta(String className, String college, String headTeacher,
                            String major, String grade) {}

    /**
     * 导入解析结果。
     */
    public record ImportData(List<Student> students, Map<String, ClassMeta> classMetas) {}

    /** 第二张表可用的名字：新模板用「班级信息」，旧文件用「班级明细」，也兼容 Sheet2/第二张表。 */
    private static final List<String> CLASS_SHEET_NAMES =
            List.of("班级信息", "班级明细", "Sheet2", "sheet2", "第二张表");

    /**
     * 解析班级花名册 Excel。<p>
     * Sheet 1：学生列表；Sheet 2（可选）：「班级信息」/「班级明细」。
     */
    public ImportData parseClassRoster(byte[] excelData) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            List<Student> students = parseStudentSheet(workbook.getSheetAt(0));
            Map<String, ClassMeta> classMetas = parseClassDetailSheet(findClassSheet(workbook));
            return new ImportData(students, classMetas);
        } catch (IOException e) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR, "无法读取Excel文件");
        }
    }

    private Sheet findClassSheet(Workbook workbook) {
        for (String name : CLASS_SHEET_NAMES) {
            Sheet sheet = workbook.getSheet(name);
            if (sheet != null) {
                return sheet;
            }
        }
        // 没有按名字命名时，若第二张表不是「填写说明」也当作班级信息表
        if (workbook.getNumberOfSheets() > 1) {
            Sheet second = workbook.getSheetAt(1);
            if (second != null && !"填写说明".equals(second.getSheetName())) {
                return second;
            }
        }
        return null;
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

                    // 模板示例行不特殊过滤：真实名单里可能有同名同号的学生
                    String name = mapping.nameIdx >= 0 ? getCellString(row, mapping.nameIdx) : null;

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
     * 解析班级信息 Sheet（可选）。支持两种格式：<p>
     * A. 简单表（推荐）：表头包含「班级」，可带「学院 / 班主任 / 专业 / 年级」列，每行一个班级；<br>
     * B. 旧模板的横向分组：每两列一组，(学院名, "班主任") 为表头，下面每行 (班级, 班主任)。<br>
     * 如果没有班级信息 Sheet，返回空 Map，不影响学生导入。
     */
    private Map<String, ClassMeta> parseClassDetailSheet(Sheet sheet) {
        if (sheet == null) return Map.of();

        Map<String, ClassMeta> result = new LinkedHashMap<>();
        int headerRowIdx = findClassHeaderRow(sheet);
        if (headerRowIdx >= 0) {
            parseSimpleClassTable(sheet, headerRowIdx, result);
            if (!result.isEmpty()) {
                return result;
            }
        }
        parseLegacyClassTable(sheet, result);
        return result;
    }

    private int findClassHeaderRow(Sheet sheet) {
        for (int rowIdx = 0; rowIdx <= Math.min(sheet.getLastRowNum(), 5); rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            if (row == null) continue;
            boolean hasClass = false;
            for (int col = 0; col < row.getLastCellNum(); col++) {
                String value = getCellStringDirect(row.getCell(col));
                if (value == null) continue;
                if ("班级".equals(value) || "班级名称".equals(value)) {
                    hasClass = true;
                    break;
                }
            }
            if (hasClass) {
                return rowIdx;
            }
        }
        return -1;
    }

    private void parseSimpleClassTable(Sheet sheet, int headerRowIdx, Map<String, ClassMeta> result) {
        Row headerRow = sheet.getRow(headerRowIdx);
        int classCol = -1, collegeCol = -1, teacherCol = -1, majorCol = -1, gradeCol = -1;
        for (int col = 0; col < headerRow.getLastCellNum(); col++) {
            String value = getCellStringDirect(headerRow.getCell(col));
            if (value == null) continue;
            switch (value) {
                case "班级", "班级名称" -> classCol = col;
                case "学院", "院系", "学院名称", "系部" -> collegeCol = col;
                case "班主任", "辅导员", "班主任姓名", "辅导员姓名" -> teacherCol = col;
                case "专业" -> majorCol = col;
                case "年级" -> gradeCol = col;
                default -> { }
            }
        }
        if (classCol < 0) return;

        for (int r = headerRowIdx + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String className = trim(getCellString(row, classCol));
            if (className == null || className.isBlank()) continue;
            result.put(className, new ClassMeta(
                    className,
                    trim(getCellString(row, collegeCol)),
                    trim(getCellString(row, teacherCol)),
                    trim(getCellString(row, majorCol)),
                    trim(getCellString(row, gradeCol))));
        }
    }

    private void parseLegacyClassTable(Sheet sheet, Map<String, ClassMeta> result) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return;

        int groupCount = headerRow.getLastCellNum() / 2;
        for (int g = 0; g < groupCount; g++) {
            int nameCol = g * 2;
            int teacherCol = nameCol + 1;

            String college = trim(getCellString(headerRow, nameCol));
            if (college == null || college.isBlank()) continue;

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String className = trim(getCellString(row, nameCol));
                if (className == null || className.isBlank()) continue;

                result.put(className, new ClassMeta(
                        className, college, trim(getCellString(row, teacherCol)), null, null));
            }
        }
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
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