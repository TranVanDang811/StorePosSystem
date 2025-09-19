package com.possystem.backend.config;

import com.nimbusds.jose.JOSEException;
import com.possystem.backend.auth.dto.IntrospectRequest;
import com.possystem.backend.auth.service.AuthenticationService;
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

import javax.crypto.spec.SecretKeySpec;
import java.text.ParseException;

@Component
public class CustomJwtDecoder implements JwtDecoder {

    // Lấy giá trị của `jwt.signerKey` từ file cấu hình (application.yml hoặc application.properties)
    @Value("${jwt.signerKey}")
    private String signerKey;

    // Inject service AuthenticationService để gọi API introspection
    @Autowired
    private AuthenticationService authenticationService;

    // Đối tượng NimbusJwtDecoder dùng để giải mã JWT, sẽ được khởi tạo khi cần
    private NimbusJwtDecoder nimbusJwtDecoder = null;


    private static final Logger logger = LoggerFactory.getLogger(CustomJwtDecoder.class);

    // Override phương thức decode() từ interface JwtDecoder để thực hiện giải mã JWT
    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            // Gọi service introspect để kiểm tra token có hợp lệ không
            var response = authenticationService.introspect(
                    IntrospectRequest.builder().token(token).build());

            // Nếu token không hợp lệ, ném ra ngoại lệ JwtException
            if (!response.isValid()) {
                logger.warn("Token invalid: {}", token);
                throw new JwtException("Token invalid");
            }
        } catch (JOSEException | ParseException e) {
            logger.error("Error during token introspection", e);
            // Nếu có lỗi xảy ra trong quá trình kiểm tra, ném ra JwtException với thông báo lỗi
            throw new JwtException(e.getMessage());
        }

        // Giải mã và trả về đối tượng JWT
        return nimbusJwtDecoder.decode(token);
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