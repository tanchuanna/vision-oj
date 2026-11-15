package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.QuestionSolutionComment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

public interface QuestionSolutionCommentMapper extends BaseMapper<QuestionSolutionComment> {
    @Delete("DELETE FROM question_solution_comment WHERE solution_id = #{solutionId}")
    int deleteBySolutionId(@Param("solutionId") long solutionId);
}
