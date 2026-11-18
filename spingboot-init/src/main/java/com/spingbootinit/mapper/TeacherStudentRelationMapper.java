package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.TeacherStudentRelation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TeacherStudentRelationMapper extends BaseMapper<TeacherStudentRelation> {

    /** 老师名下已建立关系的学生数 */
    @Select("""
            SELECT COUNT(*)
            FROM teacher_student_relation
            WHERE teacher_id = #{teacherId}
              AND status = 1
              AND is_delete = 0
            """)
    long countActiveStudents(@Param("teacherId") Long teacherId);

    /** 学生已建立关系的指导老师 id 列表 */
    @Select("""
            SELECT teacher_id
            FROM teacher_student_relation
            WHERE student_id = #{studentId}
              AND status = 1
              AND is_delete = 0
            """)
    List<Long> selectActiveTeacherIds(@Param("studentId") Long studentId);
}
