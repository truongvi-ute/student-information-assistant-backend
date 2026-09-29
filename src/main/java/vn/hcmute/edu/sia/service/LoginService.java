package vn.hcmute.edu.sia.service;

import vn.hcmute.edu.sia.dto.request.LoginRequest;
import vn.hcmute.edu.sia.dto.response.LoginResponse;

public interface LoginService {

    LoginResponse login(LoginRequest request);
}