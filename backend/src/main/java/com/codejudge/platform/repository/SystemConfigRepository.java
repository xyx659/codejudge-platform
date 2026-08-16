package com.codejudge.platform.repository;

import com.codejudge.platform.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 系统配置数据访问接口。
 */
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKey(String configKey);

    List<SystemConfig> findByConfigKeyIn(Collection<String> configKeys);

    List<SystemConfig> findAllByOrderByIdAsc();
}
