package net.alan.jupiter.application.counselor;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.domain.repository.TaskRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportCounselorService {

    private static final List<String> TEMPLATE_HEADERS = List.of(
            "班级", "姓名", "性别", "学号", "手机号", "密码", "部门"
    );

    private static final List<String> SAMPLE_ROW = List.of(
            "机车2602", "张浩天", "男", "2602531414", "15226511870", "1234", "权益部"
    );

    private static final List<List<String>> INSTRUCTION_ROWS = List.of(
            List.of("班级", "必填", "文本", "机车2602", "学生所属班级"),
            List.of("姓名", "必填", "文本", "张浩天", ""),
            List.of("性别", "必填", "文本", "男", "男 / 女"),
            List.of("学号", "必填", "文本", "2602531414", "登录账户，唯一标识，必须为文本格式"),
            List.of("手机号", "必填", "文本", "15226511870", "11 位数字"),
            List.of("密码", "必填", "文本", "1234", "初始登录密码，查宿者后续可自行修改"),
            List.of("部门", "选填", "文本", "权益部", "学生会部门，如权益部、宿管部")
    );

    private final CounselorRepository counselorRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    public byte[] downloadTemplate() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet importSheet = workbook.createSheet("导入模板");
            Row headerRow = importSheet.createRow(0);
            for (int i = 0; i < TEMPLATE_HEADERS.size(); i++) {
                headerRow.createCell(i).setCellValue(TEMPLATE_HEADERS.get(i));
            }
            Row sampleRow = importSheet.createRow(1);
            for (int i = 0; i < SAMPLE_ROW.size(); i++) {
                sampleRow.createCell(i).setCellValue(SAMPLE_ROW.get(i));
            }
            autoSizeSheet(importSheet, TEMPLATE_HEADERS.size());

            Sheet instructionSheet = workbook.createSheet("填写说明");
            Row insHeader = instructionSheet.createRow(0);
            String[] insHeaders = {"列", "是否必填", "数据类型", "示例值", "说明"};
            for (int i = 0; i < insHeaders.length; i++) {
                insHeader.createCell(i).setCellValue(insHeaders[i]);
            }
            for (int r = 0; r < INSTRUCTION_ROWS.size(); r++) {
                Row row = instructionSheet.createRow(r + 1);
                List<String> cells = INSTRUCTION_ROWS.get(r);
                for (int c = 0; c < cells.size(); c++) {
                    row.createCell(c).setCellValue(cells.get(c));
                }
            }
            autoSizeSheet(instructionSheet, insHeaders.length);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "生成模板失败");
        }
    }

    private void autoSizeSheet(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
            int width = Math.min(sheet.getColumnWidth(i) + 512, 15 * 256);
            sheet.setColumnWidth(i, width);
        }
    }

    public List<CounselorImportRow> previewImport(byte[] excelData) {
        List<CounselorImportRow> rows = parseExcel(excelData);
        Set<String> existingStudentNos = counselorRepository.findByStudentNoIn(
                        rows.stream()
                                .map(CounselorImportRow::getStudentNo)
                                .filter(Objects::nonNull)
                                .toList())
                .stream()
                .map(Counselor::getStudentNo)
                .collect(Collectors.toSet());

        rows.forEach(row -> row.setExists(existingStudentNos.contains(row.getStudentNo())));
        return rows;
    }

    @Transactional
    public ImportResult importCounselors(byte[] excelData) {
        List<CounselorImportRow> rows = parseExcel(excelData);
        ImportResult result = new ImportResult();
        List<Counselor> toSave = new ArrayList<>();

        Set<String> newStudentNos = rows.stream()
                .map(CounselorImportRow::getStudentNo)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        List<Counselor> existingNonAdmins = counselorRepository
                .findByIsActiveTrueAndRoleIgnoreCase("COUNSELOR");
        for (Counselor c : existingNonAdmins) {
            if (c.getStudentNo() != null && !newStudentNos.contains(c.getStudentNo())) {
                taskRepository.clearCounselorReference(c.getId());
                counselorRepository.delete(c);
                result.addDeleted(c.getStudentNo());
            }
        }
        if (result.getDeleted() > 0) {
            counselorRepository.flush();
        }

        for (CounselorImportRow row : rows) {
            if (row.getStudentNo() == null || row.getStudentNo().isBlank()) {
                result.addError("学号为空");
                continue;
            }
            if (row.getPassword() == null || row.getPassword().isBlank()) {
                result.addError(String.format("学号 %s 密码为空", row.getStudentNo()));
                continue;
            }

            Counselor existing = counselorRepository.findByStudentNo(row.getStudentNo()).orElse(null);
            if (existing != null) {
                existing.setUsername(row.getStudentNo());
                existing.setName(row.getName());
                existing.setGender(row.getGender());
                existing.setClassName(row.getClassName());
                existing.setPhone(row.getPhone());
                existing.setDepartment(row.getDepartment());
                if (!passwordEncoder.matches(row.getPassword(), existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(row.getPassword()));
                    existing.setTokenVersion(
                            (existing.getTokenVersion() != null ? existing.getTokenVersion() : 0L) + 1);
                }
                existing.setIsActive(true);
                toSave.add(existing);
                result.addUpdated(row.getStudentNo());
            } else {
                toSave.add(buildNew(row));
                result.addImported(row.getStudentNo());
            }
        }

        if (!toSave.isEmpty()) {
            counselorRepository.saveAll(toSave);
        }

        return result;
    }

    private Counselor buildNew(CounselorImportRow row) {
        return Counselor.builder()
                .username(row.getStudentNo())
                .name(row.getName())
                .gender(row.getGender())
                .studentNo(row.getStudentNo())
                .className(row.getClassName())
                .phone(row.getPhone())
                .department(row.getDepartment())
                .password(passwordEncoder.encode(row.getPassword()))
                .role("COUNSELOR")
                .isActive(true)
                .build();
    }

    private List<CounselorImportRow> parseExcel(byte[] excelData) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelData))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR, "Excel 文件中未找到工作表");
            }

            ColumnIndex idx = detectColumns(sheet);
            List<CounselorImportRow> rows = new ArrayList<>();

            for (int i = idx.headerRow + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String studentNo = getCellString(row, idx.studentNo);
                if (studentNo == null || studentNo.isBlank()) continue;

                rows.add(CounselorImportRow.builder()
                        .studentNo(studentNo)
                        .name(getCellString(row, idx.name))
                        .gender(getCellString(row, idx.gender))
                        .className(getCellString(row, idx.className))
                        .phone(getCellString(row, idx.phone))
                        .password(getCellString(row, idx.password))
                        .department(getCellString(row, idx.department))
                        .build());
            }
            return rows;
        } catch (IOException e) {
            throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR, "无法读取 Excel 文件");
        }
    }

    private ColumnIndex detectColumns(Sheet sheet) {
        ColumnIndex idx = new ColumnIndex();
        idx.headerRow = -1;

        for (int rowNum = 0; rowNum <= Math.min(sheet.getLastRowNum(), 5); rowNum++) {
            Row row = sheet.getRow(rowNum);
            if (row == null) continue;
            for (int colNum = 0; colNum <= row.getLastCellNum(); colNum++) {
                String header = getCellString(row, colNum);
                if (header == null) continue;
                switch (header) {
                    case "学号" -> idx.studentNo = colNum;
                    case "姓名" -> idx.name = colNum;
                    case "性别" -> idx.gender = colNum;
                    case "班级" -> idx.className = colNum;
                    case "手机号" -> idx.phone = colNum;
                    case "密码" -> idx.password = colNum;
                    case "部门" -> idx.department = colNum;
                }
            }
            if (idx.studentNo >= 0 && idx.name >= 0) {
                idx.headerRow = rowNum;
                return idx;
            }
        }

        throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR,
                "未找到表头行，请确保 Excel 包含「学号」和「姓名」列");
    }

    private String getCellString(Row row, int colIndex) {
        if (colIndex < 0 || row == null) return null;
        org.apache.poi.ss.usermodel.Cell cell = row.getCell(colIndex);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    try {
                        yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                    } catch (Exception e) {
                        yield org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                    }
                }
                yield org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e1) {
                    try {
                        yield org.apache.poi.ss.util.NumberToTextConverter.toText(cell.getNumericCellValue());
                    } catch (Exception e2) {
                        yield cell.getCellFormula();
                    }
                }
            }
            case BLANK -> null;
            default -> null;
        };
    }

    @Setter
    @Getter
    @Builder
    public static class CounselorImportRow {
        private String className;
        private String name;
        private String gender;
        private String studentNo;
        private String phone;
        private String password;
        private String department;
        private boolean exists;
    }

    @Setter
    @Getter
    public static class ImportResult {
        private int imported;
        private int updated;
        private int skipped;
        private int deleted;
        private final List<String> errors = new ArrayList<>();
        private final List<String> importedList = new ArrayList<>();
        private final List<String> updatedList = new ArrayList<>();
        private final List<String> skippedList = new ArrayList<>();
        private final List<String> deletedList = new ArrayList<>();

        public void addImported(String studentNo) { imported++; importedList.add(studentNo); }
        public void addUpdated(String studentNo) { updated++; updatedList.add(studentNo); }
        public void addSkipped(String studentNo) { skipped++; skippedList.add(studentNo); }
        public void addDeleted(String studentNo) { deleted++; deletedList.add(studentNo); }
        public void addError(String msg) { errors.add(msg); }
    }

    private static class ColumnIndex {
        int headerRow = -1;
        int studentNo = -1;
        int name = -1;
        int gender = -1;
        int className = -1;
        int phone = -1;
        int password = -1;
        int department = -1;
    }
}