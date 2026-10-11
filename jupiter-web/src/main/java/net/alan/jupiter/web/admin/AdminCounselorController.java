package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.counselor.CounselorManagementService;
import net.alan.jupiter.application.counselor.ImportCounselorService;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.web.dto.CounselorDto;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "管理端-查宿者", description = "管理端查宿者账户管理接口")
@RestController
@RequestMapping("/admin-api/counselors")
@RequiredArgsConstructor
@Slf4j
public class AdminCounselorController {

    private final CounselorManagementService counselorManagementService;
    private final ImportCounselorService importCounselorService;

    @Operation(summary = "查宿者列表")
    @GetMapping
    public ApiResult<List<CounselorDto>> listCounselors() {
        List<CounselorDto> dtos = counselorManagementService.listCounselors()
                .stream()
                .map(this::toDto)
                .toList();
        return ApiResult.success(dtos);
    }

    @Operation(summary = "查宿者下拉列表（仅查宿角色）")
    @GetMapping("/checkers")
    public ApiResult<List<CounselorDto>> listCheckers() {
        List<CounselorDto> dtos = counselorManagementService.listActiveCounselorsByRole("COUNSELOR")
                .stream()
                .map(this::toDto)
                .toList();
        return ApiResult.success(dtos);
    }

    @Operation(summary = "删除查宿者")
    @DeleteMapping("/{id}")
    public ApiResult<Void> deleteCounselor(@PathVariable String id) {
        counselorManagementService.deleteCounselor(id);
        return ApiResult.success();
    }

    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public ApiResult<Void> resetPassword(@PathVariable String id, @RequestBody ResetPasswordRequest request) {
        counselorManagementService.resetPassword(id, request.getNewPassword());
        return ApiResult.success();
    }

    @Operation(summary = "下载查宿者导入模板")
    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] templateData = importCounselorService.downloadTemplate();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("查宿账户导入模板.xlsx", StandardCharsets.UTF_8)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDisposition(disposition);
        return new ResponseEntity<>(templateData, headers, HttpStatus.OK);
    }

    @Operation(summary = "预览查宿者导入")
    @PostMapping("/preview")
    public ApiResult<List<ImportCounselorService.CounselorImportRow>> previewImport(
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            List<ImportCounselorService.CounselorImportRow> rows =
                    importCounselorService.previewImport(data);
            return ApiResult.success(rows);
        } catch (IOException e) {
            log.error("预览查宿者导入失败", e);
            return ApiResult.error("文件读取失败，请重试");
        }
    }

    @Operation(summary = "导入查宿者")
    @PostMapping("/import")
    public ApiResult<ImportCounselorService.ImportResult> importCounselors(
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] data = file.getBytes();
            ImportCounselorService.ImportResult result =
                    importCounselorService.importCounselors(data);
            return ApiResult.success(result);
        } catch (IOException e) {
            log.error("导入查宿者失败", e);
            return ApiResult.error("文件读取失败，请重试");
        }
    }

    private CounselorDto toDto(Counselor c) {
        return CounselorDto.builder()
                .id(c.getId())
                .name(c.getName())
                .username(c.getUsername())
                .studentNo(c.getStudentNo())
                .className(c.getClassName())
                .gender(c.getGender())
                .phone(c.getPhone())
                .department(c.getDepartment())
                .role(c.getRole())
                .isActive(c.getIsActive())
                .createdAt(c.getCreatedAt())
                .build();
    }

    @lombok.Data
    static class ResetPasswordRequest {
        private String newPassword;
    }
}