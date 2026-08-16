package com.codejudge.platform.service;

import com.codejudge.platform.common.NotFoundException;
import com.codejudge.platform.common.PageResult;
import com.codejudge.platform.dto.QuestionDetail;
import com.codejudge.platform.dto.QuestionSummary;
import com.codejudge.platform.dto.SubmissionRequest;
import com.codejudge.platform.dto.SubmissionResponse;
import com.codejudge.platform.dto.SubmissionResult;
import com.codejudge.platform.dto.SubmissionSummary;
import com.codejudge.platform.entity.Question;
import com.codejudge.platform.entity.Student;
import com.codejudge.platform.entity.Submission;
import com.codejudge.platform.entity.SubmissionDetail;
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

import java.util.List;
import java.util.Map;
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

    /** 学生仓库：负责 MySQL students 表的查询（根据用户名找当前登录学生） */
    private final StudentRepository studentRepository;

    /** 提交摘要仓库：负责 MySQL submissions 表（判卷摘要） */
    private final SubmissionRepository submissionRepository;

    /** 提交明细仓库：负责 MongoDB submission_details 集合（源码、评测明细、AI 评审） */
    private final SubmissionDetailRepository submissionDetailRepository;

    /** 评测服务：负责触发代码评测 */
    private final JudgeService judgeService;

    /** 构造方法：Spring 启动时会自动把需要的仓库和服务传进来（这叫依赖注入） */
    public StudentService(MongoTemplate mongoTemplate,
                          QuestionRepository questionRepository,
                          StudentRepository studentRepository,
                          SubmissionRepository submissionRepository,
                          SubmissionDetailRepository submissionDetailRepository,
                          JudgeService judgeService) {
        this.mongoTemplate = mongoTemplate;
        this.questionRepository = questionRepository;
        this.studentRepository = studentRepository;
        this.submissionRepository = submissionRepository;
        this.submissionDetailRepository = submissionDetailRepository;
        this.judgeService = judgeService;
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
        // 1. 组装查询条件。学生只能看到「已发布」的题目，这是固定条件。
        Criteria criteria = Criteria.where("published").is(true);

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

        // 6. 把每道题目实体转成「摘要」（去掉测试用例等敏感字段），装进分页结果返回。
        List<QuestionSummary> list = questions.stream()
                .map(QuestionSummary::from)
                .toList();
        return new PageResult<>(list, page, size, total);
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

        // 2. 安全考虑：学生只能看「已发布」的题目。
        //    未发布的题目，对前端表现得和「不存在」一样（同样返回 404），
        //    避免让学生猜出或探出草稿题的存在。
        if (!Boolean.TRUE.equals(question.getPublished())) {
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

        // 2. 校验题目存在且已发布，防止对不存在的题或草稿题提交
        Question question = questionRepository.findById(request.questionId())
                .orElseThrow(() -> new NotFoundException("题目不存在"));
        if (!Boolean.TRUE.equals(question.getPublished())) {
            throw new NotFoundException("题目不存在");
        }

        // 3. 先写 MySQL 的「提交摘要」：谁、哪道题、状态 PENDING（待评测）。
        //    注意：save 之后才会生成自增 id，所以要把返回值接住。
        Submission submission = new Submission(question.getId(), student.getId());
        submission.setJudgeStatus("PENDING");
        submission = submissionRepository.save(submission);

        // 4. 再写 MongoDB 的「提交明细」：完整源码，状态同样 PENDING。
        SubmissionDetail detail = new SubmissionDetail();
        detail.setSubmissionId(submission.getId()); // 关键：关联 MySQL 提交记录的 id
        detail.setStudentId(student.getId());
        detail.setQuestionId(question.getId());
        detail.setSourceCode(request.sourceCode());
        detail.setJudgeStatus("PENDING");
        submissionDetailRepository.save(detail);

        // 5. 触发评测（目前是占位；评测引擎接入后这里才真正执行代码）
        judgeService.trigger(submission.getId());

        // 6. 立刻返回提交 ID 和状态，前端展示「提交成功，评测中」
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
}
