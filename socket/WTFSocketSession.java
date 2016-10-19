package wtf.socket;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * socket会话
 * 每个会话均只有唯一会话对象
 * 会话对象一旦设置则不允许更改
 * 会话对象只能通过SocketSessionFactory创建
 */
public class WTFSocketSession {

    private static Logger logger = Logger.getLogger("socket");

    // 自身名称
    private String from;

    // 会话名称
    private String to;

    // 默认响应方法
    private WTFSocketHandler defaultResponse = new WTFSocketHandler() {
    };

    // 等待回复消息列表
    private ConcurrentHashMap<String, WTFSocketMsgWrapper> waitResponses = new ConcurrentHashMap<>();

    // 等待发送信息列表
    private ConcurrentLinkedQueue<WTFSocketMsgWrapper> waitSendQ = new ConcurrentLinkedQueue<>();

    WTFSocketSession(String from, String to) {
        this.from = from;
        this.to = to;
    }

    /**
     * 获取会话中的本机地址
     *
     * @return 本机地址
     */
    public String getFrom() {
        return from;
    }

    /**
     * 获取会话中的对方地址
     *
     * @return 对方地址
     */
    public String getTo() {
        return to;
    }


    /**
     * 发送消息给会话对象
     * 会自动添加新的msgId
     * 不接受回复
     *
     * @param msg 消息对象
     *            ·
     */
    public void sendMsg(WTFSocketMsg msg) {

        sendMsg(msg, null);

    }

    /**
     * 发送消息给会话对象
     * 会自动添加新的msgId
     * 并通过 handler 对象处理回复
     *
     * @param msg     消息对象
     * @param handler 响应方法
     */
    public void sendMsg(WTFSocketMsg msg, WTFSocketHandler handler) {
        sendMsg(msg, handler, 0);
    }

    /**
     * 发送消息给会话对象
     * 会自动添加新的msgId
     * 并通过 handler 对象处理回复
     *
     * @param msg     消息对象
     * @param handler 响应方法
     * @param timeout 等待超时，不能低于200ms
     */
    public void sendMsg(WTFSocketMsg msg, WTFSocketHandler handler, int timeout) {

        if (timeout == 0) {
            timeout = Integer.MAX_VALUE;
        } else if (timeout < 200) {
            timeout = 200;
        }

        WTFSocketMsgWrapper msgWrapper = new WTFSocketMsgWrapper(this, msg);
        msgWrapper.setMsgId(WTFSocketSessionFactory.getSelfIncrementMsgId());
        msgWrapper.setTimeout(System.currentTimeMillis() + timeout);

        if (handler != null) {
            msgWrapper.setHandler(handler);
            waitResponses.put(msgWrapper.getTag(), msgWrapper);
        }

        waitSendQ.add(msgWrapper);
    }

    /**
     * 回复某条消息
     * 回复消息不会产生新的msgId
     *
     * @param reply    回复的消息
     * @param original 原始消息
     */
    public void replyMsg(WTFSocketMsg reply, WTFSocketMsg original) {
        waitSendQ.add(new WTFSocketMsgWrapper(this, reply)
                .setMsgId(original.getMsgId()));
    }

    /**
     * 取消发送某条消息
     * 如果消息已被发送，则取消无效
     *
     * @param msg    需要撤回的消息
     */
    public void cancelMsg(WTFSocketMsg msg) {

        WTFSocketMsgWrapper wrapper = msg.getWrapper();

        if (waitResponses.contains(wrapper)) {
            waitResponses.remove(wrapper.getTag());
        }
        if (waitSendQ.contains(wrapper)) {
            waitSendQ.remove(wrapper);
        }
    }

    /**
     * 设置默认响应方法
     *
     * @param defaultResponse 响应方法
     */
    public void setDefaultResponse(WTFSocketHandler defaultResponse) {
        if (defaultResponse != null) {
            this.defaultResponse = defaultResponse;
        }
    }

    /**
     * 移除默认响应方法
     */
    public void removeDefaultResponse() {
        defaultResponse = new WTFSocketHandler() {
        };
    }

    /**
     * 关闭会话
     */
    public void close() {
        WTFSocketSessionFactory.closeSession(this);
    }

    // 派发消息
    boolean dispatchMsg(WTFSocketMsgWrapper msg) {

        String msgTag = msg.getTag();

        // 使用单次响应
        if (waitResponses.containsKey(msgTag)) {
            WTFSocketMsgWrapper wrapper = waitResponses.get(msgTag);
            waitResponses.remove(msgTag);

            if (wrapper.getHandler().onReceive(this, msg.getMsg())) {
                return true;
            }
        }
        return defaultResponse.onReceive(this, msg.getMsg());
    }

    // 派发异常
    boolean dispatchException(WTFSocketException e) {

        WTFSocketMsgWrapper msg = e.getMsg();

        // 使用单次响应
        if (waitResponses.containsKey(msg.getTag())) {
            WTFSocketMsgWrapper waitMsg = waitResponses.get(msg.getTag());
            waitResponses.remove(msg.getTag());

            if (waitMsg.getHandler().onException(msg.getBelong(), msg.getMsg(), e)) {
                return true;
            }
        }
        return defaultResponse.onException(msg.getBelong(), msg.getMsg(), e);

    }

    // 超时检查
    void checkTimeout() {

        for (WTFSocketMsgWrapper msg : waitResponses.values()) {

            if (msg.getTimeout() < System.currentTimeMillis()) {
                WTFSocketException e = new WTFSocketException("time out");
                e.setLocation(this.getClass().getName() + "$checkTimeout");
                e.setMsg(msg);
                WTFSocketSessionFactory.dispatchException(e);
            }

        }

    }

    // 获取带发送消息队列
    ConcurrentLinkedQueue<WTFSocketMsgWrapper> getWaitSendQ() {
        return waitSendQ;
    }

    // 清空等待回复消息
    void clearWaitResponses() {
        waitResponses.clear();
    }

    // 清空 id < msgId 的消息的等待
    void clearWaitResponsesBefore(int msgId) {
        for (String key : waitResponses.keySet()) {
            if (Integer.valueOf(key) < msgId) {
                waitResponses.remove(key);
            }
        }
    }

    // 判断是否有消息等待发送
    boolean hasMsg () {
        return !waitSendQ.isEmpty();
    }

}