package com.acme.client;

import com.acme.exception.DisconnectException;
import com.acme.protocol.Protocol;
import com.acme.common.Receiver;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/19 7:59 PM
 */
public class BIOClient extends AbstractClient implements Client{

    private Socket socket;

    public BIOClient(){

    }

    public BIOClient(String key){
        this.key = key;
    }

    public BIOClient(String key, Protocol protocol){
        this.key = key;
        this.protocol = protocol;
    }

    public BIOClient(String key, String host, int port){
        this.key = key;
        this.host = host;
        this.port = port;
    }

    @Override
    public void send(byte[] content) {
        new Thread(new HandleTask(() -> writeTask(content))).start();
    }

    @Override
    public void receive(Receiver receiver, Integer pollTime) {
        new Thread(() -> recv(receiver, pollTime)).start();
    }

    @Override
    public void connect() {
        try {
            checkProtocol();
            socket = new Socket(host, port);
            if(!socket.isConnected()){
                throw new RuntimeException("连接失败");
            }
            consoleLog.info("Connect to the server success. {}:{}", host, port);
            isConnected = true;
            register();
            new Thread(new HandleTask(this::readTask)).start();
            scheduledExecutorService.scheduleWithFixedDelay(new HandleTask(this::heartBeatTask), 0, 1, TimeUnit.SECONDS);
        } catch (IOException e) {
            consoleLog.error("Connect to the server failed. {}:{}, e:{}", host, port, e.getMessage());
        }
    }

    protected void register(){
        try {
            OutputStream out = socket.getOutputStream();
            out.write(protocol.encodeFrame(key.getBytes(), Protocol.KEY_TYPE));
            out.flush();
            consoleLog.info("Registered successfully.");
        } catch (IOException e) {
            consoleLog.error("Fail to register. e:{}", e.getMessage());
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e.getMessage());
        }
    }

    private void readTask(){
        while (true){
            try {
                InputStream in = socket.getInputStream();
                while (true){
                    normFrameQueue.add(protocol.decodeFrame(in));
                }
            } catch (DisconnectException e){
                consoleLog.error("(readTask) disconnect to the server. e:{}", e.getMessage());
                isConnected = false;
                reConnect();
            } catch (Throwable e) {
                consoleLog.error("an unknown error:{}", e.getMessage());
            }
        }
    }

    private void writeTask(byte[] msg){
        try {
            OutputStream out = socket.getOutputStream();
            out.write(protocol.encodeFrame(msg, Protocol.CONTENT_TYPE));
            out.flush();
        } catch (IOException e) {
            consoleLog.error("(writeTask) disconnect to the server. e:{}", e.getMessage());
            isConnected = false;
            reConnect();
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e.getMessage());
        }
    }

    private void heartBeatTask(){
        try {
            OutputStream out = socket.getOutputStream();
            out.write(protocol.encodeFrame("h".getBytes(), Protocol.HEART_BEAT_TYPE));
            out.flush();
            consoleLog.debug("Send a heartbeat");
        } catch (IOException e) {
            consoleLog.error("(heartBeatTask) disconnect to the server. e:{}", e.getMessage());
            isConnected = false;
            reConnect();
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e.getMessage());
        }
    }

    protected void reConnect(){
        synchronized (BIOClient.class){
            if(isConnected) return;
            try {
                socket.close();
            } catch (IOException e) {
                consoleLog.error("socket close failed, e:{}", e.getMessage());
            }
            long b = System.currentTimeMillis();
            while (!isConnected){
                try {
                    consoleLog.debug("Try to reconnect to the server on {}:{}...", host, port);
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(host, port));
                    register();
                    isConnected = true;
                    consoleLog.debug("Reconnect the server successfully on {}:{}, cost time:{}", host, port, (System.currentTimeMillis() - b));
                    break;
                } catch (IOException e) {
                    //nothing to do
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //nothing to do
                }
                if(System.currentTimeMillis() - b > 3600 * 1000){
                    consoleLog.error("Fail try to reconnect to the server on {}:{}. cost time:{}", host, port, (System.currentTimeMillis() - b));
                }
            }
        }
    }
}
