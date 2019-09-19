package acme.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.ExecutionException;

/**
 * @author acme
 * @date 2019/9/10 11:39 AM
 */
public class AIOClient {
    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        //创建客户端通道
        AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
        //异步连接，使用Future.get等待连接成功后返回
        //当然也可以使用方法回调，这里简单演示，就不用了
        socketChannel.connect(new InetSocketAddress("127.0.0.1", 9999)).get();
        ByteBuffer readBuffer = ByteBuffer.allocate(32);
        ByteBuffer writeBuffer = ByteBuffer.allocate(32);
        while (true){
            //正常buffer写操作
            writeBuffer.put("hello".getBytes());
            writeBuffer.flip();
            //异步写，使用Future.get等待写成功
            socketChannel.write(writeBuffer).get();
            writeBuffer.clear();
            //异步读，使用Future.get等待读成功
            int len = socketChannel.read(readBuffer).get();
            byte[] buf = new byte[len];
            readBuffer.flip();
            readBuffer.get(buf);
            readBuffer.clear();
            System.out.println("recv:" + new String(buf));
            Thread.sleep(1000);
        }
    }
}
