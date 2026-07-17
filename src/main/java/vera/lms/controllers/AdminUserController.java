package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.UserDto.*;
import vera.lms.mapping.ProfileMapper;
import vera.lms.mapping.UserMapper;
import vera.lms.models.EvaluatorProfile;
import vera.lms.models.StudentProfile;
import vera.lms.models.TeacherProfile;
import vera.lms.models.User;
import vera.lms.services.UserService;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final UserService userService;
    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;

    @Autowired
    public AdminUserController(UserService userService, UserMapper userMapper, ProfileMapper profileMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @PostMapping("/students")
    public ResponseEntity<StudentProfileResponse> createStudent(@RequestBody @Valid CreateStudentRequest request) {
        StudentProfile profile = userService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMapper.toStudentResponse(profile));
    }

    @PostMapping("/teachers")
    public ResponseEntity<TeacherProfileResponse> createTeacher(@RequestBody @Valid CreateTeacherRequest request) {
        TeacherProfile profile = userService.createTeacher(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMapper.toTeacherResponse(profile));
    }

    @PostMapping("/evaluators")
    public ResponseEntity<EvaluatorProfileResponse> createEvaluator(@RequestBody @Valid CreateEvaluatorRequest request) {
        EvaluatorProfile profile = userService.createEvaluator(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMapper.toEvaluatorResponse(profile));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody @Valid UpdateUserRequest request,
            @AuthenticationPrincipal User currentUser) {
        User user = userService.updateUser(id, request, currentUser);
        return ResponseEntity.ok(userMapper.toResponse(user));
    }

    @PatchMapping("/users/{id}/extend")
    public ResponseEntity<AccountAccessResponse> extendAccount(
            @PathVariable Long id,
            @RequestBody @Valid ExtendAccountRequest request) {
        var access = userService.extendAccount(id, request.months());
        return ResponseEntity.ok(profileMapper.toAccountAccessResponse(access));
    }
}
