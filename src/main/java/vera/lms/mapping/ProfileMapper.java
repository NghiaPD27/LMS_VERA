package vera.lms.mapping;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import vera.lms.dtos.UserDto;
import vera.lms.models.AccountAccess;
import vera.lms.models.EvaluatorProfile;
import vera.lms.models.StudentProfile;
import vera.lms.models.TeacherProfile;

@Mapper(componentModel = "spring")
public interface ProfileMapper {

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.accountAccess.status", target = "status")
    @Mapping(source = "user.accountAccess.mustChangePassword", target = "mustChangePassword")
    UserDto.StudentProfileResponse toStudentResponse(StudentProfile profile);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    UserDto.TeacherProfileResponse toTeacherResponse(TeacherProfile profile);

    @Mapping(source = "user.id", target = "userId")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    UserDto.EvaluatorProfileResponse toEvaluatorResponse(EvaluatorProfile profile);

    @Mapping(source = "user.id", target = "userId")
    UserDto.AccountAccessResponse toAccountAccessResponse(AccountAccess access);
}
