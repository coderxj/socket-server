package com.acme.server;

import com.acme.common.AIOSocketChannelHandle;
import com.acme.common.Receiver;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/28 3:35 PM
 */
public class AIOServer extends AbstractServer implements Server{

    private AsynchronousServerSocketChannel serverSocketChannel;
    private HandleWrite handleWrite;

    public AIOServer() {
        this.threadPool = new ThreadPoolExecutor(4, 100, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
        this.handleWrite = new HandleWrite();
    }

    public AIOServer(Protocol protocol) {
        this();
        this.protocol = protocol;
    }

    @Override
    public void send(String key, byte[] content) {
        AIOSocketChannelHandle channelHandle = (AIOSocketChannelHandle) keyMap.get(key);
        if(channelHandle != null){
            channelHandle.send(content, Protocol.CONTENT_TYPE, handleWrite, channelHandle);
        }
    }

    @Override
    public FrameInfo receive(String key) {
        AIOSocketChannelHandle channelHandle = (AIOSocketChannelHandle) keyMap.get(key);
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
            serverSocketChannel = AsynchronousServerSocketChannel.open().bind(new InetSocketAddress(port));
            consoleLog.info("AIOServer start success on {}:{}", InetAddress.getLocalHost().getHostAddress(), port);
            serverSocketChannel.accept(null, new HandleAccpet());
            waitForAccept();
        } catch (IOException e) {
            consoleLog.error("AIOServer start failed, e:{}", e);
        }
    }

    class HandleAccpet implements CompletionHandler<AsynchronousSocketChannel, Object> {

        @Override
        public void completed(AsynchronousSocketChannel result, Object attachment) {
            serverSocketChannel.accept(attachment, this);
            AIOSocketChannelHandle channelHandle = new AIOSocketChannelHandle(result, protocol);
            channelHandle.setKey(String.valueOf(result.hashCode()));
            channelHandle.setReceiver(new CustomReceiver<>(channelHandle));
            if(isUseDefaultKey){
                keyMap.put(channelHandle.getKey(), channelHandle);
                consoleLog.info("key:{} connected", channelHandle.getKey());
                checkClientAndNotify();
            }
            result.read(channelHandle.getReadBuffer(), channelHandle, new HandleRead());
        }

        @Override
        public void failed(Throwable exc, Object attachment) {
            consoleLog.error("e:{}", exc);
        }
    }

    class HandleRead implements CompletionHandler<Integer, AIOSocketChannelHandle> {

        @Override
        public void completed(Integer result, AIOSocketChannelHandle channelHandle) {
            if(result > 0){
                channelHandle.receive();
            } else if(result == -1){
                //说明已经断开连接
                consoleLog.warn("key:{} is disconnect", channelHandle.getKey());
                keyMap.remove(channelHandle.getKey());
                return;
            }
            channelHandle.getSocketChannel().read(channelHandle.getReadBuffer(), channelHandle, this);
        }

        @Override
        public void failed(Throwable exc, AIOSocketChannelHandle attachment) {
            consoleLog.error("e:{}", exc);
        }
    }

    class HandleWrite implements CompletionHandler<Integer, Object> {

        @Override
        public void completed(Integer result, Object o) {

        }

        @Override
        public void failed(Throwable exc, Object o) {
            consoleLog.warn("key:{} is disconnect", ((AIOSocketChannelHandle)o).getKey());
        }
    }
}
