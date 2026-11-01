package vn.hcmute.edu.sia.dto.internal;

import vn.hcmute.edu.sia.dto.response.LoginResponse;

public record LoginResult(LoginResponse response, IssuedTokens tokens) {}
