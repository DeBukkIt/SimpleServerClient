package com.blogspot.debukkitsblog.net;

import java.util.concurrent.ThreadFactory;

/**
 * https://github.com/Stupremee
 *
 * @author Stu
 * @since 20.03.2019
 */
public class ServerThreadFactory implements ThreadFactory {

    private static final class Lazy {
        private static ServerThreadFactory INSTANCE = new ServerThreadFactory();
    }

    private int threadCount = 0;

    private ServerThreadFactory() {
    }

    @Override
    public Thread newThread(Runnable r) {
        return new Thread(r, "ServerThread-" + threadCount++);
    }

    public static ThreadFactory getDefault() {
        return Lazy.INSTANCE;
    }
}
