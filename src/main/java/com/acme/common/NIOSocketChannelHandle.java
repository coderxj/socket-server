package com.acme.common;

import com.acme.protocol.Protocol;
import lombok.Getter;
import lombok.Setter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.LinkedList;

/**
 * @author acme
 * @date 2019/8/22 4:22 PM
 */
@Setter
@Getter
public class NIOSocketChannelHandle extends AbstractSocketChannelHandle{
    private SocketChannel socketChannel;

    private SelectionKey selectionKey;

    private byte[] readyToSend;

    public NIOSocketChannelHandle(){
    }

    public NIOSocketChannelHandle(SocketChannel socketChannel, Protocol protocol){
       this(socketChannel);
       this.protocol = protocol;
    }

    public NIOSocketChannelHandle(SocketChannel socketChannel){
        this.socketChannel = socketChannel;
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    }

    public NIOSocketChannelHandle(SocketChannel socketChannel, int readBufferSize, int writeBufferSize){
        this();
        this.socketChannel = socketChannel;
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
        this.writeBuffer = ByteBuffer.allocate(writeBufferSize);
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


    public int receive() throws IOException {
        int len = readBuffer.remaining();
        if(len == 0 || readBuffer.position() == 0){
            readBuffer.clear();
            len = socketChannel.read(readBuffer);
            readBuffer.flip();
        }
        if(len > 0){

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
        }
        return len;
    }

    public void send(byte[] content, byte type) throws IOException {
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
                socketChannel.write(writeBuffer);
            }
        }
        if(selectionKey != null){
            selectionKey.interestOps(selectionKey.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }
}
