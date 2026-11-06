package net.alan.jupiter.infrastructure.excel;

import net.alan.jupiter.domain.enums.CheckStatus;
import net.alan.jupiter.domain.model.LeaveDetailData;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 请假明细导出回归：按样例只输出一张班级汇总表（含请假明细列），不再拆多个 Sheet。
 */
class LeaveDetailExportTest {

    @Test
    void exportContainsSingleClassSummarySheet() throws Exception {
        LeaveDetailData data = LeaveDetailData.builder()
                .date("2026-11-05")
                .collegeName("")
                .header(LeaveDetailData.HeaderSummary.builder()
                        .totalStudents(40).male(30).female(10)
                        .actual(38).actualMale(29).actualFemale(9)
                        .leaveTotal(2).longSickLeave(1)
                        .build())
                .rows(List.of(LeaveDetailData.ClassLeaveRow.builder()
                        .className("机车2602")
                        .headTeacher("王老师")
                        .enrolled(40).actual(38).leave(2).longSickLeave(1)
                        .details(List.of(LeaveDetailData.LeaveDetailItem.builder()
                                .studentName("张三")
                                .studentNo("2602531401")
                                .status(CheckStatus.LONG_SICK_LEAVE)
                                .gender("男")
                                .phone("13800001111")
                                .startDate("2026-09-01")
                                .endDate("2026-12-31")
                                .days(122)
                                .proofFile("C:/proofs/leave-zhangsan.jpg")
                                .remark("已办理手续")
                                .build()))
                        .build()))
                .build();

        byte[] bytes = new ExcelExportHelper().exportLeaveDetail(data, null);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertNotNull(workbook.getSheet("请假明细"));
            assertNull(workbook.getSheet("请假学生明细"));
            var sheet = workbook.getSheet("请假明细");
            assertNotNull(sheet.getRow(0));
            assertEquals(1, workbook.getNumberOfSheets());
        }
    }
}
