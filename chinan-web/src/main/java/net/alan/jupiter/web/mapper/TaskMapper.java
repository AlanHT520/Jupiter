package net.alan.jupiter.web.mapper;

import net.alan.jupiter.domain.model.Task;
import net.alan.jupiter.web.dto.TaskDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface TaskMapper {

    TaskMapper INSTANCE = Mappers.getMapper(TaskMapper.class);

    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "floorId", source = "floor.id")
    @Mapping(target = "assignedCounselorId", source = "assignedCounselor.id")
    @Mapping(target = "assignedCounselorName", source = "assignedCounselorName")
    TaskDto toDto(Task task);
}