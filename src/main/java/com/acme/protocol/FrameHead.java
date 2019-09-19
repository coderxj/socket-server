package com.acme.protocol;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * @author acme
 * @date 2019/8/17 2:43 PM
 */
@Setter
@Getter
@AllArgsConstructor
public class FrameHead {
    //帧的类型，心跳/内容
    int type;

    //帧的内容长度
    int length;
}
