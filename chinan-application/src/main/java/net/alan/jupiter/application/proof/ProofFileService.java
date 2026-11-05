package net.alan.jupiter.application.proof;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
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
    private final CheckRecordRepository checkRecordRepository;

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
            String proofFilePath = localFileStorage.saveProof(checkRecordId, inputStream, ext);
            // 上传成功后自动关联到查寝记录（checkRecordId格式为 {taskId}_{studentId}）
            int idx = checkRecordId.indexOf('_');
            if (idx > 0 && idx < checkRecordId.length() - 1) {
                String taskId = checkRecordId.substring(0, idx);
                String studentId = checkRecordId.substring(idx + 1);
                List<CheckRecord> records = checkRecordRepository
                        .findByTaskIdAndStudentId(taskId, studentId);
                for (CheckRecord record : records) {
                    record.setProofFile(proofFilePath);
                    checkRecordRepository.save(record);
                }
                if (!records.isEmpty()) {
                    log.info("证明上传已自动关联查寝记录: taskId={}, studentId={}, path={}",
                            taskId, studentId, proofFilePath);
                }
            }

            return proofFilePath;
        } catch (SecurityException e) {
            log.warn("非法的证明文件记录ID: {}", checkRecordId);
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的记录ID");
        } catch (IOException e) {
            log.error("上传证明文件失败", e);
            throw new BizException(ErrorCode.FILE_UPLOAD_ERROR, "文件上传失败，请稍后再试");
        }
    }

    public byte[] downloadProof(String filePath) {
        try {
            return localFileStorage.readFile(filePath);
        } catch (SecurityException e) {
            log.warn("非法的证明文件路径: {}", filePath);
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的文件路径");
        } catch (IOException e) {
            log.error("读取证明文件失败: {}", filePath, e);
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
    }

    public void deleteProof(String filePath) {
        try {
            localFileStorage.deleteFile(filePath);
        } catch (SecurityException e) {
            log.warn("非法的证明文件路径: {}", filePath);
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的文件路径");
        } catch (IOException e) {
            log.error("删除证明文件失败: {}", filePath, e);
        }
    }

    public List<String> listProofs(String checkRecordId) {
        try {
            return localFileStorage.listProofs(checkRecordId);
        } catch (SecurityException e) {
            log.warn("非法的记录ID: {}", checkRecordId);
            throw new BizException(ErrorCode.BAD_REQUEST, "非法的记录ID");
        } catch (IOException e) {
            log.error("列出证明文件失败: {}", checkRecordId, e);
            throw new BizException(ErrorCode.FILE_NOT_FOUND);
        }
    }
}