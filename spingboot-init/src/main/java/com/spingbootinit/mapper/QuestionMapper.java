package com.spingbootinit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spingbootinit.model.entity.Question;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface QuestionMapper extends BaseMapper<Question> {
    @Update("UPDATE question SET submit_num = COALESCE(submit_num, 0) + 1, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{questionId} AND is_delete = 0")
    int incrementSubmitNum(@Param("questionId") long questionId);
}
