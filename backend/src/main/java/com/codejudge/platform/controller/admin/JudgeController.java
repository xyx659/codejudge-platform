package com.codejudge.platform.controller.admin;

import com.codejudge.platform.common.ApiResponse;
import com.codejudge.platform.common.AuditOperation;
import com.codejudge.platform.dto.JudgeTriggerResult;
import com.codejudge.platform.service.JudgeService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端评测接口（骨架）。
 *
 * <p>提供「手动重新评测」能力，用于提交后判卷状态卡在 {@code PENDING} 时人工触发重判。
 * 真实评测引擎接入前，内部是占位实现，触发后只打日志、不改判卷状态。</p>
 */
@RestController
@RequestMapping("/api/admin/judge")
public class JudgeController {

    private final JudgeService judgeService;

    public JudgeController(JudgeService judgeService) {
        this.judgeService = judgeService;
    }

    /**
     * 手动重新评测某次提交。
     *
     * <pre>POST /api/admin/judge/{submissionId}</pre>
     */
    @PostMapping("/{submissionId}")
    @AuditOperation(
            module = "评测",
            operation = "REJUDGE",
            description = "手动重新评测提交")
    public ApiResponse<JudgeTriggerResult> rejudge(@PathVariable Long submissionId) {
        judgeService.trigger(submissionId);
        return ApiResponse.ok(new JudgeTriggerResult(submissionId, true, "已触发评测"));
    }
}
