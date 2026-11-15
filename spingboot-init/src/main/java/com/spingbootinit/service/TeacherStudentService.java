package com.spingbootinit.service;

import com.spingbootinit.model.vo.teaching.TeacherStudentRelationVO;

import java.util.List;

public interface TeacherStudentService {
    void bind(long operatorUserId, long teacherId, long studentId);
    void unbind(long operatorUserId, long teacherId, long studentId);
    List<Long> activeStudentIds(long teacherId);
    boolean canTeacherAccessStudent(long teacherId, long studentId);
    void deactivateRelationsForRoleChange(long userId);

    /** 学生申请加入老师 */
    long applyToTeacher(long studentId, long teacherId);

    /** 老师邀请学生 */
    long inviteStudent(long teacherId, long studentId);

    /** 同意申请/邀请 */
    void approveRelation(long relationId, long approverId);

    /** 拒绝申请/邀请 */
    void rejectRelation(long relationId, long rejecterId);

    /** 发起人撤回 */
    void cancelRelation(long relationId, long cancellerId);

    /** 已建立关系后：学生退出 / 老师移除 */
    void leaveRelation(long relationId, long leaverId);

    /** 待我审批的关系列表 */
    List<TeacherStudentRelationVO> listPending(long userId);

    /** 我的师生关系列表（活跃 + 待审批） */
    List<TeacherStudentRelationVO> listMyRelations(long userId);

    /** 查询当前用户与目标用户的关系状态：返回关系 id + 状态，无则 null */
    TeacherStudentRelationVO getRelationWith(long currentUserId, long otherUserId);
}
