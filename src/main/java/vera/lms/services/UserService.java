package vera.lms.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.AuthDto.RegisterStudentRequest;
import vera.lms.dtos.PageDto.PageResponse;
import vera.lms.dtos.UserDto.*;
import vera.lms.enums.AccountStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.*;
import vera.lms.repositories.*;
import vera.lms.utils.PaginationUtils;

import java.util.List;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final EvaluatorProfileRepository evaluatorProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountAccessService accountAccessService;

    @Autowired
    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            EvaluatorProfileRepository evaluatorProfileRepository,
            PasswordEncoder passwordEncoder,
            AccountAccessService accountAccessService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.evaluatorProfileRepository = evaluatorProfileRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountAccessService = accountAccessService;
    }

    public StudentProfile createStudent(CreateStudentRequest request) {
        validateUniqueUsernameAndEmail(request.username(), request.email());

        Role role = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Role STUDENT not found"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(role)
                .build();

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .build();

        AccountAccess access = accountAccessService.createDefaultAccess(user);

        user.setStudentProfile(profile);
        user.setAccountAccess(access);

        userRepository.save(user);
        return profile;
    }

    public StudentProfile registerStudent(RegisterStudentRequest request) {
        validateUniqueUsernameAndEmail(request.username(), request.email());

        Role role = roleRepository.findByName(RoleName.STUDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Role STUDENT not found"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(role)
                .build();

        StudentProfile profile = StudentProfile.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .build();

        AccountAccess access = accountAccessService.createSelfRegisteredStudentAccess(user);

        user.setStudentProfile(profile);
        user.setAccountAccess(access);

        userRepository.save(user);
        return profile;
    }

    public TeacherProfile createTeacher(CreateTeacherRequest request) {
        validateUniqueUsernameAndEmail(request.username(), request.email());

        Role role = roleRepository.findByName(RoleName.TEACHER)
                .orElseThrow(() -> new ResourceNotFoundException("Role TEACHER not found"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(role)
                .build();

        TeacherProfile profile = TeacherProfile.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .bio(request.bio())
                .build();

        AccountAccess access = accountAccessService.createDefaultAccess(user);

        user.setTeacherProfile(profile);
        user.setAccountAccess(access);

        userRepository.save(user);
        return profile;
    }

    public EvaluatorProfile createEvaluator(CreateEvaluatorRequest request) {
        validateUniqueUsernameAndEmail(request.username(), request.email());

        Role role = roleRepository.findByName(RoleName.EVALUATOR)
                .orElseThrow(() -> new ResourceNotFoundException("Role EVALUATOR not found"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .enabled(true)
                .role(role)
                .build();

        EvaluatorProfile profile = EvaluatorProfile.builder()
                .user(user)
                .firstName(request.firstName())
                .lastName(request.lastName())
                .phoneNumber(request.phoneNumber())
                .build();

        AccountAccess access = accountAccessService.createDefaultAccess(user);

        user.setEvaluatorProfile(profile);
        user.setAccountAccess(access);

        userRepository.save(user);
        return profile;
    }

    public User updateUser(Long id, UpdateUserRequest request, User currentUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id " + id));

        // Prevent admin self-demotion
        if (currentUser != null && id.equals(currentUser.getId())) {
            if (request.enabled() != null && !request.enabled()) {
                throw new BadRequestException("Cannot disable your own account");
            }
            if (request.status() != null && (request.status().equalsIgnoreCase("SUSPENDED") || request.status().equalsIgnoreCase("EXPIRED"))) {
                throw new BadRequestException("Cannot suspend or expire your own account");
            }
        }

        // Email conflict check
        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new ConflictException("Email already in use");
            }
            user.setEmail(request.email());
        }

        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }

        if (request.status() != null) {
            AccountAccess access = user.getAccountAccess();
            if (access == null) {
                access = accountAccessService.createDefaultAccess(user);
                user.setAccountAccess(access);
            }
            try {
                access.setStatus(AccountStatus.valueOf(request.status().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid account status: " + request.status());
            }
        }

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStudentResponse> getAdminStudents(String keyword, Integer page, Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<User> students = userRepository.searchByRoleAndStudentKeyword(
                RoleName.STUDENT, normalizeKeyword(keyword), pageable);
        List<AdminStudentResponse> content = students.getContent().stream()
                .map(this::toAdminStudentResponse)
                .toList();
        return new PageResponse<>(
                content,
                students.getTotalElements(),
                students.getTotalPages(),
                students.getNumber(),
                students.getSize());
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTeacherResponse> getAdminTeachers(String keyword, Integer page, Integer size) {
        Pageable pageable = PaginationUtils.createPageable(page, size, Sort.by("id").descending());
        Page<User> teachers = userRepository.searchByRoleAndTeacherKeyword(
                RoleName.TEACHER, normalizeKeyword(keyword), pageable);
        List<AdminTeacherResponse> content = teachers.getContent().stream()
                .map(this::toAdminTeacherResponse)
                .toList();
        return new PageResponse<>(
                content,
                teachers.getTotalElements(),
                teachers.getTotalPages(),
                teachers.getNumber(),
                teachers.getSize());
    }

    @Transactional(readOnly = true)
    public AdminTeacherResponse getAdminTeacher(Long id) {
        User teacher = userRepository.findByIdAndRoleName(id, RoleName.TEACHER)
                .orElseThrow(() -> new ResourceNotFoundException("Teacher not found with id " + id));
        return toAdminTeacherResponse(teacher);
    }

    @Transactional(readOnly = true)
    public AdminStudentResponse getAdminStudent(Long id) {
        return toAdminStudentResponse(getStudentUser(id));
    }

    @Transactional(readOnly = true)
    public User getStudentUser(Long id) {
        return userRepository.findByIdAndRoleName(id, RoleName.STUDENT)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with id " + id));
    }

    private void validateUniqueUsernameAndEmail(String username, String email) {
        if (userRepository.existsByUsername(username)) {
            throw new ConflictException("Username is already taken");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("Email is already taken");
        }
    }

    private AdminStudentResponse toAdminStudentResponse(User user) {
        StudentProfile profile = user.getStudentProfile();
        AccountAccess access = user.getAccountAccess();
        return new AdminStudentResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                profile != null ? profile.getFirstName() : null,
                profile != null ? profile.getLastName() : null,
                profile != null ? profile.getPhoneNumber() : null,
                user.isEnabled(),
                access != null && access.getStatus() != null ? access.getStatus().name() : null);
    }

    private AdminTeacherResponse toAdminTeacherResponse(User user) {
        TeacherProfile profile = user.getTeacherProfile();
        AccountAccess access = user.getAccountAccess();
        return new AdminTeacherResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                profile != null ? profile.getFirstName() : null,
                profile != null ? profile.getLastName() : null,
                profile != null ? profile.getPhoneNumber() : null,
                profile != null ? profile.getBio() : null,
                user.isEnabled(),
                access != null && access.getStatus() != null ? access.getStatus().name() : null);
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }
        return keyword.trim();
    }
}
