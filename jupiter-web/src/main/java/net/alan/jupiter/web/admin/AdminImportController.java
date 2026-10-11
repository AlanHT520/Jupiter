package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.roster.ImportClassRosterService;
import net.alan.jupiter.application.roster.RosterValidateService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.model.Student;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "管理端-导入导出", description = "管理端数据导入导出接口")
@RestController
@RequestMapping("/admin-api/import")
@RequiredArgsConstructor
@Slf4j
public class AdminImportController {

    private final ImportClassRosterService importClassRosterService;
    private final RosterValidateService rosterValidateService;

    @Operation(summary = "下载花名册导入模板")
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] templateData = importClassRosterService.downloadTemplate();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("花名册导入模板.xlsx", StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(disposition);
        return new ResponseEntity<>(templateData, headers, HttpStatus.OK);
    }

    @Operation(summary = "导入花名册")
    @PostMapping("/roster")
    public ApiResult<ImportClassRosterService.ImportResult> importRoster(
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            String fileName = file.getOriginalFilename();
            ImportClassRosterService.ImportResult result =
                    importClassRosterService.importClassRoster(data, fileName);
            return ApiResult.success(result);
        } catch (IOException e) {
            log.error("导入花名册失败", e);
            return ApiResult.error("文件读取失败，请重试");
        }
    }

    @Operation(summary = "预览花名册导入")
    @PostMapping("/roster/preview")
    public ApiResult<List<Student>> previewRoster(
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            List<Student> students = importClassRosterService.previewImport(data);
            return ApiResult.success(students);
        } catch (IOException e) {
            log.error("预览花名册导入失败", e);
            return ApiResult.error("文件读取失败，请重试");
        }
    }

    @Operation(summary = "验证导入数据")
    @PostMapping("/validate")
    public ApiResult<List<RosterValidateService.ValidationError>> validateImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "buildingId", defaultValue = "") String buildingId) {
        try {
            byte[] data = file.getBytes();
            List<Student> students = importClassRosterService.previewImport(data);
            List<RosterValidateService.ValidationError> errors;
            if (buildingId.isBlank()) {
                errors = students.stream()
                        .flatMap(s -> rosterValidateService.validateStudent(s).stream())
                        .collect(java.util.stream.Collectors.toList());
            } else {
                errors = rosterValidateService.validateBatchWithBuilding(students, buildingId);
            }
            return ApiResult.success(errors);
        } catch (IOException e) {
            log.error("验证导入数据失败", e);
            return ApiResult.error("文件读取失败，请重试");
        }
    }
}