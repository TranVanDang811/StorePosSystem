package com.possystem.backend.auth.service;

import com.nimbusds.jose.JOSEException;
import com.possystem.backend.auth.dto.AuthenticationRequest;
import com.possystem.backend.auth.dto.AuthenticationResponse;
import com.possystem.backend.auth.dto.IntrospectRequest;
import com.possystem.backend.auth.dto.IntrospectResponse;

import java.text.ParseException;

public interface AuthenticationService {
    AuthenticationResponse authenticate(AuthenticationRequest request);
    IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException;
}
