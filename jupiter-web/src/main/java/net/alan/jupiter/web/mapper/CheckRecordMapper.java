package net.alan.jupiter.web.mapper;

import net.alan.jupiter.domain.model.CheckRecord;
import net.alan.jupiter.web.dto.CheckRecordDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface CheckRecordMapper {

    CheckRecordMapper INSTANCE = Mappers.getMapper(CheckRecordMapper.class);

    @Mapping(target = "taskId", source = "task.id")
    @Mapping(target = "roomId", source = "room.id")
    @Mapping(target = "studentId", source = "student.id")
    @Mapping(target = "checkStatus", expression = "java(record.getCheckStatus() != null ? record.getCheckStatus().name() : null)")
    CheckRecordDto toDto(CheckRecord record);
}