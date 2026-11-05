package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.common.result.PageResult;
import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.domain.repository.CheckRecordRepository;
import net.alan.jupiter.domain.repository.StudentRepository;
import net.alan.jupiter.infrastructure.persistence.specification.StudentSpecification;
import net.alan.jupiter.web.dto.StudentDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "管理端-名册", description = "管理端学生名册接口")
@RestController
@RequestMapping("/admin-api/roster")
@RequiredArgsConstructor
public class AdminRosterController {

    private final StudentRepository studentRepository;
    private final CheckRecordRepository checkRecordRepository;
    private final net.alan.jupiter.web.mapper.StudentMapper studentMapper;

    @Operation(summary = "分页查询学生列表")
    @GetMapping("/students")
    public ApiResult<PageResult<StudentDto>> listStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String className,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Specification<Student> spec = StudentSpecification.searchBy(keyword, buildingId, className);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("studentNo").ascending());
        Page<Student> studentPage = studentRepository.findAll(spec, pageRequest);

        List<StudentDto> dtos = studentPage.getContent().stream()
                .map(studentMapper::toDto)
                .toList();

        return ApiResult.success(PageResult.of(dtos, studentPage.getTotalElements(), page, size));
    }

    @Operation(summary = "获取学生详情")
    @GetMapping("/students/{id}")
    public ApiResult<StudentDto> getStudent(@PathVariable String id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.STUDENT_NOT_FOUND));
        return ApiResult.success(studentMapper.toDto(student));
    }

    @Operation(summary = "停用学生（保留历史查寝记录）")
    @DeleteMapping("/students/{id}")
    @Transactional
    public ApiResult<Void> deleteStudent(@PathVariable String id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.STUDENT_NOT_FOUND));
        // 软删除：仅标记离校，保留查寝与报表历史
        student.setIsActive(false);
        studentRepository.save(student);
        return ApiResult.success();
    }
}