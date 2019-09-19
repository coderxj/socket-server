package com.acme.common;

import com.acme.protocol.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;

/**
 * @author acme
 * @date 2019/8/28 6:53 PM
 */
@Getter
@Setter
public class AIOSocketChannelHandle extends AbstractSocketChannelHandle{
    private AsynchronousSocketChannel socketChannel;

    public AIOSocketChannelHandle(AsynchronousSocketChannel socketChannel, Protocol protocol){
        super(protocol);
        this.socketChannel = socketChannel;
    }

    public AIOSocketChannelHandle(AsynchronousSocketChannel socketChannel, int readBufferSize, int writeBufferSize){
        super(readBufferSize, writeBufferSize);
        this.socketChannel = socketChannel;
    }

    public void receive(){
        readBuffer.flip();

        //开始读帧头, 缓冲区可读数据是否大于等于帧头长度
        if((StateEnum.BEGIN_RECV_HEAD == stateEnum ||
                StateEnum.END_RECV_CONTENT == stateEnum) &&
                readBuffer.remaining() >= Protocol.FRAME_HEAD_LENGTH){
            startReadHead();
        }

        //开始读内容
        if((StateEnum.END_RECV_HEAD == stateEnum ||
                StateEnum.BEGIN_RECV_CONTENT == stateEnum) &&
                readBuffer.remaining() > 0){
            startReadContent();
        }

        readBuffer.clear();
    }

    public void send(byte[] content, byte type, CompletionHandler<Integer, Object> handleWrite, Object attachment){
        content = protocol.encodeFrame(content, type);
        int total = content.length;
        int pos = 0;
        while (total > 0){
            writeBuffer.clear();
            if(writeBuffer.remaining() > 0){
                int writeLen = writeBuffer.remaining() > total ? total : writeBuffer.remaining();
                writeBuffer.put(content, pos, writeLen);
                pos += writeLen;
                total -= writeLen;
                writeBuffer.flip();
                socketChannel.write(writeBuffer, attachment, handleWrite);
            }
        }
    }

    @Override
    public void close() {
        try {
            socketChannel.close();
        } catch (IOException e) {
            //nothing to do
            consoleLog.error("close channel failed e:{}", e);
        }
    }
}
