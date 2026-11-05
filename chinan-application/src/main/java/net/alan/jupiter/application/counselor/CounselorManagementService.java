package net.alan.jupiter.application.counselor;

import lombok.RequiredArgsConstructor;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CounselorManagementService {

    private final CounselorRepository counselorRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<Counselor> listCounselors() {
        return counselorRepository.findByRoleNotIgnoreCase("ADMIN");
    }

    @Transactional(readOnly = true)
    public List<Counselor> listActiveCounselorsByRole(String role) {
        return counselorRepository.findByIsActiveTrueAndRoleIgnoreCase(role);
    }

    @Transactional
    public Counselor createCounselor(String name, String username, String password, String phone) {
        if (name == null || name.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "姓名不能为空");
        }
        if (username == null || username.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录账号不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new BizException(ErrorCode.BAD_REQUEST, "登录密码至少 6 位");
        }
        String normalizedUsername = username.trim();
        if (counselorRepository.existsByUsername(normalizedUsername)) {
            throw new BizException(ErrorCode.USERNAME_DUPLICATE, "登录账号已存在");
        }
        String normalizedPhone = phone == null || phone.isBlank() ? null : phone.trim();
        if (normalizedPhone != null && counselorRepository.findByPhone(normalizedPhone).isPresent()) {
            throw new BizException(ErrorCode.CONFLICT, "该手机号已被其他账号使用");
        }

        Counselor counselor = Counselor.builder()
                .name(name.trim())
                .username(normalizedUsername)
                .password(passwordEncoder.encode(password))
                .phone(normalizedPhone)
                .role("COUNSELOR")
                .isActive(true)
                .build();
        log.info("新增查宿者账号: {}", normalizedUsername);
        return counselorRepository.save(counselor);
    }

    @Transactional
    public void deleteCounselor(String id) {
        Counselor counselor = counselorRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "查宿者不存在"));
        if ("ADMIN".equalsIgnoreCase(counselor.getRole())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "不能删除管理员账户");
        }
        // 软删除：停用账号并让已签发的 token 立即失效，保留历史任务与查寝记录中的归属信息
        counselor.setIsActive(false);
        counselor.setTokenVersion(
                (counselor.getTokenVersion() != null ? counselor.getTokenVersion() : 0L) + 1);
        counselorRepository.save(counselor);
        log.info("已停用查宿者账号: {}", counselor.getUsername());
    }

    @Transactional
    public void resetPassword(String id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "新密码不能为空");
        }
        Counselor counselor = counselorRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "查宿者不存在"));
        counselor.setPassword(passwordEncoder.encode(newPassword));
        counselor.setTokenVersion(
                (counselor.getTokenVersion() != null ? counselor.getTokenVersion() : 0L) + 1);
        counselorRepository.save(counselor);
    }

    @Transactional(readOnly = true)
    public byte[] exportAccounts(List<Counselor> counselors) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("工作人员账户");
            Row headerRow = sheet.createRow(0);
            String[] headers = {"姓名", "用户名", "学号", "班级", "性别", "手机号", "部门", "状态", "密码"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (Counselor c : counselors) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(c.getName() != null ? c.getName() : "");
                row.createCell(1).setCellValue(c.getUsername() != null ? c.getUsername() : "");
                row.createCell(2).setCellValue(c.getStudentNo() != null ? c.getStudentNo() : "");
                row.createCell(3).setCellValue(c.getClassName() != null ? c.getClassName() : "");
                row.createCell(4).setCellValue(c.getGender() != null ? c.getGender() : "");
                row.createCell(5).setCellValue(c.getPhone() != null ? c.getPhone() : "");
                row.createCell(6).setCellValue(c.getDepartment() != null ? c.getDepartment() : "");
                row.createCell(7).setCellValue(Boolean.TRUE.equals(c.getIsActive()) ? "启用" : "禁用");
                row.createCell(8).setCellValue(c.getPassword() != null ? c.getPassword() : "");
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                int width = Math.min(sheet.getColumnWidth(i) + 512, 15 * 256);
                sheet.setColumnWidth(i, width);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            log.error("导出工作人员账户失败", e);
            throw new BizException(ErrorCode.EXPORT_ERROR, "导出工作人员账户失败，请稍后再试");
        }
    }
}