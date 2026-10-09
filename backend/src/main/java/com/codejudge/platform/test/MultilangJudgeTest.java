package com.codejudge.platform.test;

import com.codejudge.platform.CodejudgeApplication;
import com.codejudge.platform.repository.QuestionRepository;
import com.codejudge.platform.repository.StudentRepository;
import com.codejudge.platform.repository.SubmissionDetailRepository;
import com.codejudge.platform.repository.SubmissionRepository;
import com.codejudge.platform.service.JudgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Arrays;

/**
 * 多语言判题冒烟测试入口：启动无 Web 的 Spring 上下文（真实 MySQL / MongoDB / Docker），
 * 逐题逐语言跑真实判题链路。
 *
 * <p>用法（在 backend 目录下，依赖与主应用一致）：
 * <pre>
 * mvn -q compile
 * mvn -q exec:java -Dexec.mainClass=com.codejudge.platform.test.MultilangJudgeTest \
 *     -Dexec.args="/home/taylor/question-bank-import.json /home/taylor/multilang-judge/test/solutions"
 * </pre>
 * 或用 IDE 直接运行本类 main，传入 [题库json路径] [参考解目录]。</p>
 */
public class MultilangJudgeTest {

    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "/home/taylor/question-bank-import.json";
        String solutionsDir = args.length > 1 ? args[1] : "/home/taylor/multilang-judge/test/solutions";
        // 其余参数透传给 Spring（如 --spring.data.mongodb.uri=...）
        String[] springArgs = args.length > 2 ? Arrays.copyOfRange(args, 2, args.length) : new String[0];

        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CodejudgeApplication.class)
                .web(WebApplicationType.NONE)
                .run(springArgs);

        try {
            MultilangTestRunner runner = new MultilangTestRunner(
                    ctx.getBean(QuestionRepository.class),
                    ctx.getBean(SubmissionRepository.class),
                    ctx.getBean(SubmissionDetailRepository.class),
                    ctx.getBean(StudentRepository.class),
                    ctx.getBean(JudgeService.class),
                    ctx.getBean(ObjectMapper.class));
            runner.run(jsonPath, solutionsDir);
        } finally {
            ctx.close();
        }
    }
}
