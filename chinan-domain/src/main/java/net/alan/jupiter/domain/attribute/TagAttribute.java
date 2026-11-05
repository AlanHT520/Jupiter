package net.alan.jupiter.domain.attribute;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagAttribute {

    private String key;
    private String displayName;
    private String color;
    private String icon;
    private int sortOrder;
}