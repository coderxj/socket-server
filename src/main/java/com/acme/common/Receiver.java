package com.acme.common;

import com.acme.protocol.FrameInfo;

/**
 * @author acme
 * @date 2019/8/19 4:00 PM
 */
public interface Receiver {
    void recv(FrameInfo frameInfo, String key);
}
