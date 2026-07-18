package vera.lms.mapping;

import org.mapstruct.Mapper;
import vera.lms.dtos.ProgramDto;
import vera.lms.models.Program;

@Mapper(componentModel = "spring")
public interface ProgramMapper {
    ProgramDto.ProgramResponse toResponse(Program program);
}
