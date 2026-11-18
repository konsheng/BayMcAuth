package com.baymc.auth.model;

public class SQLRuntimeException extends RuntimeException {

  public SQLRuntimeException(Throwable cause) {
    this("执行数据库 SQL 操作时发生内部错误", cause);
  }

  public SQLRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }
}
