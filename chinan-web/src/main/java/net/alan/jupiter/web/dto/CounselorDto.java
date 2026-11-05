package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CounselorDto {

    private String id;
    private String name;
    private String username;
    private String studentNo;
    private String className;
    private String gender;
    private String phone;
    private String department;
    private String role;
    private Boolean isActive;
    private LocalDateTime createdAt;
}