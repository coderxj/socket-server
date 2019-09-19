package acme;

import com.acme.client.BIOClient;
import com.acme.common.Receiver;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author acme
 * @date 2019/9/3 8:14 PM
 */
public class BIOClientTest {
    public static void main(String[] args) {
        List<BIOClient> bioClients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            BIOClient bioClient = new BIOClient("server" + (i + 1), new FixedLengthProtocol());
            bioClient.connect();
            bioClient.receive(new MyReceiver(), 100);
            bioClients.add(bioClient);
        }

        Iterator<BIOClient> iterator = bioClients.iterator();
        int t = 10;
        while (iterator.hasNext()){
            BIOClient bioServer = iterator.next();
            int sleep = t*=1;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 1000; i++) {
                        bioServer.send(GenString.getBytes(1));
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
