package vera.lms.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vera.lms.dtos.LessonDto;
import vera.lms.models.Lesson;

@Mapper(componentModel = "spring")
public interface LessonMapper {
    @Mapping(source = "program.id", target = "programId")
    @Mapping(target = "lessonProgressStatus", ignore = true)
    @Mapping(target = "locked", ignore = true)
    @Mapping(target = "hasVideo", ignore = true)
    @Mapping(target = "videoStatus", ignore = true)
    @Mapping(target = "videoDurationSeconds", ignore = true)
    @Mapping(target = "hasQuiz", ignore = true)
    @Mapping(target = "questionCount", ignore = true)
    LessonDto.LessonResponse toResponse(Lesson lesson);
}
