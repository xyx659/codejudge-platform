package com.codejudge.platform.service;

import com.codejudge.platform.common.BadRequestException;
import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.ExamSubmitRequest;
import com.codejudge.platform.dto.ExamSubmitResult;
import com.codejudge.platform.dto.QuestionDetail;
import com.codejudge.platform.dto.QuestionSummary;
import com.codejudge.platform.dto.StudentExamDetail;
import com.codejudge.platform.dto.StudentExamQuestion;
import com.codejudge.platform.dto.StudentExamSummary;
import com.codejudge.platform.dto.StudentQuestionSubmission;
import com.codejudge.platform.dto.SubmissionRequest;
import com.codejudge.platform.dto.SubmissionResponse;
import com.codejudge.platform.dto.SubmissionResult;
import com.codejudge.platform.dto.SubmissionSummary;
import com.codejudge.platform.entity.CheatEvent;
import com.codejudge.platform.entity.Exam;
import com.codejudge.platform.entity.ExamQuestion;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
import com.codejudge.platform.repository.CheatEventRepository;
import com.codejudge.platform.repository.ExamRepository;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import org.bson.types.ObjectId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生端业务逻辑。
 *
 * <p>约定：控制器（Controller）只负责「收请求、返回结果」，真正的业务规则
 * 都写在 Service 里。这样逻辑集中在一处，方便维护和测试。</p>
 *
 * <p>这个类会随着后面几项任务逐步「长大」：现在只有题目列表，
 * 后面还会加入详情、提交、成绩查询等方法。</p>
 */
@Service
public class StudentService {

    /**
     * MongoTemplate 是 Spring Data 提供的 MongoDB 操作工具。
     *
     * <p>跟「写死查询的 Repository」不同，它允许我们用代码动态组装查询条件
     * （比如「难度传了才按难度筛选，没传就不筛」），最适合做这种灵活的列表查询。</p>
     */
    private final MongoTemplate mongoTemplate;

    /** 题目仓库：负责 MongoDB questions 集合的简单增删改查（如按 ID 查询） */
    private final QuestionRepository questionRepository;

    /** 考试仓库：负责 MongoDB exams 集合的查询（学生端按考试答题） */
    private final ExamRepository examRepository;

    /** 学生仓库：负责 MySQL students 表的查询（根据用户名找当前登录学生） */
    private final StudentRepository studentRepository;

    /** 提交摘要仓库：负责 MySQL submissions 表（判卷摘要） */
    private final SubmissionRepository submissionRepository;

    /** 提交明细仓库：负责 MongoDB submission_details 集合（源码、评测明细、AI 评审） */
    private final SubmissionDetailRepository submissionDetailRepository;

    /** 评测服务：负责触发代码评测 */
    private final JudgeService judgeService;

    /** 题目可见性索引：学生只能看到已发布试卷里的题目 */
    private final QuestionVisibilityIndex visibilityIndex;

    /** 防作弊事件仓库：学生端答题页上报切屏/切页面事件 */
    private final CheatEventRepository cheatEventRepository;

    /** 构造方法：Spring 启动时会自动把需要的仓库和服务传进来（这叫依赖注入） */
    public StudentService(MongoTemplate mongoTemplate,
                          QuestionRepository questionRepository,
                          ExamRepository examRepository,
                          StudentRepository studentRepository,
                          SubmissionRepository submissionRepository,
                          SubmissionDetailRepository submissionDetailRepository,
                          JudgeService judgeService,
                          QuestionVisibilityIndex visibilityIndex,
                          CheatEventRepository cheatEventRepository) {
        this.mongoTemplate = mongoTemplate;
        this.questionRepository = questionRepository;
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.judgeService = judgeService;
        this.visibilityIndex = visibilityIndex;
        this.cheatEventRepository = cheatEventRepository;
    }

    /**
     * 查询「已发布」的题目列表，支持分页 + 按难度/标签筛选。
     *
     * @param page       页码，从 0 开始（第 0 页 = 第一页）
     * @param size       每页条数
     * @param difficulty 按难度筛选，可空（如「简单」「中等」「困难」）
     * @param tag        按标签筛选，可空（如「数学」）
     * @return 分页结果：当前页的题目摘要 + 总条数
     */
    public PageResult<QuestionSummary> listQuestions(int page, int size,
                                                     String difficulty, String tag) {
        // 1. 组装查询条件。学生只能看到「已发布试卷里的题目」——这是固定条件。
        //    可见题目 ID 由 Redis 索引（QuestionVisibilityIndex）给出，这里按 ID 集合过滤。
        List<ObjectId> visibleIds = visibilityIndex.getVisibleIds().stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();
        Criteria criteria = Criteria.where("_id").in(visibleIds);

        // 2. 如果前端传了「难度」，就追加「难度必须等于这个值」的条件。
        //    isBlank() 用来判断字符串是不是 null、空串或纯空格。
        if (difficulty != null && !difficulty.isBlank()) {
            criteria = criteria.and("difficulty").is(difficulty);
        }

        // 3. 如果传了「标签」，就追加「tags 数组里包含这个标签」的条件。
        //    注意 tags 在 MongoDB 里是个数组，判断「数组里有没有某元素」要用 in。
        if (tag != null && !tag.isBlank()) {
            criteria = criteria.and("tags").in(tag);
        }

        // 4. 先统计符合条件的题目一共有多少条。
        //    注意：这里还没加分页，count 会统计「所有」满足条件的题目，而不是当前页。
        Query query = new Query(criteria);
        long total = mongoTemplate.count(query, Question.class);

        // 5. 再查「当前页」的数据：
        //    - 按创建时间倒序（新题目排前面）
        //    - skip 掉前面的 page*size 条，只取接下来 size 条
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Question> questions = mongoTemplate.find(query.with(pageable), Question.class);

        // 6. 算出当前学生在这页题目中已经提交过的题目 ID，用于前端标记「已提交」。
        //    先取当前页的题目 ID 列表，再一次性批量查提交记录，避免逐题查询（N+1）。
        Student student = currentStudent();
        List<String> questionIds = questions.stream()
                .map(Question::getId)
                .toList();
        Set<String> submittedIds = questionIds.isEmpty()
                ? Set.of()
                : submissionRepository
                        .findByStudentIdAndQuestionIdIn(student.getId(), questionIds).stream()
                        .map(Submission::getQuestionId)
                        .collect(Collectors.toSet());

        // 7. 把每道题目实体转成「摘要」（去掉测试用例等敏感字段），并带上「是否已提交」标记。
        List<QuestionSummary> list = questions.stream()
                .map(q -> QuestionSummary.from(q, submittedIds.contains(q.getId())))
                .toList();
        return new PageResult<>(list, page, size, total);
    }

    /**
     * 学生端「我的考试」列表：返回所有已发布（PUBLISHED）的考试。
     *
     * <p>学生看到的是<b>试卷</b>而非题库题目。每项带一个按当前时间算出的状态
     * （未开始 / 进行中 / 已结束），以及「是否已交卷」标记。</p>
     */
    public List<StudentExamSummary> listExams() {
        Student student = currentStudent();
        LocalDateTime now = LocalDateTime.now();
        List<Exam> exams = examRepository.findByStatus("PUBLISHED");
        List<StudentExamSummary> result = new ArrayList<StudentExamSummary>();
        for (Exam exam : exams) {
            if (!visibleByClass(exam, student)) {
                continue;
            }
            boolean submitted = submissionRepository
                    .findFirstByStudentIdAndExamId(student.getId(), exam.getId())
                    .isPresent();
            result.add(toSummary(exam, now, submitted));
        }
        return result;
    }

    /**
     * 学生端考试详情：进试卷答题 / 交卷后回看。
     *
     * <p>只有 {@code PUBLISHED} 状态的考试对学生可见；展开试卷内每道题的完整内容
     * （描述、方法名、语言、测试用例），已交卷时同时带回学生的源码与得分。</p>
     */
    public StudentExamDetail getExam(String examId) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new NotFoundException("考试不存在");
        }
        Student student = currentStudent();
        if (!visibleByClass(exam, student)) {
            throw new NotFoundException("考试不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        String status = timeStatus(exam, now);

        // 该学生在这场考试里的全部提交（已交卷时才有），按题目 ID 建映射
        boolean submitted = submissionRepository
                .findFirstByStudentIdAndExamId(student.getId(), examId).isPresent();
        Map<String, Submission> submissionByQuestion = new HashMap<String, Submission>();
        if (submitted) {
            for (Submission s : submissionRepository.findByStudentIdAndExamId(student.getId(), examId)) {
                submissionByQuestion.put(s.getQuestionId(), s);
            }
        }

        List<StudentExamQuestion> questions = new ArrayList<StudentExamQuestion>();
        if (exam.getQuestions() != null) {
            for (ExamQuestion eq : exam.getQuestions()) {
                Question q = questionRepository.findById(eq.getQuestionId()).orElse(null);
                // 标题/难度优先用组卷快照（组卷即冻结），其余内容取题库实时值
                String title = eq.getTitle() != null ? eq.getTitle() : (q != null ? q.getTitle() : null);
                String difficulty = eq.getDifficulty() != null ? eq.getDifficulty() : (q != null ? q.getDifficulty() : null);

                Submission sub = submissionByQuestion.get(eq.getQuestionId());
                String sourceCode = null;
                String judgeStatus = null;
                Integer myScore = null;
                if (sub != null) {
                    SubmissionDetail detail = submissionDetailRepository
                            .findBySubmissionIdAndStudentId(sub.getId(), student.getId())
                            .orElse(null);
                    sourceCode = detail == null ? null : detail.getSourceCode();
                    judgeStatus = sub.getJudgeStatus();
                    myScore = sub.getScore();
                }
                questions.add(new StudentExamQuestion(
                        eq.getQuestionId(),
                        title,
                        difficulty,
                        eq.getScore(),
                        q != null ? q.getDescription() : null,
                        q != null ? q.getMethodName() : null,
                        q != null ? q.getLanguage() : null,
                        q != null ? q.getTestCases() : List.of(),
                        sourceCode, judgeStatus, myScore));
            }
        }

        int totalScore = exam.getQuestions() == null ? 0
                : exam.getQuestions().stream()
                        .mapToInt(q -> q.getScore() == null ? 0 : q.getScore())
                        .sum();
        return new StudentExamDetail(
                exam.getId(), exam.getTitle(), exam.getDescription(), exam.getTargetClass(),
                exam.getStartTime(), exam.getEndTime(), exam.getDurationMinutes(),
                status, questions.size(), totalScore, submitted, questions);
    }

    /**
     * 整卷一次交卷：把试卷内各题的答案一次性落库，未作答的题记为「未作答」（0 分）。
     *
     * <p>交卷前做三道校验：考试必须已发布、必须在时间窗内（留 60 秒容错给前端
     * 倒计时到点自动交卷）、同一学生同一考试只允许交一次。</p>
     */
    public ExamSubmitResult submitExam(String examId, ExamSubmitRequest request) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        if (!"PUBLISHED".equals(exam.getStatus())) {
            throw new NotFoundException("考试不存在");
        }
        Student student = currentStudent();
        if (!visibleByClass(exam, student)) {
            throw new NotFoundException("考试不存在");
        }
        LocalDateTime now = LocalDateTime.now();

        // 时间窗校验：未开始不能交；已结束（留 60 秒容错）不能交
        if (exam.getStartTime() != null && now.isBefore(exam.getStartTime())) {
            throw new BadRequestException("考试尚未开始，不能交卷");
        }
        if (exam.getEndTime() != null && now.isAfter(exam.getEndTime().plusSeconds(60))) {
            throw new BadRequestException("考试已结束，无法交卷");
        }

        // 整卷一次交卷：已交过则拒绝
        if (submissionRepository.findFirstByStudentIdAndExamId(student.getId(), examId).isPresent()) {
            throw new BadRequestException("该考试已交卷，不能重复交卷");
        }

        // 前端答案按题目 ID 建映射（空白代码视为未作答）
        Map<String, String> answerMap = new HashMap<String, String>();
        if (request.answers() != null) {
            for (ExamSubmitRequest.Answer a : request.answers()) {
                answerMap.put(a.questionId(), a.sourceCode());
            }
        }

        List<ExamQuestion> questions = exam.getQuestions() == null ? List.of() : exam.getQuestions();
        int answered = 0;
        for (ExamQuestion eq : questions) {
            String sourceCode = answerMap.get(eq.getQuestionId());
            boolean answeredQ = sourceCode != null && !sourceCode.isBlank();
            if (answeredQ) {
                answered++;
            }

            Submission submission = new Submission(eq.getQuestionId(), student.getId(), examId);
            submission.setJudgeStatus(answeredQ ? "PENDING" : "UNANSWERED");
            submission.setScore(answeredQ ? null : 0);
            submission = submissionRepository.save(submission);

            SubmissionDetail detail = new SubmissionDetail();
            detail.setSubmissionId(submission.getId());
            detail.setStudentId(student.getId());
            detail.setExamId(examId);
            detail.setQuestionId(eq.getQuestionId());
            detail.setSourceCode(answeredQ ? sourceCode : "");
            detail.setJudgeStatus(answeredQ ? "PENDING" : "UNANSWERED");
            detail.setScore(answeredQ ? null : 0);
            submissionDetailRepository.save(detail);

            if (answeredQ) {
                judgeService.trigger(submission.getId());
            }
        }

        return new ExamSubmitResult(examId, now, answered, questions.size());
    }

    /**
     * 上报防作弊事件（切屏 / 切页面）。
     *
     * <p>学生端答题页检测到离开页面/失焦时上报；仅记录事件供监考统计，
     * 不做拦截。校验考试存在且对本班可见，避免乱写考试 ID。</p>
     */
    public void reportCheat(String examId, String eventType) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("考试不存在"));
        Student student = currentStudent();
        if (!visibleByClass(exam, student)) {
            throw new NotFoundException("考试不存在");
        }
        // 只认两类事件，其余归一为切屏
        String type = "LEAVE_PAGE".equals(eventType) ? "LEAVE_PAGE" : "SWITCH_TAB";
        cheatEventRepository.save(new CheatEvent(examId, student.getId(), type));
    }

    /**
     * 学生所在班级能否看到这场考试。
     *
     * <p>考试未指定目标班级（targetClass 为空）时全员可见；否则只对本班学生可见。</p>
     */
    private boolean visibleByClass(Exam exam, Student student) {
        String target = exam.getTargetClass();
        if (target == null || target.isBlank()) {
            return true;
        }
        return target.equals(student.getClassName());
    }

    /** 按当前时间判断考试处于哪个时间窗阶段：未开始 / 进行中 / 已结束 */
    private String timeStatus(Exam exam, LocalDateTime now) {
        if (exam.getStartTime() == null || exam.getEndTime() == null) {
            return "ENDED";
        }
        if (now.isBefore(exam.getStartTime())) {
            return "NOT_STARTED";
        }
        if (now.isAfter(exam.getEndTime())) {
            return "ENDED";
        }
        return "ONGOING";
    }

    /** 把考试实体转成列表摘要（算题数、总分、时间窗状态） */
    private StudentExamSummary toSummary(Exam exam, LocalDateTime now, boolean submitted) {
        int count = exam.getQuestions() == null ? 0 : exam.getQuestions().size();
        int totalScore = exam.getQuestions() == null ? 0
                : exam.getQuestions().stream()
                        .mapToInt(q -> q.getScore() == null ? 0 : q.getScore())
                        .sum();
        return new StudentExamSummary(
                exam.getId(), exam.getTitle(), exam.getDescription(), exam.getTargetClass(),
                exam.getStartTime(), exam.getEndTime(), exam.getDurationMinutes(),
                timeStatus(exam, now), count, totalScore, submitted);
    }

    /**
     * 查询单个题目的完整详情（含题目描述和测试用例说明）。
     *
     * @param id 题目 ID（MongoDB 的 _id）
     * @return 题目详情
     */
    public QuestionDetail getQuestion(String id) {
        // 1. 按 ID 查题目；查不到就抛出「资源不存在」异常，前端会收到 404。
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("题目不存在"));

        // 2. 安全考虑：学生只能看「已发布试卷里的题目」。
        //    不在可见索引里的题目，对前端表现得和「不存在」一样（同样返回 404），
        //    避免让学生猜出或探出未发布的内容。
        if (!visibilityIndex.isVisible(question.getId())) {
            throw new NotFoundException("题目不存在");
        }

        // 3. 转成详情 DTO 返回（详情里才带题目描述和测试用例）。
        return QuestionDetail.from(question);
    }

    /**
     * 提交代码：先在两个库各写一条记录，再触发评测。
     *
     * @param request 提交请求（题目 ID + 源码）
     * @return 提交结果（提交 ID + 判卷状态）
     */
    public SubmissionResponse submit(SubmissionRequest request) {
        // 1. 拿到当前登录的学生（从安全上下文里取出用户名，再到学生表查）
        Student student = currentStudent();

        // 2. 校验题目存在且对学生可见（在已发布试卷里），防止对不可见题提交
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("题目不存在"));
        if (!visibilityIndex.isVisible(question.getId())) {
            throw new NotFoundException("题目不存在");
        }

        // 3. 每题只允许提交一次：已提交过则直接拒绝，避免重复判卷刷分。
        if (submissionRepository
                .findFirstByStudentIdAndQuestionId(student.getId(), question.getId())
                .isPresent()) {
            throw new BadRequestException("该题目已提交过，不能重复提交");
        }

        // 4. 先写 MySQL 的「提交摘要」：谁、哪道题、状态 PENDING（待评测）。
        //    注意：save 之后才会生成自增 id，所以要把返回值接住。
        Submission submission = new Submission(question.getId(), student.getId());
        submission.setJudgeStatus("PENDING");
        submission = submissionRepository.save(submission);

        // 5. 再写 MongoDB 的「提交明细」：完整源码，状态同样 PENDING。
        SubmissionDetail detail = new SubmissionDetail();
        detail.setSubmissionId(submission.getId()); // 关键：关联 MySQL 提交记录的 id
        detail.setStudentId(student.getId());
        detail.setQuestionId(question.getId());
        detail.setSourceCode(request.sourceCode());
        detail.setJudgeStatus("PENDING");
        submissionDetailRepository.save(detail);

        // 6. 触发评测（目前是占位；评测引擎接入后这里才真正执行代码）
        judgeService.trigger(submission.getId());

        // 7. 立刻返回提交 ID 和状态，前端展示「提交成功，评测中」
        return new SubmissionResponse(submission.getId(), submission.getJudgeStatus());
    }

    /**
     * 从安全上下文里取「当前登录的学生」。
     *
     * <p>登录时 JWT 里存了用户名，认证过滤器会把它放进安全上下文；
     * 这里取出用户名，再去学生表查出完整的学生信息（含数字 id）。</p>
     */
    private Student currentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        return studentRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("学生不存在"));
    }

    /**
     * 查询当前学生的提交记录列表（分页，按提交时间倒序）。
     *
     * <p>提交记录存在 MySQL，但每条记录的「题目标题」在 MongoDB，
     * 所以这里要跨两个库手动「拼接」一次，把标题补上再返回。</p>
     *
     * @param page 页码，从 0 开始
     * @param size 每页条数
     * @return 分页的提交记录摘要（含题目标题）
     */
    public PageResult<SubmissionSummary> listSubmissions(int page, int size) {
        // 1. 拿到当前登录的学生
        Student student = currentStudent();

        // 2. 查当前学生的提交，按提交时间倒序、分页
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Submission> submissionPage = submissionRepository.findByStudentId(student.getId(), pageable);

        // 3. 收集这一页所有提交涉及到的题目 ID
        List<String> questionIds = submissionPage.getContent().stream()
                .map(Submission::getQuestionId)
                .toList();

        // 4. 一次性批量查这些题目，建立「题目 ID -> 标题」的映射。
        //    先批量查、再建映射，是为了避免对每条提交都单独查一次题目（N+1 问题）。
        //    如果一条提交都没有，questionIds 为空，直接给空映射，不对空列表发查询。
        Map<String, String> titleMap = questionIds.isEmpty()
                ? Map.of()
                : questionRepository.findAllById(questionIds).stream()
                        .collect(Collectors.toMap(Question::getId, Question::getTitle));

        // 5. 把每条提交转成摘要，并补上题目标题
        List<SubmissionSummary> list = submissionPage.getContent().stream()
                .map(s -> SubmissionSummary.from(s, titleMap.get(s.getQuestionId())))
                .toList();

        // 6. 装进分页结果返回（总条数来自 JPA 的分页对象）
        return new PageResult<>(list, page, size, submissionPage.getTotalElements());
    }

    /**
     * 查询某次提交的成绩与 AI 评审反馈。
     *
     * @param submissionId 提交记录 ID（MySQL 的 submissions.id）
     * @return 提交结果（得分、测试用例结果、AI 评审）
     */
    public SubmissionResult getSubmissionResult(Long submissionId) {
        // 1. 拿到当前登录的学生
        Student student = currentStudent();

        // 2. 校验这条提交存在、且属于当前学生。
        //    安全：不能让学生查看别人的成绩，所以「不属于自己」和「不存在」都返回 404。
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("提交记录不存在"));
        if (!submission.getStudentId().equals(student.getId())) {
            throw new NotFoundException("提交记录不存在");
        }

        // 3. 查 MongoDB 里的提交明细（含测试结果和 AI 评审）。
        //    同时带学生 ID 一起查，双保险确保拿到的是自己的明细。
        SubmissionDetail detail = submissionDetailRepository
                .findBySubmissionIdAndStudentId(submissionId, student.getId())
                .orElseThrow(() -> new NotFoundException("评测结果尚未生成"));

        // 4. 补上题目标题（方便前端展示）
        String title = questionRepository.findById(submission.getQuestionId())
                .map(Question::getTitle)
                .orElse(null);

        // 5. 组装结果返回
        return SubmissionResult.from(detail, title, submissionId);
    }

    /**
     * 查询当前学生对某道题的提交（提交后回看题目和自己的回答）。
     *
     * <p>未提交过时返回 {@code null}，前端据此判断是否允许提交；
     * 已提交则返回源码 + 评测结果，让答题页切换到「只读回看」模式。</p>
     *
     * @param questionId 题目 ID
     * @return 提交视图（含源码与评测结果）；未提交过返回 {@code null}
     */
    public StudentQuestionSubmission getQuestionSubmission(String questionId) {
        // 1. 拿到当前登录的学生
        Student student = currentStudent();

        // 2. 查该学生对这道题已有的一条提交；没有就返回 null
        Submission submission = submissionRepository
                .findFirstByStudentIdAndQuestionId(student.getId(), questionId)
                .orElse(null);
        if (submission == null) {
            return null;
        }

        // 3. 查 MongoDB 的提交明细（含源码、测试结果与 AI 评审）
        SubmissionDetail detail = submissionDetailRepository
                .findBySubmissionIdAndStudentId(submission.getId(), student.getId())
                .orElse(null);

        // 4. 补题目标题，方便前端展示
        String title = questionRepository.findById(questionId)
                .map(Question::getTitle)
                .orElse(null);

        // 5. 明细尚未生成（极端情况）：仍返回摘要信息，源码与评测留空，前端可降级展示
        if (detail == null) {
            return new StudentQuestionSubmission(
                    submission.getId(), questionId, title,
                    submission.getJudgeStatus(), submission.getScore(),
                    null, null, null);
        }
        return StudentQuestionSubmission.from(detail, title, submission.getId());
    }
}
