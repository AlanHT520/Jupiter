package net.alan.jupiter.web.app;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import net.alan.jupiter.application.security.AccessGuard;
import net.alan.jupiter.application.summary.SummaryAggregator;
import net.alan.jupiter.application.summary.SummarySnapshot;
import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.common.exception.ErrorCode;
import net.alan.jupiter.common.result.ApiResult;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * 总汇总只读接口：桌面报表与后续 Android 共用同一聚合口径。
 *
 * <p>权限规则：</p>
 * <ul>
 *   <li>{@code scope=TASK}：复用 {@link AccessGuard}，管理员可查任意任务，辅导员仅可查分配给自己的任务；</li>
 *   <li>{@code scope=SCOPE / scope=ALL}：管理员为全量口径；辅导员自动收窄为"仅自己任务"
 *       （未分配任务不计入，与 {@link AccessGuard} 的归属判定保持一致）。</li>
 * </ul>
 *
 * <p>说明：{@code /api/**} 已由 Spring Security 限制为 ADMIN / COUNSELOR 登录用户，
 * 因此这里不再重复做登录校验，只做任务归属收窄。</p>
 */
@Tag(name = "总汇总", description = "总汇总只读聚合接口（桌面报表 / Android 共用）")
@RestController
@RequestMapping("/api/summary")
@RequiredArgsConstructor
public class SummaryController {

    private final SummaryAggregator summaryAggregator;
    private final AccessGuard accessGuard;

    @Operation(summary = "获取总汇总快照",
            description = "scope=TASK 时按任务汇总（需 taskId）；"
                    + "scope=SCOPE 时按日期范围/学院/楼栋汇总（需 from、to）；"
                    + "scope=ALL 时全量按学生去重汇总（默认）。"
                    + "日期格式 yyyy-MM-dd，辅导员仅能统计自己的任务。")
    @GetMapping
    public ApiResult<SummarySnapshot> getSummary(
            @RequestParam(defaultValue = "ALL") String scope,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String college,
            @RequestParam(required = false) String building) {

        String userId = currentUserId();
        boolean admin = accessGuard.isAdmin(userId);
        // 辅导员只能看自己的任务；管理员不受限（null = 不限制）
        String counselorScopeId = admin ? null : userId;
        SummarySnapshot.Scope target = parseScope(scope);

        return ApiResult.success(switch (target) {
            case TASK -> {
                if (taskId == null || taskId.isBlank()) {
                    throw new BizException(ErrorCode.BAD_REQUEST, "scope=TASK 时必须提供 taskId");
                }
                accessGuard.requireTaskAccess(userId, taskId);
                yield summaryAggregator.taskSummary(taskId);
            }
            case SCOPE -> {
                LocalDate fromDate = parseDate(from, "from");
                LocalDate toDate = parseDate(to, "to");
                if (fromDate == null || toDate == null) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "scope=SCOPE 时必须提供 from 与 to（yyyy-MM-dd）");
                }
                yield summaryAggregator.scopeSummary(fromDate, toDate, college, building,
                        counselorScopeId);
            }
            case ALL -> summaryAggregator.allTasksSummary(counselorScopeId);
        });
    }

    private static String currentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return authentication.getName();
    }

    private static SummarySnapshot.Scope parseScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SummarySnapshot.Scope.ALL;
        }
        try {
            return SummarySnapshot.Scope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "scope 仅支持 TASK / SCOPE / ALL");
        }
    }

    /**
     * 宽松解析日期：空串（前端常传 {@code from=}）视为未传，格式错误给明确提示。
     */
    private static LocalDate parseDate(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 日期格式无效，应为 yyyy-MM-dd");
        }
    }
}
