package com.acme.server;

import com.acme.common.SocketIface;
import com.acme.protocol.FrameInfo;

/**
 * @author acme
 * @date 2019/8/17 2:47 PM
 */
public interface Server extends SocketIface{

    /**
     * 发送数据
     * @param key
     * @param content
     */
    void send(String key, byte[] content);

    /**
     * 接收指定key的数据
     * @param key
     * @return
     */
    FrameInfo receive(String key);

    /**
     * 启动服务器
     */
    void start();
}