package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.QuestionSolutionLike;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface QuestionSolutionLikeMapper extends BaseMapper<QuestionSolutionLike> {
    @Delete("DELETE FROM question_solution_like WHERE solution_id = #{solutionId}")
    int deleteBySolutionId(@Param("solutionId") long solutionId);
}
