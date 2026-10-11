package net.alan.jupiter.application.auth;

import net.alan.jupiter.common.exception.BizException;
import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CounselorRepository counselorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(counselorRepository, passwordEncoder, jwtTokenProvider);
    }

    @Test
    @DisplayName("登录成功应返回JWT令牌")
    void shouldReturnTokenOnSuccessfulLogin() {
        Counselor counselor = Counselor.builder()
                .id("c1")
                .username("admin")
                .password("encodedPassword")
                .role("ADMIN")
                .build();

        when(counselorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(counselor));
        when(passwordEncoder.matches("admin123", "encodedPassword"))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("mock-jwt-token");

        LoginResult result = authService.login("admin", "admin123");
        assertEquals("mock-jwt-token", result.getToken());
        assertEquals("c1", result.getUserId());
        assertEquals("admin", result.getUsername());
        assertEquals("ADMIN", result.getRole());
        verify(jwtTokenProvider).generateToken("c1", "admin", "ROLE_ADMIN", 0L);
    }

    @Test
    @DisplayName("用户不存在应抛出异常")
    void shouldThrowWhenUserNotFound() {
        when(counselorRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(BizException.class, () ->
                authService.login("unknown", "password"));
    }

    @Test
    @DisplayName("密码错误应抛出异常")
    void shouldThrowWhenPasswordWrong() {
        Counselor counselor = Counselor.builder()
                .id("c1")
                .username("admin")
                .password("encodedPassword")
                .build();

        when(counselorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(counselor));
        when(passwordEncoder.matches("wrong", "encodedPassword"))
                .thenReturn(false);

        assertThrows(BizException.class, () ->
                authService.login("admin", "wrong"));
    }

    @Test
    @DisplayName("角色前缀处理")
    void shouldHandleRolePrefix() {
        Counselor counselor = Counselor.builder()
                .id("c1")
                .username("admin")
                .password("encodedPassword")
                .role("ROLE_ADMIN")
                .build();

        when(counselorRepository.findByUsername("admin"))
                .thenReturn(Optional.of(counselor));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(jwtTokenProvider.generateToken(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("token");

        LoginResult result = authService.login("admin", "password");
        assertEquals("token", result.getToken());
        verify(jwtTokenProvider).generateToken("c1", "admin", "ROLE_ADMIN", 0L);
    }

    @Test
    @DisplayName("修改密码成功")
    void shouldChangePasswordSuccessfully() {
        Counselor counselor = Counselor.builder()
                .id("c1")
                .username("admin")
                .password("oldEncoded")
                .build();

        when(counselorRepository.findById("c1"))
                .thenReturn(Optional.of(counselor));
        when(passwordEncoder.matches("oldPass", "oldEncoded"))
                .thenReturn(true);
        when(passwordEncoder.encode("newPass"))
                .thenReturn("newEncoded");

        authService.changePassword("c1", "oldPass", "newPass");

        verify(counselorRepository).save(any(Counselor.class));
        assertEquals("newEncoded", counselor.getPassword());
    }

    @Test
    @DisplayName("旧密码错误时修改密码失败")
    void shouldFailChangePasswordWhenOldPasswordWrong() {
        Counselor counselor = Counselor.builder()
                .id("c1")
                .password("oldEncoded")
                .build();

        when(counselorRepository.findById("c1"))
                .thenReturn(Optional.of(counselor));
        when(passwordEncoder.matches("wrongOld", "oldEncoded"))
                .thenReturn(false);

        assertThrows(BizException.class, () ->
                authService.changePassword("c1", "wrongOld", "newPass"));
    }
}