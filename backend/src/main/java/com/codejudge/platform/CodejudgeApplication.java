package com.codejudge.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 应用启动入口。
 *
 * <p>数智化编程考核与智能分析系统后端，基于 Spring Boot 3 构建，
 * 采用混合存储：MySQL 存用户与提交元数据，MongoDB 存题目与提交明细。</p>
 */
@SpringBootApplication
public class CodejudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodejudgeApplication.class, args);
    }
}
