package com.acme.server;

import com.acme.protocol.FrameInfo;
import lombok.Getter;
import lombok.Setter;

import java.net.Socket;
import java.util.LinkedList;

/**
 * @author acme
 * @date 2019/8/17 3:22 PM
 */
@Setter
@Getter
public class SocketInfo{
    //客户端套接字
    Socket socket;

    //正常数据接收帧队列
    LinkedList<FrameInfo> normFrameQueue;

    //心跳数据接收帧队列
    LinkedList<FrameInfo> heartBeatFrameQueue;

    public SocketInfo(Socket socket){
        this.socket = socket;
        this.normFrameQueue = new LinkedList<>();
        this.heartBeatFrameQueue = new LinkedList<>();
    }
}
