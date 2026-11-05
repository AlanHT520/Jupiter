package net.alan.jupiter.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String userId;
    private String username;
    private String role;
    private String name;
    private String phone;
    private String studentNo;
    private String className;
    private String departmentName;
}