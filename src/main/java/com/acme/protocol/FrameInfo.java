package com.acme.protocol;

import lombok.Getter;
import lombok.Setter;

/**
 * @author acme
 * @date 2019/8/17 2:37 PM
 */
@Setter
@Getter
public class FrameInfo {
    //帧头信息
    FrameHead frameHead;

    //帧的内容
    String msg;

    //来源客户端唯一标识
    String key;

    @Override
    public String toString() {
        return "key:" + key + "\ntype: " + frameHead.getType() + "\nlength: " + frameHead.getLength() + "\nmsg:" + msg;
    }
}
