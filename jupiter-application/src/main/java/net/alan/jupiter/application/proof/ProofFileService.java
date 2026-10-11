package net.alan.jupiter.application.proof;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.infrastructure.storage.LocalFileStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProofFileService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".pdf", ".doc", ".docx"
    );

    private final LocalFileStorage localFileStorage;

    public String uploadProof(java.io.InputStream inputStream, String originalFilename, String checkRecordId) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        if (ext.isEmpty() || !ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR,
                    "不支持的文件类型: " + ext + "，仅允许: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        try {
            return localFileStorage.saveProof(checkRecordId, inputStream, ext);
        } catch (IOException e) {
            log.error("上传证明文件失败", e);
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR, "文件上传失败，请稍后再试");
        }
    }

    public byte[] downloadProof(String filePath) {
        try {
            return localFileStorage.readFile(filePath);
        } catch (IOException e) {
            log.error("读取证明文件失败: {}", filePath, e);
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    public void deleteProof(String filePath) {
        try {
            localFileStorage.deleteFile(filePath);
        } catch (IOException e) {
            log.error("删除证明文件失败: {}", filePath, e);
        }
    }

    public List<String> listProofs(String checkRecordId) {
        try {
            return localFileStorage.listProofs(checkRecordId);
        } catch (IOException e) {
            log.error("列出证明文件失败: {}", checkRecordId, e);
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
    }
}