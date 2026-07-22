package vera.lms.services;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vera.lms.dtos.QuizDto.QuizAttemptResponse;
import vera.lms.dtos.QuizDto.QuizOptionRequest;
import vera.lms.dtos.QuizDto.QuizOptionResponse;
import vera.lms.dtos.QuizDto.QuizQuestionRequest;
import vera.lms.dtos.QuizDto.QuizQuestionResponse;
import vera.lms.dtos.QuizDto.QuizResponse;
import vera.lms.dtos.QuizDto.SubmitQuizAnswerRequest;
import vera.lms.dtos.QuizDto.SubmitQuizAttemptRequest;
import vera.lms.dtos.QuizDto.UpsertQuizRequest;
import vera.lms.enums.EnrollmentStatus;
import vera.lms.enums.LessonProgressStatus;
import vera.lms.enums.LessonStatus;
import vera.lms.enums.RoleName;
import vera.lms.exceptions.BadRequestException;
import vera.lms.exceptions.ConflictException;
import vera.lms.exceptions.ForbiddenException;
import vera.lms.exceptions.ResourceNotFoundException;
import vera.lms.models.Lesson;
import vera.lms.models.LessonVideo;
import vera.lms.models.Quiz;
import vera.lms.models.QuizAnswer;
import vera.lms.models.QuizAttempt;
import vera.lms.models.QuizOption;
import vera.lms.models.QuizQuestion;
import vera.lms.models.StudentLessonProgress;
import vera.lms.models.User;
import vera.lms.models.VideoProgress;
import vera.lms.repositories.EnrollmentRepository;
import vera.lms.repositories.LessonRepository;
import vera.lms.repositories.LessonVideoRepository;
import vera.lms.repositories.QuizAttemptRepository;
import vera.lms.repositories.QuizRepository;
import vera.lms.repositories.StudentLessonProgressRepository;
import vera.lms.repositories.VideoProgressRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final LessonRepository lessonRepository;
    private final LessonVideoRepository lessonVideoRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final StudentLessonProgressRepository lessonProgressRepository;
    private final EnrollmentRepository enrollmentRepository;

    public QuizService(
            QuizRepository quizRepository,
            QuizAttemptRepository quizAttemptRepository,
            LessonRepository lessonRepository,
            LessonVideoRepository lessonVideoRepository,
            VideoProgressRepository videoProgressRepository,
            StudentLessonProgressRepository lessonProgressRepository,
            EnrollmentRepository enrollmentRepository) {
        this.quizRepository = quizRepository;
        this.quizAttemptRepository = quizAttemptRepository;
        this.lessonRepository = lessonRepository;
        this.lessonVideoRepository = lessonVideoRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.lessonProgressRepository = lessonProgressRepository;
        this.enrollmentRepository = enrollmentRepository;
    }

    public QuizResponse upsertQuiz(Long lessonId, UpsertQuizRequest request) {
        Lesson lesson = lessonRepository.findByIdAndStatusNot(lessonId, LessonStatus.ARCHIVED)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id " + lessonId));

        validateQuizRequest(request);

        Quiz quiz = quizRepository.findByLessonId(lessonId)
                .orElseGet(() -> Quiz.builder().lesson(lesson).build());
        quiz.setTitle(request.title().trim());
        quiz.replaceQuestions(buildQuestions(request.questions()));
        return toQuizResponse(quizRepository.save(quiz), true);
    }

    @Transactional(readOnly = true)
    public QuizResponse getQuizForLesson(Long lessonId, User currentUser) {
        Quiz quiz = quizRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found for lesson id " + lessonId));

        boolean includeCorrectAnswers = currentUser != null
                && currentUser.getRole() != null
                && currentUser.getRole().getName() == RoleName.ADMIN;
        if (!includeCorrectAnswers) {
            ensureStudentCanTakeQuiz(quiz, currentUser);
        }
        return toQuizResponse(quiz, includeCorrectAnswers);
    }

    public QuizAttemptResponse startAttempt(Long quizId, User student) {
        Quiz quiz = quizRepository.findWithQuestionsById(quizId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found with id " + quizId));
        StudentLessonProgress lessonProgress = ensureStudentCanTakeQuiz(quiz, student);

        int attemptNumber = (int) quizAttemptRepository.countByQuizIdAndStudentId(quizId, student.getId()) + 1;
        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .student(student)
                .attemptNumber(attemptNumber)
                .build();
        attempt = quizAttemptRepository.save(attempt);
        return toAttemptResponse(attempt, lessonProgress);
    }

    public QuizAttemptResponse submitAttempt(Long attemptId, User student, SubmitQuizAttemptRequest request) {
        QuizAttempt attempt = quizAttemptRepository.findWithDetailsById(attemptId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz attempt not found with id " + attemptId));

        if (attempt.getStudent() == null || !attempt.getStudent().getId().equals(student.getId())) {
            throw new ForbiddenException("Quiz attempt does not belong to current student");
        }
        if (attempt.isSubmitted()) {
            throw new ConflictException("Quiz attempt has already been submitted");
        }

        Quiz quiz = attempt.getQuiz();
        StudentLessonProgress lessonProgress = ensureStudentCanTakeQuiz(quiz, student);
        List<QuizAnswer> answers = gradeAnswers(quiz, request.answers());

        int totalQuestions = quiz.getQuestions().size();
        int correctCount = (int) answers.stream().filter(QuizAnswer::isCorrect).count();
        int scorePercent = totalQuestions == 0 ? 0 : (int) Math.floor((correctCount * 100.0) / totalQuestions);

        attempt.replaceAnswers(answers);
        attempt.setCorrectCount(correctCount);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setScorePercent(scorePercent);
        attempt.setSubmitted(true);
        attempt.setSubmittedAt(Instant.now());
        attempt = quizAttemptRepository.save(attempt);

        if (lessonProgress.getStatus() == LessonProgressStatus.QUIZ_AVAILABLE) {
            lessonProgress.setStatus(LessonProgressStatus.WAITING_FOR_TEACHER);
            lessonProgress = lessonProgressRepository.save(lessonProgress);
        }

        return toAttemptResponse(attempt, lessonProgress);
    }

    public void deleteQuiz(Long lessonId) {
        Quiz quiz = quizRepository.findByLessonId(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz not found for lesson id " + lessonId));
        if (quizAttemptRepository.existsByQuizId(quiz.getId())) {
            throw new ConflictException("Cannot delete quiz because it already has attempts");
        }
        quizRepository.delete(quiz);
    }

    @Transactional(readOnly = true)
    public List<QuizAttemptResponse> getAdminLessonQuizAttempts(Long lessonId) {
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson not found with id " + lessonId);
        }
        return quizAttemptRepository.findByQuizLessonIdOrderByStartedAtDesc(lessonId).stream()
                .map(this::toAttemptResponseForAdmin)
                .toList();
    }

    private void validateQuizRequest(UpsertQuizRequest request) {
        Set<String> normalizedQuestions = new HashSet<>();
        int questionPosition = 1;
        for (QuizQuestionRequest question : request.questions()) {
            String normalizedQuestion = question.questionText().trim().toLowerCase();
            if (!normalizedQuestions.add(normalizedQuestion)) {
                throw new BadRequestException("Duplicate quiz question at position " + questionPosition);
            }
            if (question.options().size() < 2) {
                throw new BadRequestException("Question must have at least two options");
            }
            long correctOptions = question.options().stream().filter(QuizOptionRequest::correct).count();
            if (correctOptions != 1) {
                throw new BadRequestException("Each question must have exactly one correct option");
            }
            questionPosition++;
        }
    }

    private List<QuizQuestion> buildQuestions(List<QuizQuestionRequest> requests) {
        List<QuizQuestion> questions = new ArrayList<>();
        int questionPosition = 1;
        for (QuizQuestionRequest questionRequest : requests) {
            QuizQuestion question = QuizQuestion.builder()
                    .questionText(questionRequest.questionText().trim())
                    .position(questionPosition++)
                    .build();
            question.replaceOptions(buildOptions(questionRequest.options()));
            questions.add(question);
        }
        return questions;
    }

    private List<QuizOption> buildOptions(List<QuizOptionRequest> requests) {
        List<QuizOption> options = new ArrayList<>();
        int optionPosition = 1;
        for (QuizOptionRequest optionRequest : requests) {
            options.add(QuizOption.builder()
                    .optionText(optionRequest.optionText().trim())
                    .correct(optionRequest.correct())
                    .position(optionPosition++)
                    .build());
        }
        return options;
    }

    private StudentLessonProgress ensureStudentCanTakeQuiz(Quiz quiz, User student) {
        if (student == null || student.getRole() == null || student.getRole().getName() != RoleName.STUDENT) {
            throw new ForbiddenException("Only students can take quiz");
        }

        Lesson lesson = quiz.getLesson();
        if (lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new ForbiddenException("Lesson is not published");
        }

        boolean isEnrolled = enrollmentRepository.existsAccessibleEnrollment(
                student.getId(), lesson.getProgram().getId(), EnrollmentStatus.ACTIVE, Instant.now());
        if (!isEnrolled) {
            throw new ForbiddenException("Course enrollment is expired or unavailable");
        }

        StudentLessonProgress lessonProgress = lessonProgressRepository
                .findByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseThrow(() -> new ForbiddenException("Lesson is locked"));
        if (lessonProgress.getStatus() == LessonProgressStatus.LOCKED
                || lessonProgress.getStatus() == LessonProgressStatus.VIDEO_IN_PROGRESS) {
            throw new ForbiddenException("Video must be completed before taking quiz");
        }

        LessonVideo lessonVideo = lessonVideoRepository.findByLessonId(lesson.getId())
                .orElseThrow(() -> new ForbiddenException("Video must be completed before taking quiz"));
        VideoProgress videoProgress = videoProgressRepository
                .findByStudentIdAndLessonVideoId(student.getId(), lessonVideo.getId())
                .orElseThrow(() -> new ForbiddenException("Video must be completed before taking quiz"));
        if (!videoProgress.isCompleted()) {
            throw new ForbiddenException("Video must be completed before taking quiz");
        }

        return lessonProgress;
    }

    private List<QuizAnswer> gradeAnswers(Quiz quiz, List<SubmitQuizAnswerRequest> answerRequests) {
        Map<Long, QuizQuestion> questionsById = new HashMap<>();
        for (QuizQuestion question : quiz.getQuestions()) {
            questionsById.put(question.getId(), question);
        }

        if (answerRequests.size() != questionsById.size()) {
            throw new BadRequestException("Submission must answer every quiz question");
        }

        Set<Long> answeredQuestionIds = new HashSet<>();
        List<QuizAnswer> answers = new ArrayList<>();
        for (SubmitQuizAnswerRequest answerRequest : answerRequests) {
            QuizQuestion question = questionsById.get(answerRequest.questionId());
            if (question == null) {
                throw new BadRequestException("Question does not belong to this quiz: " + answerRequest.questionId());
            }
            if (!answeredQuestionIds.add(question.getId())) {
                throw new BadRequestException("Question answered more than once: " + question.getId());
            }

            QuizOption selectedOption = question.getOptions().stream()
                    .filter(option -> option.getId().equals(answerRequest.selectedOptionId()))
                    .findFirst()
                    .orElseThrow(() -> new BadRequestException("Selected option does not belong to question: " + answerRequest.selectedOptionId()));

            answers.add(QuizAnswer.builder()
                    .question(question)
                    .selectedOption(selectedOption)
                    .correct(selectedOption.isCorrect())
                    .build());
        }
        return answers;
    }

    private QuizResponse toQuizResponse(Quiz quiz, boolean includeCorrectAnswers) {
        List<QuizQuestionResponse> questions = quiz.getQuestions().stream()
                .map(question -> new QuizQuestionResponse(
                        question.getId(),
                        question.getQuestionText(),
                        question.getPosition(),
                        question.getOptions().stream()
                                .map(option -> new QuizOptionResponse(
                                        option.getId(),
                                        option.getOptionText(),
                                        option.getPosition(),
                                        includeCorrectAnswers ? option.isCorrect() : null))
                                .toList()))
                .toList();
        return new QuizResponse(quiz.getId(), quiz.getLesson().getId(), quiz.getTitle(), questions);
    }

    private QuizAttemptResponse toAttemptResponse(QuizAttempt attempt, StudentLessonProgress lessonProgress) {
        Integer bestScore = quizAttemptRepository.findBestScorePercent(
                attempt.getQuiz().getId(), attempt.getStudent().getId());
        return new QuizAttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getLesson().getId(),
                attempt.getStudent().getId(),
                attempt.getAttemptNumber(),
                attempt.isSubmitted(),
                attempt.getCorrectCount(),
                attempt.getTotalQuestions(),
                attempt.getScorePercent(),
                bestScore,
                lessonProgress.getStatus().name(),
                attempt.getStartedAt(),
                attempt.getSubmittedAt());
    }

    private QuizAttemptResponse toAttemptResponseForAdmin(QuizAttempt attempt) {
        Integer bestScore = quizAttemptRepository.findBestScorePercent(
                attempt.getQuiz().getId(), attempt.getStudent().getId());
        return new QuizAttemptResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getQuiz().getLesson().getId(),
                attempt.getStudent().getId(),
                attempt.getAttemptNumber(),
                attempt.isSubmitted(),
                attempt.getCorrectCount(),
                attempt.getTotalQuestions(),
                attempt.getScorePercent(),
                bestScore,
                null,
                attempt.getStartedAt(),
                attempt.getSubmittedAt());
    }
}
