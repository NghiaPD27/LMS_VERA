package vera.lms.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vera.lms.dtos.EnrollmentDto;
import vera.lms.models.Enrollment;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {
    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "program.id", target = "programId")
    @Mapping(target = "programName", ignore = true)
    @Mapping(target = "progressPercent", ignore = true)
    @Mapping(target = "currentLessonNumber", ignore = true)
    @Mapping(target = "currentLessonName", ignore = true)
    @Mapping(target = "currentLessonStatus", ignore = true)
    @Mapping(target = "nextAction", ignore = true)
    EnrollmentDto.EnrollmentResponse toResponse(Enrollment enrollment);
}
