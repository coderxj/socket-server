package com.acme.exception;

/**
 * @author acme
 * @date 2019/8/23 11:41 AM
 */
public class DisconnectException extends RuntimeException {
    public DisconnectException(){
        super("Disconnect");
    }

    public DisconnectException(String msg){
        super(msg);
    }
}
