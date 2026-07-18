package vera.lms.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vera.lms.dtos.EnrollmentDto;
import vera.lms.models.Enrollment;

@Mapper(componentModel = "spring")
public interface EnrollmentMapper {
    @Mapping(source = "student.id", target = "studentId")
    @Mapping(source = "program.id", target = "programId")
    EnrollmentDto.EnrollmentResponse toResponse(Enrollment enrollment);
}
