package acme;

import com.acme.common.Receiver;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;
import com.acme.server.BIOServer;

/**
 * @author acme
 * @date 2019/9/3 8:12 PM
 */
public class BIOServerTest {
    static int i = 0;
    public static void main(String[] args) {
        BIOServer bioServer = new BIOServer(new FixedLengthProtocol());
        bioServer.start();
        bioServer.receive(new MyReceiver(), 100);
        for (int i = 0; i < 100; i++) {
            bioServer.send("server1", GenString.getBytes(1));
        }
    }

    static class MyReceiver implements Receiver {
        int i = 0;
        @Override
        public void recv(FrameInfo frameInfo, String key) {
            System.out.println(++i);
            System.out.println(frameInfo);
        }
    }
}
