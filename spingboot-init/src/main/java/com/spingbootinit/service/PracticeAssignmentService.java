package com.spingbootinit.service;

import com.spingbootinit.model.dto.teaching.PracticeAssignRequest;
import com.spingbootinit.model.vo.teaching.PracticeAssignResultVO;
import com.spingbootinit.model.vo.teaching.PracticeAssignmentVO;

import java.util.List;

/**
 * 老师一键布置练习与练习进度查询。
 */
public interface PracticeAssignmentService {
    PracticeAssignResultVO assign(long operatorId, String operatorRole, PracticeAssignRequest request);

    List<PracticeAssignmentVO> listForTeacher(long operatorId, String operatorRole, Long studentId);

    List<PracticeAssignmentVO> listForStudent(long studentId);

    void delete(long operatorId, String operatorRole, long assignmentId);
}
