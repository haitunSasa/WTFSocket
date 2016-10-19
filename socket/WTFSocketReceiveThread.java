package wtf.socket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;

import java.io.IOException;
import java.net.Socket;
import java.util.logging.Logger;

class WTFSocketReceiveThread implements Runnable {

    private static final Logger logger = Logger.getLogger("socket");

    private WTFSocketClient wtfSocketClient;

    WTFSocketReceiveThread(WTFSocketClient wtfSocketClient) {
        this.wtfSocketClient = wtfSocketClient;
    }

    @Override
    public void run() {

        WTFSocketMsgWrapper msg = new WTFSocketMsgWrapper();

        try {
            Socket socket = wtfSocketClient.getSocket();

            if (socket.isClosed() || !socket.isConnected()) {
                return;
            }

            int toReadLen = socket.getInputStream().available();

            if (toReadLen <= 0) {
                return;
            }

            byte bytes[] = new byte[toReadLen];
            int readLen = socket.getInputStream().read(bytes);
            msg.setOriginalStr(new String(bytes, 0, readLen));

            for (String packet : wtfSocketClient.parseAndGetPackets(msg.getOriginalStr())) {

                msg = JSON.parseObject(packet, WTFSocketMsgWrapper.class);

                if (msg.getFrom() == null || msg.getTo() == null || msg.getMsgId() == null || msg.getMsgType() == null) {
                    throw new IOException("protocol err!");
                }

                if (msg.getMsgType() == null || msg.getMsgType() == 1) {
                    logger.info(String.format("received msg from <%s> to <%s>:\nmsg => %s",
                            msg.getFrom(),
                            msg.getTo(),
                            packet));

                }
                WTFSocketSessionFactory.dispatchMsg(msg);
            }

        } catch (IOException | JSONException e) {

            wtfSocketClient.clearBuffer();

            WTFSocketException exception = new WTFSocketException(e.getMessage());
            exception.setLocation(this.getClass().getName() + "$run");
            exception.setMsg(msg);

            WTFSocketSessionFactory.dispatchException(exception);
        }

    }
}