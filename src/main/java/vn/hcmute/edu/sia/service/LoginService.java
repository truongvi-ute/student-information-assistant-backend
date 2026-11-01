package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.internal.LoginResult;

public interface LoginService {
    LoginResult login(LoginRequest request);
}
