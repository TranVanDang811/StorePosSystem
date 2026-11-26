package com.possystem.backend.config;

import com.possystem.backend.auth.repository.InvalidatedTokenRepository;
import com.possystem.backend.common.enums.UserStatus;
import com.possystem.backend.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;

@Component
public class CustomJwtDecoder implements JwtDecoder {

    @Value("${jwt.signerKey}")
    private String signerKey;

    @Autowired
    private InvalidatedTokenRepository invalidatedTokenRepository;

    @Autowired
    private UserRepository userRepository;

    private NimbusJwtDecoder nimbusJwtDecoder = null;

    private static final Logger logger = LoggerFactory.getLogger(CustomJwtDecoder.class);

    @Override
    public Jwt decode(String token) throws JwtException {
        // 1. NimbusJwtDecoder performs signature verification & expiration check in 1 optimized pass
        Jwt jwt = nimbusJwtDecoder.decode(token);

        // 2. Fast revocation lookup by JTI in invalidated_token table
        String jti = jwt.getId();
        if (jti != null && invalidatedTokenRepository.existsById(jti)) {
            logger.warn("Received revoked JWT token with jti: {}", jti);
            throw new JwtException("Token has been revoked");
        }

        // 3. Prevent using Refresh Token as Access Token on API endpoints
        String tokenType = jwt.getClaimAsString("token_type");
        if ("REFRESH".equalsIgnoreCase(tokenType)) {
            logger.warn("Attempt to use Refresh Token as Access Token for subject: {}", jwt.getSubject());
            throw new JwtException("Refresh token cannot be used to access protected resources");
        }

        // 4. Verify user status is still ACTIVE & Token invalidation on password change (lightweight scalar projection, avoids loading @OneToOne profiles)
        String username = jwt.getSubject();
        if (username != null && userRepository != null) {
            List<Object[]> authRows = userRepository.findAuthStateByUsername(username);
            if (authRows != null && !authRows.isEmpty()) {
                Object[] row = authRows.get(0);
                UserStatus status = (row[0] instanceof UserStatus s) ? s : null;
                LocalDateTime passwordChangedAt = (row[1] instanceof LocalDateTime ldt) ? ldt : null;

                if (status != null && status != UserStatus.ACTIVE) {
                    logger.warn("Access denied for deactivated user: {}", username);
                    throw new JwtException("User account is inactive or disabled");
                }
                if (passwordChangedAt != null) {
                    Object pwdTsObj = jwt.getClaims().get("pwd_ts");
                    long tokenPwdTs = (pwdTsObj instanceof Number number) ? number.longValue() : 0L;
                    long userPwdTs = passwordChangedAt.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    if (tokenPwdTs < (userPwdTs - 1000)) {
                        logger.warn("Access denied: Token invalidated due to password change for user {}", username);
                        throw new JwtException("Token has been invalidated due to password change");
                    }
                }
            }
        }

        return jwt;
    }

    @PostConstruct
    public void init() {
        if (signerKey == null || signerKey.isBlank()) {
            throw new IllegalStateException("JWT signerKey is missing or empty");
        }
        SecretKeySpec secretKeySpec = new SecretKeySpec(signerKey.getBytes(), "HmacSHA512");
        nimbusJwtDecoder = NimbusJwtDecoder.withSecretKey(secretKeySpec)
                .macAlgorithm(MacAlgorithm.HS512)
                .build();
    }
}
