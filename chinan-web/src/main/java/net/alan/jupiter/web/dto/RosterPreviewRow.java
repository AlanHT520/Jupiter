package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 花名册导入预览行。
 * <p>只包含标量字段，避免把带懒加载关联的实体直接序列化到响应里。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RosterPreviewRow {

    private String studentNo;
    private String name;
    private String gender;
    private String className;
    private String buildingName;
    private Integer floorNumber;
    private String roomNumber;
    private String bedNumber;
    private String phone;
    private String remark;
}
