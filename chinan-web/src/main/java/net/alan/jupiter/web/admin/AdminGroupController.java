package net.alan.jupiter.web.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.common.result.ApiResult;
import net.alan.jupiter.domain.model.Group;
import net.alan.jupiter.domain.repository.GroupRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "管理端-小组", description = "查寝小组接口")
@RestController
@RequestMapping("/admin-api/groups")
@RequiredArgsConstructor
public class AdminGroupController {

    private final GroupRepository groupRepository;

    @Operation(summary = "小组列表")
    @GetMapping
    @Transactional(readOnly = true)
    public ApiResult<List<GroupDto>> listGroups() {
        List<GroupDto> dtos = groupRepository.findAll().stream()
                .map(g -> GroupDto.builder()
                        .id(g.getId())
                        .name(g.getName())
                        .buildingId(g.getBuilding() != null ? g.getBuilding().getId() : null)
                        .build())
                .toList();
        return ApiResult.success(dtos);
    }

    @Data
    @Builder
    static class GroupDto {
        private String id;
        private String name;
        private String buildingId;
    }
}