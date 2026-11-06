package net.alan.jupiter.application.summary;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 总汇总快照（只读 DTO）。
 * <p>设计约定：</p>
 * <ul>
 *   <li>只下发数字与聚合项，<b>不下发学生明细</b>；明细仍由既有报表接口负责。</li>
 *   <li>{@code studentTotal} 为口径内<b>去重学生数</b>；
 *       {@code shouldCount / actualCount / leaveCount / absentCount} 为<b>人次</b>
 *       （SCOPE 形态按"日期 × 学生"计一次，ALL 形态按学生去重后计一次）。</li>
 *   <li>{@code rate} 为实到率百分比：{@code actualCount / shouldCount * 100}，保留两位小数；
 *       {@code shouldCount == 0} 时返回 {@code 0.0}。</li>
 *   <li>应到口径与既有报表保持一致：走读（DAY_STUDENT）、长期病假（LONG_SICK_LEAVE）不计入应到；
 *       请假 {@code leaveCount} = 事假 + 病假；旷宿 {@code absentCount} = ABSENT。</li>
 * </ul>
 *
 * @param scope        汇总形态：单任务 / 日期范围 / 全量
 * @param asOf         快照生成时间
 * @param taskCount    口径内任务数
 * @param studentTotal 去重学生数
 * @param shouldCount  应到人次
 * @param actualCount  实到人次
 * @param leaveCount   请假人次（事假 + 病假）
 * @param absentCount  旷宿人次
 * @param rate         实到率百分比（0~100，保留两位小数）
 * @param items        按维度的聚合项，可为空列表（不会为 {@code null}）
 */
public record SummarySnapshot(
        Scope scope,
        LocalDateTime asOf,
        int taskCount,
        int studentTotal,
        int shouldCount,
        int actualCount,
        int leaveCount,
        int absentCount,
        double rate,
        List<Item> items) {

    /** 汇总形态。 */
    public enum Scope {
        /** 单任务（L1）。 */
        TASK,
        /** 日期范围 / 学院 / 楼栋（L2，总汇总主形态）。 */
        SCOPE,
        /** 全量任务按学生去重（L3）。 */
        ALL
    }

    /** 聚合维度取值，序列化后为字符串。 */
    public static final String DIM_DATE = "date";
    public static final String DIM_COLLEGE = "college";
    public static final String DIM_BUILDING = "building";
    public static final String DIM_CLASS_NAME = "className";

    public SummarySnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 单个维度聚合项。
     *
     * @param dimension    维度：date / college / building / className
     * @param key          维度取值（日期为 ISO 字符串）
     * @param studentTotal 该维度下去重学生数
     * @param shouldCount  该维度下应到人次
     * @param actualCount  该维度下实到人次
     * @param leaveCount   该维度下请假人次
     * @param absentCount  该维度下旷宿人次
     * @param rate         该维度下实到率百分比
     */
    public record Item(
            String dimension,
            String key,
            int studentTotal,
            int shouldCount,
            int actualCount,
            int leaveCount,
            int absentCount,
            double rate) {
    }
}
