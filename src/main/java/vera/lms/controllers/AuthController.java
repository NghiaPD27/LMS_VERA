package vera.lms.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vera.lms.dtos.AuthDto.*;
import vera.lms.dtos.UserDto.StudentProfileResponse;
import vera.lms.dtos.UserDto.UserResponse;
import vera.lms.mapping.ProfileMapper;
import vera.lms.mapping.UserMapper;
import vera.lms.models.StudentProfile;
import vera.lms.models.User;
import vera.lms.services.AuthService;
import vera.lms.services.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;

    @Autowired
    public AuthController(
            AuthService authService,
            UserService userService,
            UserMapper userMapper,
            ProfileMapper profileMapper) {
        this.authService = authService;
        this.userService = userService;
        this.userMapper = userMapper;
        this.profileMapper = profileMapper;
    }

    @PostMapping("/register")
    public ResponseEntity<StudentProfileResponse> register(@RequestBody @Valid RegisterStudentRequest request) {
        StudentProfile profile = userService.registerStudent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(profileMapper.toStudentResponse(profile));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal User currentUser) {
        authService.changePassword(request, currentUser);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(userMapper.toResponse(currentUser));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody RefreshRequest request,
            @AuthenticationPrincipal User currentUser) {
        authService.logout(request, currentUser != null ? currentUser.getUsername() : null);
        return ResponseEntity.ok().build();
    }
}
