package com.acme.client;

import com.acme.Log.ConsoleLog;
import com.acme.common.Task;
import com.acme.protocol.FixedLengthProtocol;
import com.acme.protocol.FrameInfo;
import com.acme.common.Receiver;
import com.acme.protocol.Protocol;
import lombok.Getter;
import lombok.Setter;

import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author acme
 * @date 2019/8/26 5:26 PM
 */
@Getter
@Setter
public abstract class AbstractClient{
    protected static final int DEFAULT_PORT = 9999;

    protected static final String DEFAULT_HOST = "127.0.0.1";

    protected int port = DEFAULT_PORT;

    protected String host = DEFAULT_HOST;

    protected Protocol protocol;

    /*
    * 可以中断接收队列中的数据，不影响实际接收数据
    * 使用volatile修饰，其他线程修改的时候，保证可见性
    * */
    protected volatile boolean stopReceive;

    protected volatile boolean isConnected;

    /*
     * 标识client的key
     * */
    protected String key;

    //正常数据接收帧队列
    protected LinkedList<FrameInfo> normFrameQueue;

    //主要用于心跳定时发送
    protected ScheduledExecutorService scheduledExecutorService;

    protected ConsoleLog consoleLog;

    protected AbstractClient() {
        this.isConnected = false;
        this.stopReceive = false;
        this.normFrameQueue = new LinkedList<>();
        this.scheduledExecutorService = Executors.newScheduledThreadPool(1);
        this.consoleLog = new ConsoleLog();
    }

    /**
     * 向server注册client key信息
     */
    abstract protected void register();

    /**
     * 和server断开时，自动重连，超过1小时，认为连接失败，不再尝试
     */
    protected synchronized void reConnect() {
    }

    /**
     * 从队列中接收数据
     * 当接收到server发送的数据，会先放入队列，保证 接收>=消费 的速度
     * @param receiver 接收器，可以自定义接收器去处理数据
     * @param pollTime 处理一条消息间隔时间，若为<=0，表示不停顿处理
     */
    protected void recv(Receiver receiver, Integer pollTime) {
        while (!stopReceive) {
            try {
                while (!normFrameQueue.isEmpty()) {
                    receiver.recv(normFrameQueue.removeFirst(), "");
                }
                if(pollTime <= 0){
                    continue;
                }
                Thread.sleep(pollTime);
            } catch (InterruptedException e) {
                //nothing to do
            }
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
}
