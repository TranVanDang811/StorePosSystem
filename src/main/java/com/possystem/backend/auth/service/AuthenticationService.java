package com.possystem.backend.auth.service;

import com.nimbusds.jose.JOSEException;
import com.possystem.backend.auth.dto.*;

import java.text.ParseException;

public interface AuthenticationService {
    AuthenticationResponse authenticate(AuthenticationRequest request);
    IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException;
    AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException;
    void logout(LogoutRequest request) throws ParseException, JOSEException;
}
