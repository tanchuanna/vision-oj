package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.QuestionSolution;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface QuestionSolutionMapper extends BaseMapper<QuestionSolution> {
    @Update("UPDATE question_solution SET like_count = GREATEST(0, like_count + #{delta}) WHERE id = #{id} AND is_delete = 0")
    int changeLikeCount(@Param("id") long id, @Param("delta") int delta);

    @Update("UPDATE question_solution SET comment_count = GREATEST(0, comment_count + #{delta}) WHERE id = #{id} AND is_delete = 0")
    int changeCommentCount(@Param("id") long id, @Param("delta") int delta);

    @Delete("DELETE FROM question_solution WHERE id = #{id}")
    int hardDelete(@Param("id") long id);
}
