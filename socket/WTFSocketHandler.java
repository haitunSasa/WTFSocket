package wtf.socket;

import java.util.logging.Logger;

/**
 * socket响应
 */
public abstract class WTFSocketHandler {

    private static final Logger logger = Logger.getLogger("socket");

    /**
     * 收到消息
     *
     * @param session 会话对象
     * @param msg 会话消息
     * @return true/false 已处理消息/未处理消息
     */
    public boolean onReceive(WTFSocketSession session, WTFSocketMsg msg) {
        return false;
    };

    /**
     * 收到异常
     *
     * @param session 发生异常的会话
     * @param msg 发送异常的消息
     * @param e 异常
     */
    public boolean onException(WTFSocketSession session, WTFSocketMsg msg, WTFSocketException e) {
        return false;
    }

}
