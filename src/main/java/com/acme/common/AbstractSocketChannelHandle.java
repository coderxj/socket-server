package com.acme.common;

import com.acme.Log.ConsoleLog;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameHead;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;
import lombok.Getter;
import lombok.Setter;
import java.nio.ByteBuffer;
import java.util.LinkedList;

import static com.acme.protocol.FixedLengthProtocol.FRAME_HEAD_LENGTH;

/**
 * @author acme
 * @date 2019/8/30 4:25 PM
 */
@Setter
@Getter
public abstract class AbstractSocketChannelHandle{
    //8k
    protected final int BUFFER_SIZE = 1024 * 8;

    //编码协议
    Protocol protocol;

    //读缓冲区
    protected ByteBuffer readBuffer;

    //写缓冲区
    protected ByteBuffer writeBuffer;

    protected String key;

    //接收数据状态
    protected StateEnum stateEnum;

    //正常数据接收帧队列
    protected LinkedList<FrameInfo> normFrameQueue;

    //心跳数据接收帧队列
    protected LinkedList<FrameInfo> heartBeatFrameQueue;

    //接收器，专门用来处理，接收到一帧后，如何处理
    protected Receiver receiver;

    //帧头
    protected byte[] head;

    //内容
    protected byte[] content;

    //长度
    protected int contentLength;

    //当前读到的位置
    protected int curRead;

    ConsoleLog consoleLog;

    public AbstractSocketChannelHandle(){
        this.readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.writeBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        this.stateEnum = StateEnum.BEGIN_RECV_HEAD;
        this.normFrameQueue = new LinkedList<>();
        this.heartBeatFrameQueue = new LinkedList<>();
        this.key = "";
        this.consoleLog = new ConsoleLog();
    }

    public AbstractSocketChannelHandle(Protocol protocol){
        this();
        this.protocol = protocol;
    }

    public AbstractSocketChannelHandle(int readBufferSize, int writeBufferSize){
        this.readBuffer = ByteBuffer.allocate(readBufferSize);
        this.writeBuffer = ByteBuffer.allocate(writeBufferSize);
        this.stateEnum = StateEnum.BEGIN_RECV_HEAD;
        this.normFrameQueue = new LinkedList<>();
        this.heartBeatFrameQueue = new LinkedList<>();
        this.key = "";
        this.consoleLog = new ConsoleLog();
    }

    protected void startReadHead(){
        if(head == null || head.length < FRAME_HEAD_LENGTH){
            head = new byte[FRAME_HEAD_LENGTH];
        }
        readBuffer.get(head);
        FrameHead frameHead = protocol.decodeHead(head);
        if(content == null || content.length < frameHead.getLength()){
            content = new byte[frameHead.getLength()];
        }
        contentLength = frameHead.getLength();
        //结束读帧头
        stateEnum = StateEnum.END_RECV_HEAD;
    }

    protected void startReadContent(){
        int remainRead = contentLength - curRead;
        int readLen = readBuffer.remaining() > remainRead ? remainRead : readBuffer.remaining();
        readBuffer.get(content, curRead, readLen);
        curRead += readLen;

        //已读取长度是否等于内容长度 -> 结束
        if(curRead == contentLength){
            FrameInfo frameInfo = protocol.decodeFrame(content, contentLength, protocol.decodeHead(head));
            frameInfo.setKey(key);
            receiver.recv(frameInfo, key);
            curRead = 0;
            stateEnum = StateEnum.END_RECV_CONTENT;
        } else {
            stateEnum = StateEnum.BEGIN_RECV_CONTENT;
        }
    }

    abstract public void close();
}
