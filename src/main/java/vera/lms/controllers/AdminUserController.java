package vera.lms.controllers;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.EnrollmentDto.AdminEnrollmentResponse;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.UserDto.*;
import vera.lms.mapping.ProfileMapper;
import vera.lms.mapping.UserMapper;
import vera.lms.models.EvaluatorProfile;
import vera.lms.models.StudentProfile;
import vera.lms.models.TeacherProfile;
import vera.lms.models.User;
import vera.lms.services.EnrollmentService;
import vera.lms.services.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    private final UserService userService;
    private final EnrollmentService enrollmentService;
    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;

    @Autowired
    public AdminUserController(
            UserService userService,
            EnrollmentService enrollmentService,
            UserMapper userMapper,
            ProfileMapper profileMapper) {
        this.userService = userService;
        this.enrollmentService = enrollmentService;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @PostMapping("/students")
    public ResponseEntity<StudentProfileResponse> createStudent(@RequestBody @Valid CreateStudentRequest request) {
        StudentProfile profile = userService.createStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMapper.toStudentResponse(profile));
    }

    @GetMapping("/students")
    public ResponseEntity<PageResponse<AdminStudentResponse>> getStudents(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return ResponseEntity.ok(userService.getAdminStudents(keyword, page, size));
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<AdminStudentResponse> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getAdminStudent(id));
    }

    @GetMapping("/students/{id}/enrollments")
    public ResponseEntity<List<AdminEnrollmentResponse>> getStudentEnrollments(@PathVariable Long id) {
        return ResponseEntity.ok(enrollmentService.getAdminStudentEnrollments(id));
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
}
