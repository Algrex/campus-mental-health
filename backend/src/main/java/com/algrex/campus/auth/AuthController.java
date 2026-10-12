package com.algrex.campus.auth;

import com.algrex.campus.demo.DemoAccounts;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {
  private final DemoAccounts accounts;
  public AuthController(DemoAccounts accounts) { this.accounts = accounts; }

  @GetMapping("/api/v1/auth/session")
  public Map<String,Object> session(Authentication authentication, HttpServletRequest request) {
    CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("csrfToken", token.getToken());
    result.put("user", authentication == null || authentication instanceof AnonymousAuthenticationToken
        ? null : accounts.get(authentication.getName()));
    return result;
  }
}
