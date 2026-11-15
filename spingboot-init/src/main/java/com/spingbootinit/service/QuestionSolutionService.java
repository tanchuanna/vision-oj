package com.spingbootinit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.spingbootinit.model.dto.question.solution.SolutionOfficialRequest;
import com.spingbootinit.model.dto.question.solution.SolutionPageRequest;
import com.spingbootinit.model.dto.question.solution.SolutionReviewRequest;
import com.spingbootinit.model.dto.question.solution.SolutionSaveRequest;
import com.spingbootinit.model.entity.QuestionSolution;
import com.spingbootinit.model.vo.question.solution.SolutionDetailVO;
import com.spingbootinit.model.vo.question.solution.SolutionListItemVO;

public interface QuestionSolutionService extends IService<QuestionSolution> {
    Page<SolutionListItemVO> pagePublic(SolutionPageRequest request);
    Page<SolutionListItemVO> pageAdmin(SolutionPageRequest request, long adminId);
    SolutionDetailVO getAdminDetail(long id, long adminId);
    SolutionDetailVO getPublicDetail(long id, Long viewerId);
    SolutionDetailVO getMine(long questionId, long userId);
    long saveSolution(SolutionSaveRequest request, long userId);
    boolean submitReview(long id, long userId);
    boolean deleteMine(long id, long userId);
    boolean review(SolutionReviewRequest request, long adminId);
    boolean setOfficial(SolutionOfficialRequest request, long adminId);
}
