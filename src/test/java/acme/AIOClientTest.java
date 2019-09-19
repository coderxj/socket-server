package acme;

import com.acme.client.AIOClient;
import com.acme.common.Receiver;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author acme
 * @date 2019/9/3 8:08 PM
 */
public class AIOClientTest {
    public static void main(String[] args) throws InterruptedException {
        List<AIOClient> aioClients = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AIOClient aioClient = new AIOClient("server" + (i + 1), new FixedLengthProtocol());
            aioClient.connect();
            aioClient.receive(new MyReceiver(), 10);
            aioClients.add(aioClient);
        }
        Iterator<AIOClient> iterator = aioClients.iterator();
        int t = 10;
        while (iterator.hasNext()){
            AIOClient bioServer = iterator.next();
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
            Thread.sleep(1000 * 10);
        }

        while (true){
            Thread.sleep(10000);
        }
    }

    static class MyReceiver implements Receiver{
        int i = 0;
        @Override
        public void recv(FrameInfo frameInfo, String key) {
            System.out.println(++i);
            System.out.println(frameInfo);
        }
    }
}
