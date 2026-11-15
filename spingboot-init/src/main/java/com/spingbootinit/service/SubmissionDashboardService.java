package com.spingbootinit.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spingbootinit.model.dto.questionsubmit.SubmissionDashboardQuery;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionDetailVO;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionListItemVO;
import com.spingbootinit.model.vo.questionsubmitvo.SubmissionOverviewVO;
import java.util.List;

public interface SubmissionDashboardService {
    Page<SubmissionListItemVO> page(SubmissionDashboardQuery query);
    SubmissionOverviewVO overview(SubmissionDashboardQuery query, int days, boolean canManage);
    default SubmissionDetailVO getDetail(long submissionId, long currentUserId, boolean admin) {
        return getDetail(submissionId, currentUserId, admin, List.of());
    }

    SubmissionDetailVO getDetail(long submissionId, long currentUserId, boolean admin, List<Long> managedUserIds);
}
