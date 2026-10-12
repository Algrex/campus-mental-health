package com.algrex.campus.common;

import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String,String>> invalid(IllegalArgumentException exception) {
    return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String,String>> validation(MethodArgumentNotValidException exception) {
    return ResponseEntity.badRequest().body(Map.of("message", "请检查必填内容及数值范围"));
  }
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String,String>> denied(AccessDeniedException exception) {
    return ResponseEntity.status(403).body(Map.of("message", "你没有访问此记录的权限"));
  }
  @ExceptionHandler(DuplicateKeyException.class)
  public ResponseEntity<Map<String,String>> conflict(DuplicateKeyException exception) {
    return ResponseEntity.status(409).body(Map.of("message", "记录已存在，请勿重复提交"));
  }
}
