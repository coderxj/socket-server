package com.acme.client;

import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;
import com.acme.common.AIOSocketChannelHandle;
import com.acme.common.Receiver;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.LinkedList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/28 4:01 PM
 */
public class AIOClient extends AbstractClient implements Client{

    private AIOSocketChannelHandle channelHandle;

    private HandleWrite handleWrite;

    /*
     *AIO同一个channel写的时候，需要等待上一个写完成，才能进行下一个写
     */
    private volatile boolean writing;

    /*
    * 发送队列
    * */
    private LinkedList<byte[]> sendQueue;

    public AIOClient(){
        this.handleWrite = new HandleWrite();
        this.sendQueue = new LinkedList<>();
        this.writing = false;
    }

    public AIOClient(String key){
        this();
        this.key = key;
    }

    public AIOClient(String key, Protocol protocol){
        this();
        this.key = key;
        this.protocol = protocol;
    }

    public AIOClient(String key, String host, int port){
        this();
        this.key = key;
        this.host = host;
        this.port = port;
    }

    @Override
    public void send(byte[] content) {
        sendQueue.add(content);
        synchronized (sendQueue){
            sendQueue.notify();
        }
    }

    @Override
    public void receive(Receiver receiver, Integer pollTime) {
        new Thread(() -> recv(receiver, pollTime)).start();
    }

    @Override
    public void connect() {
        try {
            checkProtocol();
            AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
            socketChannel.connect(new InetSocketAddress(host, port)).get();
            channelHandle = new AIOSocketChannelHandle(socketChannel, protocol);
            channelHandle.setReceiver(new CustomReceiver());
            consoleLog.info("Connect to the server success. {}:{}", host, port);
            isConnected = true;
            register();
            new Thread(new HandleTask(this::readTask)).start();
            scheduledExecutorService.scheduleWithFixedDelay(new HandleTask(this::heartBeatTask), 0, 2, TimeUnit.SECONDS);
            new Thread(new HandleTask(this::writeTask)).start();
        } catch (IOException e) {
            consoleLog.error("open failed with e:{}", e);
        } catch (InterruptedException | ExecutionException e) {
            consoleLog.error("connect failed with e:{}", e);
        }
    }

    @Override
    protected void register() {
        if(isConnected){
            channelHandle.send(key.getBytes(), Protocol.KEY_TYPE, handleWrite, Protocol.KEY_TYPE);
        }
    }

    private void readTask(){
        while (true){
            try {
                if(isConnected){
                    channelHandle.getSocketChannel().read(channelHandle.getReadBuffer()).get();
                    channelHandle.receive();
                }
            } catch (InterruptedException | ExecutionException e) {
                consoleLog.error("(readTask) disconnect to the server. e:{}", e.getMessage());
                isConnected = false;
                reConnect();
            }
        }
    }

    private void writeTask(){
        while (true){
            synchronized (sendQueue){
                if(sendQueue.isEmpty()){
                    try {
                        sendQueue.wait();
                    } catch (InterruptedException e) {
                        //nothing to do
                        continue;
                    }
                }
            }

            if(writing){
                continue;
            }
            writing = true;
            channelHandle.send(sendQueue.removeFirst(), Protocol.CONTENT_TYPE, handleWrite, Protocol.CONTENT_TYPE);
        }
    }

    private void heartBeatTask(){
        try {
            if(!writing){
                writing = true;
                channelHandle.send("h".getBytes(), Protocol.HEART_BEAT_TYPE, handleWrite, Protocol.HEART_BEAT_TYPE);
            }
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e);
        }
    }

    private class CustomReceiver implements Receiver {
        @Override
        public void recv(FrameInfo frameInfo, String key) {
            normFrameQueue.add(frameInfo);
        }
    }

    class HandleWrite implements CompletionHandler<Integer, Object>{

        @Override
        public void completed(Integer result, Object attachment) {
            writing = false;
            if(((Byte)attachment) == Protocol.KEY_TYPE && result > 0){
                consoleLog.debug("Registered successfully.");
            } else if(((Byte)attachment) == Protocol.CONTENT_TYPE && result > 0){
                consoleLog.debug("Send content successfully.");
            } else if(((Byte)attachment) == Protocol.HEART_BEAT_TYPE && result > 0){
                consoleLog.debug("Send a heartbeat successfully");
            }
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            consoleLog.error("(HandleWrite) disconnect to the server. e:{}", exc.getMessage());
            isConnected = false;
            reConnect();
        }
    }

    protected void reConnect(){
        synchronized (AIOClient.class){
            if(isConnected) return;
            try {
                channelHandle.getSocketChannel().close();
            } catch (IOException e) {
                consoleLog.error("socket close failed, e:{}", e.getMessage());
            }
            long b = System.currentTimeMillis();
            while (!isConnected){
                try {
                    consoleLog.debug("Try to reconnect to the server on {}:{}...", host, port);
                    AsynchronousSocketChannel socketChannel = AsynchronousSocketChannel.open();
                    socketChannel.connect(new InetSocketAddress(host, port)).get();
                    channelHandle.setSocketChannel(socketChannel);
                    isConnected = true;
                    register();
                    consoleLog.debug("Reconnect the server successfully on {}:{}, cost time:{}", host, port, (System.currentTimeMillis() - b));
                    break;
                } catch (InterruptedException | ExecutionException | IOException e) {
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
