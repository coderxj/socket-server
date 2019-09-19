package com.acme.client;

import com.acme.exception.DisconnectException;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;
import com.acme.common.Receiver;
import com.acme.common.NIOSocketChannelHandle;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/19 7:59 PM
 */
public class NIOClient extends AbstractClient implements Client{
    private SocketChannel socketChannel;
    private NIOSocketChannelHandle channelHandle;
    private SelectionKey selectionKey;
    private Selector selector;
    private Object lock = new Object();

    public NIOClient(){

    }

    public NIOClient(String key){
        this.key = key;
    }

    public NIOClient(String key, Protocol protocol){
        this.key = key;
        this.protocol = protocol;
    }

    public NIOClient(String key, String host, int port){
        this.key = key;
        this.host = host;
        this.port = port;
    }

    @Override
    public void send(byte[] content) {
        if(!isConnected){
            waitForConnect();
        }
        channelHandle.setReadyToSend(content);
        channelHandle.getSelectionKey().interestOps(channelHandle.getSelectionKey().interestOps() | SelectionKey.OP_WRITE);
        channelHandle.getSelectionKey().selector().wakeup();
    }

    @Override
    public void receive(Receiver receiver, Integer pollTime) {
        new Thread(() -> recv(receiver, pollTime)).start();
    }

    @Override
    public void connect() {
        try {
            checkProtocol();
            socketChannel = SocketChannel.open();
            socketChannel.configureBlocking(false);
            socketChannel.connect(new InetSocketAddress(host, port));
            selector = Selector.open();
            selectionKey = socketChannel.register(selector, SelectionKey.OP_CONNECT);
            new Thread(new HandleTask(() -> selectEvent(selector))).start();
            scheduledExecutorService.scheduleWithFixedDelay(new HandleTask(this::heartBeatTask), 0, 2, TimeUnit.SECONDS);
            waitForConnect();
        } catch (IOException e) {
            consoleLog.error("Connect to the server failed. {}:{}, e:{}", host, port, e);
        }
    }

    private void selectEvent(Selector selector){
        while (true){
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()){
                    SelectionKey selectionKey = iterator.next();
                    iterator.remove();
                    if(selectionKey.isConnectable()){
                        handleConnect();
                    } else if(selectionKey.isReadable()){
                        handleRead();
                    } else if(selectionKey.isWritable()){
                        handleWrite();
                    }
                }
            } catch (DisconnectException | ConnectException de){
                isConnected = false;
                reConnect();
            } catch (Throwable e){
                consoleLog.error("an unknown error:{}", e);
            }
        }
    }

    private void handleConnect() throws IOException {
        while (!socketChannel.finishConnect()){}
        channelHandle = new NIOSocketChannelHandle(socketChannel, protocol);
        channelHandle.setReceiver(new CustomReceiver());
        channelHandle.setSelectionKey(selectionKey);
        consoleLog.info("Connect to the server success. {}:{}", host, port);
        isConnected = true;
        register();
        wakeup();
    }

    private void handleRead(){
        try {
            if(isConnected && channelHandle.receive() == -1){
                consoleLog.error("disconnect to the server.");
                isConnected = false;
                reConnect();
            }
        } catch (ClosedChannelException e){
            consoleLog.error("channel has closed. e:{}", e);
            isConnected = false;
            reConnect();
        } catch (Throwable e) {
            consoleLog.error("an unknown error:{}", e);
        }
    }

    private void handleWrite(){
        try {
            channelHandle.send(channelHandle.getReadyToSend(), Protocol.CONTENT_TYPE);
        } catch (IOException e) {
            consoleLog.error("disconnect to the server. e:{}", e);
            isConnected = false;
            reConnect();
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e);
        }
    }

    protected void register(){
        try {
            channelHandle.send(key.getBytes(), Protocol.KEY_TYPE);
            consoleLog.info("Registered successfully.");
        } catch (IOException e) {
            consoleLog.error("Fail to register. e:{}", e);
        } catch (Throwable e){
            consoleLog.error("an unknown error:{}", e);
        }

    }

    private void heartBeatTask(){
        try {
            if(isConnected){
                channelHandle.send("h".getBytes(), Protocol.HEART_BEAT_TYPE);
                consoleLog.debug("Send a heartbeat");
            }
        } catch (IOException e) {
            consoleLog.error("disconnect to the server. e:{}", e);
            isConnected = false;
            reConnect();
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

    /*
     * 注意多线程下：这里不能这样用锁protected synchronized void reConnect,
     * 这相当于synchronized(this)
     * 假设当reConnect释放this锁后，又迅速被readTask的decodeFrame锁住this
     * 那么writeTask、heartBeatTask可能就会造成死锁，除非有消息进来，decodeFrame释放
     * this 锁，那么writeTask、heartBeatTask才有可能获取this锁。
     * */
    protected void reConnect(){
        synchronized (NIOClient.class){
            if(isConnected) return;
            try {
                Thread.sleep(1000);
                consoleLog.debug("Try to reconnect to the server on {}:{}...", host, port);
                socketChannel.close();
                socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                socketChannel.connect(new InetSocketAddress(host, port));
                selector.wakeup();
                selectionKey = socketChannel.register(channelHandle.getSelectionKey().selector(), SelectionKey.OP_CONNECT);
            } catch (Throwable e) {
                //nothing to do
            }
        }
    }

    private void waitForConnect(){
        synchronized (lock){
            try {
                lock.wait();
            } catch (InterruptedException e) {
                //nothing to do
            }
        }
    }

    private void wakeup(){
        synchronized (lock){
            lock.notifyAll();
        }
    }
}
