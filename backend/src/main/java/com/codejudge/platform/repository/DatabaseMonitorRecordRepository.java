package com.codejudge.platform.repository;

import com.codejudge.platform.entity.DatabaseMonitorRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

/**
 * 数据库监控历史快照数据访问接口。
 */
public interface DatabaseMonitorRecordRepository
        extends JpaRepository<DatabaseMonitorRecord, Long> {

    Page<DatabaseMonitorRecord> findAllByOrderByCollectedAtDesc(Pageable pageable);

    Page<DatabaseMonitorRecord> findByCollectedAtBetweenOrderByCollectedAtDesc(
            LocalDateTime startTime,
            LocalDateTime endTime,
            Pageable pageable);
}
