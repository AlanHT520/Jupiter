package net.alan.jupiter.infrastructure.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
public class LocalFileStorage {

    @Value("${Chinan.storage.upload-dir:uploads}")
    private String uploadDir;

    @Value("${Chinan.storage.proof-dir:proofs}")
    private String proofDir;

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private Path getUploadsRoot() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private void validatePath(Path path) {
        Path resolved = path.toAbsolutePath().normalize();
        Path root = getUploadsRoot();
        if (!resolved.startsWith(root)) {
            throw new SecurityException("路径穿越检测: " + path);
        }
    }

    /**
     * 校验路径组件不包含路径穿越字符，防止 ../ 攻击。
     */
    private void validatePathComponent(String component) {
        if (component == null || component.isEmpty()) {
            throw new SecurityException("路径组件为空");
        }
        if (component.contains("..") || component.contains("/") || component.contains("\\")) {
            throw new SecurityException("路径组件包含非法字符: " + component);
        }
    }

    /**
     * 规范化扩展名：仅允许 {@code .[A-Za-z0-9]{1,8}}，非法值统一回退为 .jpg，
     * 防止客户端传入带路径分隔符的"扩展名"。
     */
    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".jpg";
        }
        String ext = extension.startsWith(".") ? extension : "." + extension;
        return ext.matches("\\.[A-Za-z0-9]{1,8}") ? ext.toLowerCase() : ".jpg";
    }

    public String saveProof(String recordId, java.io.InputStream inputStream, String extension) throws IOException {
        validatePathComponent(recordId);
        Path proofPath = Path.of(uploadDir, proofDir, recordId).toAbsolutePath().normalize();
        validatePath(proofPath);
        Files.createDirectories(proofPath);

        String fileName = UUID.randomUUID().toString().replace("-", "") + normalizeExtension(extension);
        Path filePath = proofPath.resolve(fileName);
        validatePath(filePath);

        long totalBytes = 0;
        byte[] buffer = new byte[8192];
        try (java.io.OutputStream os = Files.newOutputStream(filePath)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                totalBytes += bytesRead;
                if (totalBytes > MAX_FILE_SIZE) {
                    try { Files.deleteIfExists(filePath); } catch (IOException ignored) {}
                    throw new IOException("文件超过大小限制: " + MAX_FILE_SIZE / 1024 / 1024 + "MB");
                }
                os.write(buffer, 0, bytesRead);
            }
        }

        return proofDir + "/" + recordId + "/" + fileName;
    }

    public byte[] readFile(String filePath) throws IOException {
        Path path = Path.of(uploadDir, filePath).toAbsolutePath().normalize();
        validatePath(path);
        if (!Files.exists(path)) {
            throw new IOException("文件不存在: " + filePath);
        }
        if (Files.size(path) > MAX_FILE_SIZE) {
            throw new IOException("文件超过大小限制: " + MAX_FILE_SIZE / 1024 / 1024 + "MB");
        }
        return Files.readAllBytes(path);
    }

    public void deleteFile(String filePath) throws IOException {
        Path path = Path.of(uploadDir, filePath).toAbsolutePath().normalize();
        validatePath(path);
        Files.deleteIfExists(path);
    }

    public List<String> listProofs(String recordId) throws IOException {        validatePathComponent(recordId);
        Path proofPath = Path.of(uploadDir, proofDir, recordId).toAbsolutePath().normalize();
        validatePath(proofPath);
        if (!Files.exists(proofPath)) {
            return List.of();
        }

        List<String> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(proofPath)) {
            stream.map(Path::getFileName)
                    .map(Path::toString)
                    .forEach(files::add);
        }
        return files;
    }
}