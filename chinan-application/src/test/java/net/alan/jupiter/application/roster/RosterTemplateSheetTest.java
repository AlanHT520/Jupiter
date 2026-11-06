package net.alan.jupiter.application.roster;

import net.alan.jupiter.infrastructure.excel.ExcelImportHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 花名册模板与「班级信息」Sheet 的解析回归：
 * 模板必须自带 Sheet2（班级信息），且 Sheet2 的班主任能反查进 ClassMeta。
 */
class RosterTemplateSheetTest {

    private ImportClassRosterService templateOnlyService() {
        // downloadTemplate 不访问仓储，参数留空即可
        return new ImportClassRosterService(
                null, null, null, null, null, null, null, new ExcelImportHelper());
    }

    @Test
    void templateContainsClassInfoSheet() throws Exception {
        byte[] bytes = templateOnlyService().downloadTemplate();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(3, workbook.getNumberOfSheets());
            assertEquals("导入模板", workbook.getSheetAt(0).getSheetName());
            assertNotNull(workbook.getSheet("班级信息"), "模板必须包含「班级信息」Sheet");
            assertEquals("填写说明", workbook.getSheetAt(2).getSheetName());

            Row header = workbook.getSheet("班级信息").getRow(0);
            assertEquals("班级", header.getCell(0).getStringCellValue());
            assertEquals("班主任", header.getCell(2).getStringCellValue());
        }
    }

    @Test
    void parserReadsSimpleClassSheetAndHeadTeacher() {
        ExcelImportHelper helper = new ExcelImportHelper();
        byte[] bytes = buildRosterWithClassSheet();

        ExcelImportHelper.ImportData data = helper.parseClassRoster(bytes);

        assertEquals(1, data.students().size());
        ExcelImportHelper.ClassMeta meta = data.classMetas().get("机车2602");
        assertNotNull(meta, "班级信息 Sheet 应被解析");
        assertEquals("铁道机车学院", meta.college());
        assertEquals("王老师", meta.headTeacher());
        assertEquals("铁道机车", meta.major());
    }

    @Test
    void legacySheetNameIsStillAccepted() {
        ExcelImportHelper helper = new ExcelImportHelper();
        byte[] bytes = buildRosterWithLegacyClassSheet();

        ExcelImportHelper.ImportData data = helper.parseClassRoster(bytes);

        assertTrue(data.classMetas().containsKey("机车2602"));
        assertEquals("李老师", data.classMetas().get("机车2602").headTeacher());
    }

    private byte[] buildRosterWithClassSheet() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet students = workbook.createSheet("学生列表");
            Row header = students.createRow(0);
            String[] headers = {"班级", "姓名", "性别", "学号", "宿舍楼", "楼层", "房间号"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = students.createRow(1);
            String[] values = {"机车2602", "张三", "男", "2602531401", "B4", "4", "401"};
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }

            Sheet classes = workbook.createSheet("班级信息");
            Row classHeader = classes.createRow(0);
            String[] classHeaders = {"班级", "学院", "班主任", "专业", "年级"};
            for (int i = 0; i < classHeaders.length; i++) {
                classHeader.createCell(i).setCellValue(classHeaders[i]);
            }
            Row classRow = classes.createRow(1);
            String[] classValues = {"机车2602", "铁道机车学院", "王老师", "铁道机车", "2026"};
            for (int i = 0; i < classValues.length; i++) {
                classRow.createCell(i).setCellValue(classValues[i]);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private byte[] buildRosterWithLegacyClassSheet() {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet students = workbook.createSheet("学生列表");
            Row header = students.createRow(0);
            String[] headers = {"班级", "姓名", "性别", "学号"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            Row row = students.createRow(1);
            String[] values = {"机车2602", "张三", "男", "2602531401"};
            for (int i = 0; i < values.length; i++) {
                row.createCell(i).setCellValue(values[i]);
            }

            // 旧模板：横向分组 (学院, 班主任) / 下方 (班级, 班主任)
            Sheet classes = workbook.createSheet("班级明细");
            Row groupHeader = classes.createRow(0);
            groupHeader.createCell(0).setCellValue("铁道机车学院");
            groupHeader.createCell(1).setCellValue("班主任");
            Row classRow = classes.createRow(1);
            classRow.createCell(0).setCellValue("机车2602");
            classRow.createCell(1).setCellValue("李老师");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
