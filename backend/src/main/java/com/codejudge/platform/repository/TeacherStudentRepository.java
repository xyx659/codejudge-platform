package com.codejudge.platform.repository;

import com.codejudge.platform.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 教师端扩展的「学生」查询接口（JPA，对应 MySQL students 表）。
 *
 * <p>为什么单独建这个仓库、而不在团队的 {@link StudentRepository} 里加方法：
 * 教师端组卷需要「去重后的班级名列表」作为「目标班级」下拉选项，
 * 团队已有仓库没有这个方法。Spring Data 允许同一实体对应多个仓库接口，
 * 新建本接口即可，<b>无需改动团队写定的 {@link StudentRepository}</b>。</p>
 */
public interface TeacherStudentRepository extends JpaRepository<Student, Long> {

    /**
     * 查询所有去重后的班级名（按名称升序），忽略空班级。
     *
     * <p>用于组卷表单「目标班级」下拉框的数据源。</p>
     *
     * @return 去重排序后的班级名列表
     */
    @Query("select distinct s.className from Student s "
            + "where s.className is not null and s.className <> '' "
            + "order by s.className")
    List<String> findDistinctClassNames();
}
