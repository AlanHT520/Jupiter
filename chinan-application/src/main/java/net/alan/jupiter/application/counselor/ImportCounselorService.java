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
            "姓名", "性别", "手机号", "密码", "部门", "学号", "班级"
    );

    private static final List<String> SAMPLE_ROW = List.of(
            "张浩天", "男", "15226511870", "123456", "权益部", "2602531414", "机车2602"
    );

    private static final List<List<String>> INSTRUCTION_ROWS = List.of(
            List.of("姓名", "必填", "文本", "张浩天", ""),
            List.of("性别", "选填", "文本", "男", "男 / 女"),
            List.of("手机号", "必填", "文本", "15226511870", "11 位数字，同时作为登录账号"),
            List.of("密码", "选填", "文本", "123456", "至少 6 位；留空则默认用手机号作为初始密码"),
            List.of("部门", "必填", "文本", "权益部", "如权益部、宿管部、学生会"),
            List.of("学号", "选填", "文本", "2602531414", "仅作档案，不再作为登录账号"),
            List.of("班级", "选填", "文本", "机车2602", "仅作档案")
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
            // 不再预置示例人员，避免示例姓名/手机号与真实名单冲突被误导入或误过滤
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

        Set<String> newPhones = rows.stream()
                .map(CounselorImportRow::getPhone)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        List<Counselor> existingNonAdmins = counselorRepository
                .findByIsActiveTrueAndRoleIgnoreCase("COUNSELOR");
        for (Counselor c : existingNonAdmins) {
            if (c.getPhone() != null && !newPhones.contains(c.getPhone())) {
                // 名单里没有 ≠ 删除账号：改为停用，保留历史查寝记录与任务归属
                taskRepository.clearCounselorReference(c.getId());
                c.setIsActive(false);
                counselorRepository.save(c);
                result.addDisabled(c.getPhone());
            }
        }
        if (result.getDeleted() > 0) {
            counselorRepository.flush();
        }

        for (CounselorImportRow row : rows) {
            if (row.getPhone() == null || !row.getPhone().matches("\\d{11}")) {
                result.addError("手机号为空或不是 11 位数字");
                continue;
            }
            if (row.getPassword() == null || row.getPassword().isBlank()) {
                // 密码列可留空：用手机号作为初始密码，导入后提示管理员
                row.setPassword(row.getPhone());
                result.addError(String.format(
                        "提示：手机号 %s 未填写密码，已使用手机号作为初始密码", row.getPhone()));
            }

            Counselor existing = counselorRepository.findByPhone(row.getPhone()).orElse(null);
            if (existing != null) {
                existing.setUsername(row.getPhone());
                existing.setName(row.getName());
                existing.setGender(row.getGender());
                existing.setClassName(row.getClassName());
                existing.setPhone(row.getPhone());
                existing.setDepartment(row.getDepartment());
                if (!passwordEncoder.matches(row.getPassword(), existing.getPassword())) {
                    existing.setPassword(passwordEncoder.encode(row.getPassword()));
                }
                existing.setPasswordPlain(row.getPassword());
                existing.setIsActive(true);
                toSave.add(existing);
                result.addUpdated(row.getPhone());
            } else {
                toSave.add(buildNew(row));
                result.addImported(row.getPhone());
            }
        }

        if (!toSave.isEmpty()) {
            counselorRepository.saveAll(toSave);
        }

        return result;
    }

    private Counselor buildNew(CounselorImportRow row) {
        return Counselor.builder()
                .username(row.getPhone())
                .name(row.getName())
                .gender(row.getGender())
                .studentNo(row.getStudentNo())
                .className(row.getClassName())
                .phone(row.getPhone())
                .department(row.getDepartment())
                .password(passwordEncoder.encode(row.getPassword()))
                .passwordPlain(row.getPassword())
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
                String phone = getCellString(row, idx.phone);
                if (phone == null || phone.isBlank()) continue;

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
                String key = header.replace("*", "").replace(" ", "").trim();
                switch (key) {
                    case "学号", "工号" -> idx.studentNo = colNum;
                    case "姓名", "名字" -> idx.name = colNum;
                    case "性别" -> idx.gender = colNum;
                    case "班级", "班级名称" -> idx.className = colNum;
                    case "手机号", "手机号码", "电话", "联系电话" -> idx.phone = colNum;
                    case "密码", "初始密码" -> idx.password = colNum;
                    case "部门", "所属部门" -> idx.department = colNum;
                }
            }
            // 新规则：手机号是登录账号，学号改为选填
            if (idx.phone >= 0 && idx.name >= 0) {
                idx.headerRow = rowNum;
                return idx;
            }
        }

        throw new BizException(ErrorCode.EXCEL_FORMAT_ERROR,
                "未找到表头行，请确保 Excel 包含「手机号」和「姓名」列");
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
        private int disabled;
        private final List<String> errors = new ArrayList<>();
        private final List<String> importedList = new ArrayList<>();
        private final List<String> updatedList = new ArrayList<>();
        private final List<String> skippedList = new ArrayList<>();
        private final List<String> deletedList = new ArrayList<>();
        private final List<String> disabledList = new ArrayList<>();

        public void addImported(String studentNo) { imported++; importedList.add(studentNo); }
        public void addUpdated(String studentNo) { updated++; updatedList.add(studentNo); }
        public void addSkipped(String studentNo) { skipped++; skippedList.add(studentNo); }
        public void addDeleted(String studentNo) { deleted++; deletedList.add(studentNo); }
        public void addDisabled(String studentNo) { disabled++; disabledList.add(studentNo); }
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