package com.codejudge.platform.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 防作弊事件实体，对应 MongoDB 集合 {@code cheat_events}。
 *
 * <p>学生端答题页检测到「切屏 / 切页面」等异常行为时上报一条事件，
 * 教师端监考页据此统计次数并预警。属于 MVP 简化实现：只记录事件，
 * 不做证据截图、不做相似度比对。</p>
 */
@Document(collection = "cheat_events")
public class CheatEvent {

    /** 事件 ID（MongoDB 自动生成的字符串主键） */
    @Id
    private String id;

    /** 考试 ID（对应 exams._id） */
    private String examId;

    /** 学生 ID（对应 MySQL students.id） */
    private Long studentId;

    /** 事件类型：SWITCH_TAB（切屏）/ LEAVE_PAGE（切页面） */
    private String eventType;

    /** 事件发生时间 */
    private LocalDateTime occurredAt = LocalDateTime.now();

    public CheatEvent() {
    }

    public CheatEvent(String examId, Long studentId, String eventType) {
        this.examId = examId;
        this.studentId = studentId;
        this.eventType = eventType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
