package top.wyatt.core.exception;

/**
 * 密钥错误异常
 * 触发场景：密钥校验失败、无法解密索引区头部
 */
public class WrongKeyException extends RuntimeException {
    public WrongKeyException(String message) {
        super(message);
    }

    public WrongKeyException(String message, Throwable cause) {
        super(message, cause);
    }
}