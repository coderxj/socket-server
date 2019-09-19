package com.acme.server;

import com.acme.common.NIOSocketChannelHandle;
import com.acme.common.Receiver;
import com.acme.exception.DisconnectException;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/22 3:45 PM
 */
public class NIOServer extends AbstractServer implements Server{

    public NIOServer(){
        this.threadPool = new ThreadPoolExecutor(4, 100, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    public NIOServer(Protocol protocol){
        this();
        this.protocol = protocol;
    }

    public NIOServer(int port){
        this();
        this.port = port;
    }

    @Override
    public void send(String key, byte[] content) {
        NIOSocketChannelHandle channelHandle = (NIOSocketChannelHandle) keyMap.get(key);
        if(channelHandle != null){
            channelHandle.setReadyToSend(content);
            channelHandle.getSelectionKey().interestOps(channelHandle.getSelectionKey().interestOps() | SelectionKey.OP_WRITE);
            channelHandle.getSelectionKey().selector().wakeup();
        }
    }

    @Override
    public FrameInfo receive(String key) {
        if(isUseGlobalQueue){
            throw new RuntimeException("Does not support from the global queue with specified key!");
        }
        NIOSocketChannelHandle channelHandle = (NIOSocketChannelHandle)keyMap.get(key);
        if(channelHandle == null || channelHandle.getNormFrameQueue().isEmpty()){
            return null;
        }
        return channelHandle.getNormFrameQueue().removeFirst();
    }

    @Override
    public void receive(Receiver receiver, Integer pollTime) {
        threadPool.execute(() -> recv(receiver, pollTime));
    }

    @Override
    public void start() {
        try {
            checkProtocol();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.socket().bind(new InetSocketAddress(port));
            consoleLog.info("NIOServer start success on {}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            serverSocketChannel.configureBlocking(false);
            Selector selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            threadPool.execute(new HandleTask(() -> selectEvent(selector)));
            waitForAccept();
        } catch (IOException e) {
            consoleLog.error("NIOServer start failed, e:{}", e);
        }
    }

    private void selectEvent(Selector selector){
        while (true){
            SelectionKey selectionKey = null;
            try {
                selector.select();
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()){
                    selectionKey = iterator.next();
                    iterator.remove();
                    if(selectionKey.isAcceptable()){
                        handleAccept(selectionKey);
                    } else if(selectionKey.isReadable()){
                        handleRead(selectionKey);
                    } else if(selectionKey.isWritable()){
                        handleWrite(selectionKey);
                    }
                }
            } catch (DisconnectException de){
                consoleLog.warn("key:{} is disconnect", getKey(selectionKey));
                keyMap.remove(getKey(selectionKey));
            } catch (Throwable e){
                consoleLog.error("an unknown error:{}", e);
            }
        }
    }

    private void handleAccept(SelectionKey selectionKey) throws IOException {
        SocketChannel socketChannel = ((ServerSocketChannel)selectionKey.channel()).accept();
        NIOSocketChannelHandle channelHandle = new NIOSocketChannelHandle(socketChannel, protocol);
        channelHandle.setKey(String.valueOf(socketChannel.hashCode()));
        channelHandle.setSelectionKey(selectionKey);
        channelHandle.setReceiver(new CustomReceiver<>(channelHandle));
        if(isUseDefaultKey){
            keyMap.put(String.valueOf(socketChannel.hashCode()), channelHandle);
        }
        socketChannel.configureBlocking(false);
        channelHandle.setSelectionKey(socketChannel.register(selectionKey.selector(), SelectionKey.OP_READ, channelHandle));
    }

    private void handleRead(SelectionKey selectionKey) throws IOException {
        NIOSocketChannelHandle channelHandle = (NIOSocketChannelHandle)selectionKey.attachment();
        int len = channelHandle.receive();
        if(len == -1){
            channelHandle.getSocketChannel().close();
            throw new DisconnectException();
        }
    }

    private void handleWrite(SelectionKey selectionKey) throws IOException {
        NIOSocketChannelHandle channelHandle = (NIOSocketChannelHandle) selectionKey.attachment();
        channelHandle.send(channelHandle.getReadyToSend(), Protocol.CONTENT_TYPE);
    }

    private String getKey(SelectionKey selectionKey){
        if(selectionKey != null){
            NIOSocketChannelHandle channelHandle = (NIOSocketChannelHandle) selectionKey.attachment();
            if(channelHandle != null){
                return channelHandle.getKey();
            }
        }
        return "";
    }
}
