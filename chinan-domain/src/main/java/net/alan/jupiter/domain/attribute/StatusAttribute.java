package net.alan.jupiter.domain.attribute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusAttribute {

    private String key;
    private String displayName;
    private String defaultValue;
    private boolean required;
    private String options;
    private String attrType;
}