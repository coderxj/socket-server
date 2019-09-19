package acme.test;

import java.nio.ByteBuffer;

/**
 * @author acme
 * @date 2019/9/9 11:09 PM
 */
public class BufferTest {
    public static void main(String[] args) throws InterruptedException {
        ByteBuffer buffer = ByteBuffer.allocate(32);
        while (true){
            byte[] writeBuf = "hello".getBytes();
            buffer.put(writeBuf);
            buffer.flip();
            byte[] readBuf = new byte[5];
            buffer.get(readBuf);
            buffer.clear();
            System.out.println(new String(readBuf));
        }
    }
}
