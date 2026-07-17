package vera.lms.mapping;

import org.mapstruct.Mapper;
import vera.lms.dtos.UserDto;
import vera.lms.models.Role;
import vera.lms.models.User;

@Mapper(componentModel = "spring", uses = { ProfileMapper.class })
public interface UserMapper {

    UserDto.UserResponse toResponse(User user);

    default String mapRole(Role role) {
        if (role == null) {
            return null;
        }
        return role.getName().name();
    }
}
