package acme;

import com.acme.client.NIOClient;
import com.acme.common.Receiver;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author acme
 * @date 2019/9/3 8:10 PM
 */
public class NIOClientTest {
    public static void main(String[] args) {
        List<NIOClient> nioClients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            NIOClient nioClient = new NIOClient("server" + (i + 1), new FixedLengthProtocol());
            nioClient.connect();
            nioClient.receive(new MyReceiver(), 10);
            nioClients.add(nioClient);
        }

        Iterator<NIOClient> iterator = nioClients.iterator();
        int t = 10;
        while (iterator.hasNext()){
            NIOClient nioClient = iterator.next();
            int sleep = t*=1;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000; i++) {
                        nioClient.send(GenString.getBytes(1));
                        try {
                            Thread.sleep(sleep);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
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
