package net.alan.jupiter.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.application.proof.ProofFileService;
import net.alan.jupiter.common.result.ApiResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Tag(name = "APP-证明", description = "PE端证明材料接口")
@RestController
@RequestMapping("/api/proofs")
@RequiredArgsConstructor
@Slf4j
public class AppProofController {

    private final ProofFileService proofFileService;

    @Operation(summary = "上传证明材料")
    @PostMapping("/upload")
    public ApiResult<String> uploadProof(
            @RequestParam("file") MultipartFile file,
            @RequestParam String checkRecordId) {
        try {
            String path = proofFileService.uploadProof(
                    file.getInputStream(), file.getOriginalFilename(), checkRecordId);
            return ApiResult.success(path);
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return ApiResult.error("文件上传失败，请重试");
        }
    }

    @Operation(summary = "下载证明材料")
    @GetMapping("/download")
    public ResponseEntity<byte[]> downloadProof(@RequestParam String filePath) {
        byte[] data = proofFileService.downloadProof(filePath);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filePath.substring(filePath.lastIndexOf("/") + 1) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(data);
    }

    @Operation(summary = "查看证明材料列表")
    @GetMapping("/list/{checkRecordId}")
    public ApiResult<List<String>> listProofs(@PathVariable String checkRecordId) {
        return ApiResult.success(proofFileService.listProofs(checkRecordId));
    }
}