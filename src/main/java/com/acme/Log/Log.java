package com.acme.Log;

/*
* 统一格式：info("xxx:{}, xxx:{}", a, b)
* 变量a、b会顺序填充{}
* */

/**
 * @author acme
 * @date 2019/8/26 7:32 PM
 */
public interface Log {
    String DEBUG = "[DEBUG] ";
    String INFO = "[INFO] ";
    String WARN = "[WARN] ";
    String ERROR = "[ERROR] ";
    void debug(String format, Object...args);
    void info(String format, Object...args);
    void warn(String format, Object...args);
    void error(String format, Object...args);
}
