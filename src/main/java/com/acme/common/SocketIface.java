package com.acme.common;

/**
 * @author acme
 * @date 2019/9/3 3:50 PM
 */
public interface SocketIface {

    /**
     * 通过回调函数接收数据(异步)
     * @param receiver
     */
    void receive(Receiver receiver, Integer pollTime);
}
