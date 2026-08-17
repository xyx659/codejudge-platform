package com.codejudge.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用定时任务，用于数据库监控历史快照采集。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
