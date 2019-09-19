package com.acme.server;

import com.acme.Log.ConsoleLog;
import com.acme.common.Task;
import com.acme.common.AbstractSocketChannelHandle;
import com.acme.common.Receiver;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;
import com.acme.protocol.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author acme
 * @date 2019/8/26 5:02 PM
 */
@Getter
@Setter
public abstract class AbstractServer {
    //默认端口
    protected static final int DEFAULT_PORT = 9999;

    //协议编码
    protected Protocol protocol;

    protected ThreadPoolExecutor threadPool;

    //全局队列，用来放置来不及消费的FrameInfo
    protected volatile LinkedList<FrameInfo> frameQueue;

    protected int port;

    //是否停止接收数据,true表示不再去read socket
    protected volatile boolean stopReceive;

    //是否停止接收心跳,true表示不再将心跳数据放到队列
    protected volatile boolean isReceiveHeartBeat;

    //是否使用自动生成的key,false表示使用客户端传来的key
    protected volatile boolean isUseDefaultKey;

    //是否使用全局队列
    protected volatile boolean isUseGlobalQueue;

    //当前是否有client连接，主要目的是防止空轮询
    protected volatile boolean hasClient;

    protected volatile Map<String, Object> keyMap;

    protected ConsoleLog consoleLog;

    protected AbstractServer(){
        this.port = DEFAULT_PORT;
        this.isReceiveHeartBeat = false;
        this.isUseDefaultKey = false;
        this.stopReceive = false;
        this.isUseGlobalQueue = false;
        this.hasClient = false;
        this.frameQueue = new LinkedList<>();
        this.keyMap = new ConcurrentHashMap<>();
        this.consoleLog = new ConsoleLog();
    }

    protected AbstractServer(Protocol protocol){
        this();
        this.protocol = protocol;
    }

    /**
     * 阻塞，一直到有连接到来，不适用轮询的方式，容易造成CPU空转
     * 适用wait/notify的方式唤醒
     */
    protected void waitForAccept(){
        synchronized (keyMap){
            while(keyMap.isEmpty()){
                try {
                    hasClient = false;
                    keyMap.wait();
                } catch (InterruptedException e) {
                    //nothing to do
                }
            }
        }
    }

    protected void checkClientAndNotify(){
        if(!hasClient){
            synchronized (keyMap){
                keyMap.notify();
                hasClient = true;
            }
        }
    }

    protected void recvFromGlobalQueue(Receiver receiver, Integer pollTime){
        while (!stopReceive){
            try {
                if(frameQueue.isEmpty()){
                    continue;
                }
                FrameInfo frameInfo = frameQueue.removeFirst();
                if(frameInfo.getFrameHead().getType() == Protocol.CONTENT_TYPE){
                    receiver.recv(frameInfo, frameInfo.getKey());
                } else if(isReceiveHeartBeat && frameInfo.getFrameHead().getType() == Protocol.HEART_BEAT_TYPE){
                    receiver.recv(frameInfo, frameInfo.getKey());
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

    protected void recvFromCustomQueue(Receiver receiver, Integer pollTime){
        while (!stopReceive){
            try {
                for (Map.Entry<String, Object> entry : keyMap.entrySet()){
                    AbstractSocketChannelHandle channelHandle = (AbstractSocketChannelHandle) entry.getValue();
                    if(!channelHandle.getNormFrameQueue().isEmpty()){
                        receiver.recv(channelHandle.getNormFrameQueue().removeFirst(), entry.getKey());
                    }
                    if(isReceiveHeartBeat && !channelHandle.getHeartBeatFrameQueue().isEmpty()){
                        receiver.recv(channelHandle.getHeartBeatFrameQueue().removeFirst(), entry.getKey());
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

    protected void recv(Receiver receiver, Integer pollTime){
        if(isUseGlobalQueue){
            recvFromGlobalQueue(receiver, pollTime);
        }else {
            recvFromCustomQueue(receiver, pollTime);
        }
    }

    protected void checkProtocol(){
        if(protocol == null){
            throw new RuntimeException("protocol is not allowed to be null");
        }
    }

    /**
     * 处理线程任务的抽象，只需要传入自定义的task即可
     */
    protected class HandleTask implements Runnable {
        private Task task;

        public HandleTask(Task task) {
            this.task = task;
        }

        @Override
        public void run() {
            task.run();
        }
    }

    protected class CustomReceiver<T> implements Receiver {
        private T channelHandle;

        public CustomReceiver(T channelHandle){
            this.channelHandle = channelHandle;
        }

        @Override
        public void recv(FrameInfo frameInfo, String key) {
            if(frameInfo.getFrameHead().getType() == Protocol.HEART_BEAT_TYPE) {
                readHeartbeat(frameInfo);
            } else if(frameInfo.getFrameHead().getType() == Protocol.CONTENT_TYPE) {
                readContent(frameInfo);
            } else if(frameInfo.getFrameHead().getType() == Protocol.KEY_TYPE) {
                readKey(frameInfo);
            }
        }

        private void readHeartbeat(FrameInfo frameInfo){
            if(isReceiveHeartBeat){
                addFrameToQueue(frameInfo, channelHandle);
            }
        }

        private void readContent(FrameInfo frameInfo){
            addFrameToQueue(frameInfo, channelHandle);
        }

        private void readKey(FrameInfo frameInfo){
            if(keyMap.containsKey(frameInfo.getMsg())){
                consoleLog.error("key:{} already exist", frameInfo.getMsg());
                ((AbstractSocketChannelHandle)channelHandle).close();
            } else {
                ((AbstractSocketChannelHandle)channelHandle).setKey(frameInfo.getMsg());
                keyMap.put(frameInfo.getMsg(), channelHandle);
                checkClientAndNotify();
                consoleLog.info("key:{} connected", frameInfo.getMsg());
            }
        }

        private void addFrameToQueue(FrameInfo frameInfo, T channelHandle){
            if(isUseGlobalQueue){
                frameQueue.add(frameInfo);
            } else {
                ((AbstractSocketChannelHandle)channelHandle).getHeartBeatFrameQueue().add(frameInfo);
            }
        }
    }
}
