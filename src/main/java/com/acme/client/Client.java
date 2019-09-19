package com.acme.client;

import com.acme.common.SocketIface;

/**
 * @author acme
 * @date 2019/8/19 7:52 PM
 */
public interface Client extends SocketIface{

    /**
     * 发送数据
     * @param content
     */
    void send(byte[] content);

    /**
     * 连接服务器
     */
    void connect();
}
