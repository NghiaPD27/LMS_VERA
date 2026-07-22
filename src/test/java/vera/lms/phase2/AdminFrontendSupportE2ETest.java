package vera.lms.phase2;

import org.junit.jupiter.api.Test;
import vera.lms.BaseIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminFrontendSupportE2ETest extends BaseIntegrationTest {

    private Long seedProgram(String name, String description) {
        jdbcTemplate.update("INSERT INTO programs (name, description) VALUES (?, ?)", name, description);
        return jdbcTemplate.queryForObject("SELECT id FROM programs WHERE name = ?", Long.class, name);
    }

    private Long seedStudent(String username, String email, String firstName, String lastName, String status) {
        Long studentId = createUser(username, email, "Password123", "STUDENT", true);
        createAccountAccess(studentId, status, false);
        createStudentProfile(studentId, firstName, lastName, "0900000000");
        return studentId;
    }

    private Long seedTeacher(String username, String email, String firstName) {
        Long teacherId = createUser(username, email, "Password123", "TEACHER", true);
        createAccountAccess(teacherId, "ACTIVE", false);
        createTeacherProfile(teacherId, firstName, "Teacher", "0900000001", "Bio");
        return teacherId;
    }

    private Long seedEnrollment(Long studentId, Long programId, String status) {
        jdbcTemplate.update("""
                INSERT INTO enrollments (student_id, program_id, status)
                VALUES (?, ?, ?)
                """, studentId, programId, status);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM enrollments WHERE student_id = ? AND program_id = ?",
                Long.class, studentId, programId);
    }

    @Test
    void testAdminCanSearchAndPageStudents() throws Exception {
        Long studentId = seedStudent("anna", "anna@vera.lms", "An", "Nguyen", "ACTIVE");
        seedStudent("binh", "binh@vera.lms", "Binh", "Tran", "SUSPENDED");
        seedTeacher("teacher_anna", "teacher.anna@vera.lms", "Anna");

        mockMvc.perform(get("/api/admin/students")
                        .param("keyword", "anna")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(studentId))
                .andExpect(jsonPath("$.content[0].username").value("anna"))
                .andExpect(jsonPath("$.content[0].email").value("anna@vera.lms"))
                .andExpect(jsonPath("$.content[0].firstName").value("An"))
                .andExpect(jsonPath("$.content[0].lastName").value("Nguyen"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("0900000000"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/admin/students")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void testAdminCanSearchAndPageTeachers() throws Exception {
        Long teacherId = seedTeacher("teacher_assign_anna", "assign.anna@vera.lms", "Anna");
        seedTeacher("teacher_assign_binh", "assign.binh@vera.lms", "Binh");
        seedStudent("student_not_teacher", "not.teacher@vera.lms", "Not", "Teacher", "ACTIVE");

        mockMvc.perform(get("/api/admin/teachers")
                        .param("keyword", "anna")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(teacherId))
                .andExpect(jsonPath("$.content[0].username").value("teacher_assign_anna"))
                .andExpect(jsonPath("$.content[0].email").value("assign.anna@vera.lms"))
                .andExpect(jsonPath("$.content[0].firstName").value("Anna"))
                .andExpect(jsonPath("$.content[0].lastName").value("Teacher"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("0900000001"))
                .andExpect(jsonPath("$.content[0].bio").value("Bio"))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/admin/teachers")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void testAdminCanViewTeacherDetail() throws Exception {
        Long teacherId = seedTeacher("teacher_detail_admin", "teacher.detail.admin@vera.lms", "Detail");
        Long studentId = seedStudent("student_not_teacher_detail", "student.not.teacher.detail@vera.lms", "Not", "Teacher", "ACTIVE");

        mockMvc.perform(get("/api/admin/teachers/" + teacherId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(teacherId))
                .andExpect(jsonPath("$.username").value("teacher_detail_admin"))
                .andExpect(jsonPath("$.email").value("teacher.detail.admin@vera.lms"))
                .andExpect(jsonPath("$.firstName").value("Detail"))
                .andExpect(jsonPath("$.lastName").value("Teacher"))
                .andExpect(jsonPath("$.phoneNumber").value("0900000001"))
                .andExpect(jsonPath("$.bio").value("Bio"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/teachers/" + studentId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }


    @Test
    void testAdminCanViewStudentDetailAndStudentEnrollments() throws Exception {
        Long studentId = seedStudent("student_detail", "detail@vera.lms", "Detail", "Student", "ACTIVE");
        Long teacherId = seedTeacher("teacher_detail", "teacher.detail@vera.lms", "Teacher");
        Long program1Id = seedProgram("English A1 Detail", "Beginner");
        Long program2Id = seedProgram("English A2 Detail", "Elementary");
        seedEnrollment(studentId, program1Id, "COMPLETED");
        seedEnrollment(studentId, program2Id, "ACTIVE");

        mockMvc.perform(get("/api/admin/students/" + studentId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId))
                .andExpect(jsonPath("$.username").value("student_detail"))
                .andExpect(jsonPath("$.firstName").value("Detail"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/admin/students/" + studentId + "/enrollments")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].studentId").value(studentId))
                .andExpect(jsonPath("$[0].studentName").value("Detail Student"))
                .andExpect(jsonPath("$[0].studentEmail").value("detail@vera.lms"))
                .andExpect(jsonPath("$[0].programName").exists())
                .andExpect(jsonPath("$[0].status").exists());

        mockMvc.perform(get("/api/admin/students/" + teacherId)
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testAdminCanListAndFilterEnrollments() throws Exception {
        Long student1Id = seedStudent("enroll_one", "enroll.one@vera.lms", "Enroll", "One", "ACTIVE");
        Long student2Id = seedStudent("enroll_two", "enroll.two@vera.lms", "Enroll", "Two", "ACTIVE");
        Long program1Id = seedProgram("Enrollment Program One", "Desc");
        Long program2Id = seedProgram("Enrollment Program Two", "Desc");
        seedEnrollment(student1Id, program1Id, "ACTIVE");
        seedEnrollment(student2Id, program2Id, "COMPLETED");

        mockMvc.perform(get("/api/admin/enrollments")
                        .param("studentId", "")
                        .param("programId", "")
                        .param("status", "")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].studentName").exists())
                .andExpect(jsonPath("$.content[0].studentEmail").exists())
                .andExpect(jsonPath("$.content[0].programName").exists())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/admin/enrollments")
                        .param("studentId", student1Id.toString())
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].studentId").value(student1Id));

        mockMvc.perform(get("/api/admin/enrollments")
                        .param("programId", program2Id.toString())
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].programId").value(program2Id));

        mockMvc.perform(get("/api/admin/enrollments")
                        .param("status", "COMPLETED")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].status").value("COMPLETED"));
    }

    @Test
    void testProgramListSupportsKeywordAndPagination() throws Exception {
        seedProgram("English Alpha", "Speaking");
        seedProgram("Business English", "Corporate beta");

        mockMvc.perform(get("/api/programs")
                        .param("keyword", "alpha")
                        .param("page", "0")
                        .param("size", "20")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("English Alpha"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/programs")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void testAdminSupportApisAreAdminOnly() throws Exception {
        seedStudent("student_user", "student@vera.lms", "Student", "User", "ACTIVE");

        mockMvc.perform(get("/api/admin/students")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/enrollments")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/teachers")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/teachers/1")
                        .header("Authorization", "Bearer student-token"))
                .andExpect(status().isForbidden());
    }
}
