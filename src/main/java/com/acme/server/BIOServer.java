package com.acme.server;

import com.acme.common.Receiver;
import com.acme.exception.DisconnectException;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author acme
 * @date 2019/8/17 2:47 PM
 */
public class BIOServer extends AbstractServer implements Server{
    private ServerSocket serverSocket;

    public BIOServer(){
        this.threadPool = new ThreadPoolExecutor(50, 100, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }

    public BIOServer(Protocol protocol){
        this();
        this.protocol = protocol;
    }

    public BIOServer(int port){
        this();
        this.port = port;
    }

    @Override
    public void send(String key, byte[] content) {
        threadPool.execute(new HandleTask(() -> writeTask(key, content, Protocol.CONTENT_TYPE)));
    }

    @Override
    public FrameInfo receive(String key) {
        SocketInfo socketInfo = (SocketInfo) keyMap.get(key);
        if(socketInfo == null || socketInfo.getNormFrameQueue().isEmpty()){
            return null;
        }
        return socketInfo.getNormFrameQueue().removeFirst();
    }

    @Override
    public void receive(Receiver receiver, Integer pollTime) {
        threadPool.execute(() -> recv(receiver, pollTime));
    }

    @Override
    public void start() {
        String localhost = "";
        try {
            checkProtocol();
            serverSocket = new ServerSocket(port);
            localhost = InetAddress.getLocalHost().getHostAddress();
            threadPool.execute(new HandleTask(this::acceptTask));
            consoleLog.info("BIOServer start success on {}:{}", localhost, port);
            waitForAccept();
        } catch (IOException e) {
            consoleLog.error("BIOServer start failed on {}:{}, e:{}", localhost, port, e.getMessage());
            try {
                serverSocket.close();
            } catch (IOException e1) {
                consoleLog.error("serverSocket close failed, e:{}", e1.getMessage());
            }
            threadPool.shutdownNow();
        }
    }

    public List<String> getKeys(){
        return new ArrayList<>(keyMap.keySet());
    }

    //处理客户端连接
    private void acceptTask(){
        while (true){
            try {
                Socket socket = serverSocket.accept();
                threadPool.execute(new HandleTask(() -> readTask(new SocketInfo(socket))));
            } catch (Throwable e){
                consoleLog.error("accept failed e:{}", e.getMessage());
            }
        }
    }

    //处理读数据
    private void readTask(SocketInfo socketInfo){
        Socket socket = null;
        String key = "";
        try {
            if(socketInfo == null){
                consoleLog.error("socketInfo is null");
                return;
            }
            if(isUseDefaultKey){
                keyMap.put(String.valueOf(socketInfo.hashCode()), socketInfo);
            }
            socket = socketInfo.getSocket();;
            if(socket == null){
                consoleLog.error("socket is null");
                return;
            }
            InputStream in = new BufferedInputStream(socket.getInputStream());
            Protocol protocol = getProtocol().clone();
            while (true){
                FrameInfo frameInfo = protocol.decodeFrame(in);
                if(frameInfo.getFrameHead().getType() == Protocol.HEART_BEAT_TYPE && isReceiveHeartBeat){
                    frameInfo.setKey(key);
                    if(isUseGlobalQueue){
                        frameQueue.add(frameInfo);
                    } else {
                        socketInfo.getHeartBeatFrameQueue().add(frameInfo);
                    }
                } else if(frameInfo.getFrameHead().getType() == Protocol.CONTENT_TYPE){
                    frameInfo.setKey(key);
                    if(isUseGlobalQueue){
                        frameQueue.add(frameInfo);
                    } else {
                        socketInfo.getNormFrameQueue().add(frameInfo);
                    }
                } else if(frameInfo.getFrameHead().getType() == Protocol.KEY_TYPE && !isUseDefaultKey){
                    key = frameInfo.getMsg();
                    keyMap.put(frameInfo.getMsg(), socketInfo);
                    checkClientAndNotify();
                    consoleLog.info("key:{}已连接", frameInfo.getMsg());
                }
            }
        } catch (DisconnectException de){
            consoleLog.warn("key:{} is disconnect", key);
        } catch (Throwable e) {
            consoleLog.error("an unknown error:{}", e.getMessage());
        } finally {
            clear(key, socket);
        }
    }

    //处理写数据
    private void writeTask(String key, byte[] content, int type){
        Socket socket = null;
        try {
            socket = getSocket(key);
            if(socket == null){
                consoleLog.error("socket is null");
                return;
            }
            OutputStream out = socket.getOutputStream();
            out.write(protocol.encodeFrame(content, type));
        } catch (IOException io){
            clear(key, socket);
            consoleLog.warn("key:{} is disconnect", key);
        } catch (Throwable e){
            clear(key, socket);
            consoleLog.error("an unknown error:{}", e.getMessage());
        }
    }

    private Socket getSocket(String key) {
        if(!keyMap.containsKey(key)){
            consoleLog.error("key:{} is not exist", key);
            return null;
        }
        SocketInfo socketInfo = (SocketInfo) keyMap.get(key);
        if(socketInfo == null){
            consoleLog.error("socketInfo is null");
            return null;
        }
        return socketInfo.getSocket();
    }

    private void clear(String key, Socket socket){
        keyMap.remove(key);
        if(socket != null){
            try {
                socket.close();
            } catch (IOException e) {
                consoleLog.error("socket close failed, e:{}", e.getMessage());
            }
        }
    }

    protected void recvFromCustomQueue(Receiver receiver, Integer pollTime){
        while (!stopReceive){
            try {
                for (Map.Entry<String, Object> entry : keyMap.entrySet()){
                    SocketInfo socketInfo = (SocketInfo) entry.getValue();
                    if(socketInfo.getNormFrameQueue().isEmpty()){
                        continue;
                    }
                    receiver.recv(socketInfo.getNormFrameQueue().removeFirst(), entry.getKey());
                    if(isReceiveHeartBeat && !socketInfo.getHeartBeatFrameQueue().isEmpty()){
                        receiver.recv(socketInfo.getHeartBeatFrameQueue().removeFirst(), entry.getKey());
                    }
                }
                waitForAccept();
                if(pollTime <= 0){
                    continue;
                }
                Thread.sleep(pollTime);
            } catch (Throwable e){
                consoleLog.error("an unknown error:{}", e.getMessage());
            }
        }
    }
}
