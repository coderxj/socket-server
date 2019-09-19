package com.acme.protocol;

import com.acme.exception.DisconnectException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/*
使用长度标识帧的大小
------  ------  -----------
1 byte  4 byte  length byte
------  ------  -----------
type    length  content
------  ------  -----------

 */

/**
 * @author acme
 * @date 2019/8/17 2:45 PM
 */
public class FixedLengthProtocol implements Protocol{

    @Override
    public byte[] encodeFrame(byte[] msg, int type) {
        byte[] bytes = new byte[FRAME_HEAD_LENGTH + msg.length];
        setFrameHead(bytes, type, msg.length);
        setContent(bytes, msg);
        return bytes;
    }

    @Override
    public FrameInfo decodeFrame(InputStream in) throws IOException {
        return decodeFrame(in, decodeFrameHead(in));
    }

    @Override
    public FrameInfo decodeFrame(SocketChannel socketChannel) throws IOException {
        return decodeFrame(socketChannel, decodeFrameHead(socketChannel));
    }

    @Override
    public FrameHead decodeHead(byte[] bytes){
        if(bytes.length != FRAME_HEAD_LENGTH){
            throw new RuntimeException("帧头长度校验出错");
        }
        return new FrameHead(bytes[0], restoreLength(bytes[1], bytes[2], bytes[3], bytes[4]));
    }

    @Override
    public FrameInfo decodeFrame(byte[] bytes, int len, FrameHead frameHead){
        if(len != frameHead.getLength()){
            throw new RuntimeException("内容长度校验出错");
        }
        FrameInfo frameInfo = new FrameInfo();
        frameInfo.setFrameHead(frameHead);
        frameInfo.setMsg(new String(bytes, 0, len));
        return frameInfo;
    }

    @Override
    public Protocol clone() {
        return new FixedLengthProtocol();
    }

    /**
     * 根据帧头信息，从输入流中读取相应长度的数据
     * @param in
     * @param frameHead
     * @return
     * @throws IOException
     */
    private synchronized FrameInfo decodeFrame(InputStream in, FrameHead frameHead) throws IOException {
        byte[] buf = new byte[frameHead.getLength()];
        int offset = 0, total = frameHead.getLength(), len;
        while (offset < total){
            len = in.read(buf, offset, total - offset);
            if(len == -1){
                throw new DisconnectException();
            }
            offset += len;
        }
        FrameInfo frameInfo = new FrameInfo();
        frameInfo.setFrameHead(frameHead);
        frameInfo.setMsg(new String(buf));
        return frameInfo;
    }

    private synchronized FrameInfo decodeFrame(SocketChannel socketChannel, FrameHead frameHead) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(frameHead.getLength());
        int len = socketChannel.read(buf);
        if(len == -1){
            socketChannel.close();
            throw new DisconnectException();
        }
        if(len != frameHead.getLength()){
            throw new RuntimeException(String.format("读取到的长度(len=%d) != (length=%d)", len, frameHead.getLength()));
        }
        FrameInfo frameInfo = new FrameInfo();
        frameInfo.setFrameHead(frameHead);
        frameInfo.setMsg(new String(buf.array()));
        return frameInfo;
    }

    /**
     * 读取帧头信息
     * @param in
     * @return
     * @throws IOException
     */
    private synchronized FrameHead decodeFrameHead(InputStream in) throws IOException {
        byte[] buf = new byte[FRAME_HEAD_LENGTH];
        int offset = 0, total = FRAME_HEAD_LENGTH, len;
        while (offset < total){
            len = in.read(buf, offset, total);
            if(len == -1){
                throw new DisconnectException();
            }
            offset += len;
        }
        return new FrameHead(buf[0], restoreLength(buf[1], buf[2], buf[3], buf[4]));
    }

    private synchronized FrameHead decodeFrameHead(SocketChannel socketChannel) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(FRAME_HEAD_LENGTH);
        int len = socketChannel.read(buf);
        if(len == -1){
            socketChannel.close();
            throw new DisconnectException();
        }
        if(len != FRAME_HEAD_LENGTH){
            throw new RuntimeException("读取帧头出错");
        }
        return new FrameHead(buf.get(0), restoreLength(buf.get(1), buf.get(2), buf.get(3), buf.get(4)));
    }

    private int restoreLength(byte b1, byte b2, byte b3, byte b4){
        return transform(b1) << 24 | transform(b2) << 16 | transform(b3) << 8 | transform(b4);
    }

    /*
    * 因为java中byte是有符号的，所以正数最大只能到127，那么需要通过某种方法复原
    * */
    private int transform(byte b){
        return b < 0 ? b + 256 : b;
    }

    private void setFrameHead(byte[] bytes, int type, int length){
        if(bytes.length < 5) {
            throw new RuntimeException("帧大小不能小于5");
        }
        //type
        bytes[0] = (byte) (type & 0xff);

        //length
        bytes[1] = (byte) ((length >> 24) & 0xff);
        bytes[2] = (byte) ((length >> 16) & 0xff);
        bytes[3] = (byte) ((length >> 8) & 0xff);
        bytes[4] = (byte) (length & 0xff);
    }

    private void setContent(byte[] bytes, byte[] msg){
        if(bytes.length < FRAME_HEAD_LENGTH + msg.length){
            throw new RuntimeException("字节数组空间不足");
        }
        System.arraycopy(msg, 0, bytes, FRAME_HEAD_LENGTH, msg.length);
    }
}
