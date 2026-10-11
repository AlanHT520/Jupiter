package net.alan.jupiter.web.mapper;

import net.alan.jupiter.domain.model.Student;
import net.alan.jupiter.web.dto.StudentDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    StudentMapper INSTANCE = Mappers.getMapper(StudentMapper.class);

    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "buildingId", source = "building.id")
    @Mapping(target = "checkStatus", ignore = true)
    @Mapping(target = "statusCode", ignore = true)
    @Mapping(target = "presetStatus", ignore = true)
    @Mapping(target = "statusSource", ignore = true)
    @Mapping(target = "remark", ignore = true)
    @Mapping(target = "defaultStatus", ignore = true)
    @Mapping(target = "lateReturnStatus", ignore = true)
    StudentDto toDto(Student student);
}