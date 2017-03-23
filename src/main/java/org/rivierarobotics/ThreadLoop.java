package org.rivierarobotics;

public class ThreadLoop {

    @FunctionalInterface
    public interface RunnableEx {

        void run() throws Exception;

    }

    private final String name;
    private final RunnableEx loop;
    private final long millisPeriod;

    public ThreadLoop(String name, RunnableEx loop, long millisPeriod) {
        this.name = name;
        this.loop = loop;
        this.millisPeriod = millisPeriod;
    }

    public Thread start() {
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    loop.run();
                    Thread.sleep(millisPeriod);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setName(name + " Loop");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

}
