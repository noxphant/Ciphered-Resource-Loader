package top.wyatt.core.exception;

/**
 * 资源解密失败异常
 * 触发场景：数据块损坏、认证标签不通过、块索引越界
 */
public class DecryptException extends RuntimeException {
    public DecryptException(String message) {
        super(message);
    }

    public DecryptException(String message, Throwable cause) {
        super(message, cause);
    }
}