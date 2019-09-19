package com.acme.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SocketChannel;

/**
 * @author acme
 * @date 2019/8/17 2:31 PM
 */
public interface Protocol {
    //帧头长度
    int FRAME_HEAD_LENGTH = 5;

    //心跳类型
    byte HEART_BEAT_TYPE = 1;
    //正常内容
    byte CONTENT_TYPE = 2;
    //key
    byte KEY_TYPE = 3;

    byte[] encodeFrame(byte[] msg, int type);

    FrameInfo decodeFrame(InputStream in) throws IOException;

    FrameInfo decodeFrame(SocketChannel socketChannel) throws IOException;

    FrameHead decodeHead(byte[] bytes);

    FrameInfo decodeFrame(byte[] bytes, int len, FrameHead frameHead);

    Protocol clone();
}
