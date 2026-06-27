package top.wyatt.core.exception;

/**
 * 加密包格式无效异常
 * 触发场景：魔数不匹配、版本不兼容、文件结构损坏
 */
public class InvalidPackException extends RuntimeException {
    public InvalidPackException(String message) {
        super(message);
    }

    public InvalidPackException(String message, Throwable cause) {
        super(message, cause);
    }
}