package net.alan.jupiter.tcp.handler;

import net.alan.jupiter.domain.model.Counselor;
import net.alan.jupiter.domain.repository.CounselorRepository;
import net.alan.jupiter.infrastructure.security.JwtTokenProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class TcpAuthHelper {

    private final JwtTokenProvider jwtTokenProvider;
    private final CounselorRepository counselorRepository;

    public TcpAuthHelper(JwtTokenProvider jwtTokenProvider, CounselorRepository counselorRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.counselorRepository = counselorRepository;
    }

    public String validateAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        if (!jwtTokenProvider.validateToken(token)) {
            return null;
        }
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        long tokenVer = jwtTokenProvider.getTokenVersionFromToken(token);
        Optional<Counselor> counselor = counselorRepository.findById(userId);

        if (counselor.isEmpty()) {
            return null;
        }
        long curVer = counselor.get().getTokenVersion() != null ? counselor.get().getTokenVersion() : 0L;
        if (tokenVer < curVer) {
            return null;
        }
        return userId;
    }
}
