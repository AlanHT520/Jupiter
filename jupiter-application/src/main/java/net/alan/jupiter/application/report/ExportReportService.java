package net.alan.jupiter.application.report;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.infrastructure.excel.ExcelExportHelper;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportReportService {

    private final Configuration freemarkerConfig;
    private final StatisticsService statisticsService;
    private final ExcelExportHelper excelExportHelper;

    public String exportToHtml(String taskId, String templateName) {
        try {
            StatisticsService.ReportData reportData;
            if (taskId != null && !taskId.isBlank()) {
                reportData = statisticsService.generateReport(taskId);
            } else {
                reportData = statisticsService.generateAggregatedReport();
            }
            return renderTemplate(reportData, templateName);
        } catch (BizException e) {
            throw e;
        } catch (TemplateException e) {
            log.error("模板处理失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "报表导出失败，模板处理出错");
        } catch (IOException e) {
            log.error("模板读取失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "报表导出失败，模板读取出错");
        }
    }

    public String exportToFile(String taskId, String templateName, String outputDir) {
        try {
            String html = exportToHtml(taskId, templateName);
            String safeName = (taskId != null && !taskId.isBlank())
                    ? "report_" + taskId.replaceAll("[^a-zA-Z0-9_-]", "_")
                    : "report_all";
            String fileName = safeName + "_" + System.currentTimeMillis() + ".html";
            Path normalizedDir = Path.of(outputDir).toAbsolutePath().normalize();
            Path outputPath = normalizedDir.resolve(fileName).normalize();
            if (!outputPath.startsWith(normalizedDir)) {
                throw new BizException(ErrorCode.EXPORT_ERROR, "非法导出路径");
            }
            Files.writeString(outputPath, html, StandardCharsets.UTF_8);
            return outputPath.toString();
        } catch (IOException e) {
            log.error("导出文件写入失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "报表导出失败，文件写入出错");
        }
    }

    public byte[] exportCheckRecordsToExcel(List<CheckRecord> records, String sheetName) {
        return excelExportHelper.exportCheckRecords(records, sheetName, null);
    }

    private String renderTemplate(StatisticsService.ReportData reportData, String templateName)
            throws IOException, TemplateException {
        Template template = freemarkerConfig.getTemplate(templateName);

        StringWriter writer = new StringWriter();
        Map<String, Object> dataModel = new java.util.HashMap<>();
        dataModel.put("date", reportData.getDate());
        dataModel.put("college", reportData.getCollege() != null ? reportData.getCollege() : "");
        dataModel.put("blocks", reportData.getBlocks());
        dataModel.put("showSummary", true);
        dataModel.put("showFloorDetail", true);
        dataModel.put("showNewStudent", true);
        template.process(dataModel, writer);

        return writer.toString();
    }
}