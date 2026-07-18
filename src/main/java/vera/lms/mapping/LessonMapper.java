package vera.lms.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vera.lms.dtos.LessonDto;
import vera.lms.models.Lesson;

@Mapper(componentModel = "spring")
public interface LessonMapper {
    @Mapping(source = "program.id", target = "programId")
    LessonDto.LessonResponse toResponse(Lesson lesson);
}
