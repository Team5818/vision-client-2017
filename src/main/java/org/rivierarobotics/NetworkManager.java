/*
 * This file is part of vision-client-2017, licensed under the MIT License (MIT).
 *
 * Copyright (c) Team5818 <https://github.com/Team5818>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.rivierarobotics;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.rivierarobotics.packet.Packets;

import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

public class NetworkManager {

    private static final int RECONNECT_TIMEOUT = 500;
    private static final int IDLE_TIMEOUT = 500;

    private final Deque<Any> incomingPackets = new ConcurrentLinkedDeque<>();
    private final Lock inPacketLock = new ReentrantLock();
    private final Deque<Message> outgoingPackets =
            new ConcurrentLinkedDeque<>();
    private final Lock outPacketLock = new ReentrantLock();
    private volatile String addr;
    private volatile int port;
    private volatile Socket connection;
    private final Lock connLock = new ReentrantLock();
    private boolean idleSet;
    private long idleNanos;
    private long reconnectNanos;

    {
        Thread thread = new Thread(() -> {
            while (!Thread.interrupted()) {
                try {
                    networkLoop();
                    Thread.sleep(10);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        thread.setName("NetworkManager Processing");
        thread.setDaemon(true);
        thread.start();
    }

    public void setAddr(String addr) {
        this.addr = addr;

        // invalidate socket
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setPort(int port) {
        this.port = port;

        // invalidate socket
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public <M extends Message> Optional<M> nextMessageOfType(Class<M> type) {
        // lock over list access to sync
        inPacketLock.lock();
        try {
            for (Iterator<Any> iterator = incomingPackets.iterator(); iterator
                    .hasNext();) {
                Any a = iterator.next();
                if (a.is(type)) {
                    iterator.remove();
                    try {
                        return Optional.of(a.unpack(type));
                    } catch (InvalidProtocolBufferException e) {
                        throw new RuntimeException(e);
                    }
                }

            }
        } finally {
            inPacketLock.unlock();
        }
        return Optional.empty();
    }

    public void sendMessage(Message message) {
        outPacketLock.lock();
        try {
            outgoingPackets.addLast(message);
        } finally {
            outPacketLock.unlock();
        }
    }

    private void networkLoop() throws Exception {
        connLock.lock();
        try {
            if (connection != null && connection.isClosed()) {
                connection = null;
            }

            if (connection != null) {
                readPackets();
                writePackets();
            } else {
                establishConnection();
            }
        } catch (Exception e) {
            // close out conn on errors
            if (connection != null) {
                try {
                    connection.close();
                } catch (Throwable t) {
                    e.addSuppressed(t);
                } finally {
                    connection = null;
                }
                disconnect();
            }
            throw e;
        } finally {
            connLock.unlock();
        }
    }

    private void establishConnection()
            throws UnknownHostException, IOException {
        if (addr == null || addr.isEmpty() || port == 0) {
            return;
        }
        while (System.nanoTime() < reconnectNanos) {
            // stall thread until ready, to prevent grinding CPU
            try {
                long totalNanos = reconnectNanos - System.nanoTime();
                long millis = TimeUnit.NANOSECONDS
                        .toMillis(totalNanos);
                int extraNanos = (int) (totalNanos
                        - TimeUnit.MILLISECONDS.toNanos(millis));
                Thread.sleep(millis, extraNanos);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        connLock.lock();
        try {
            connection = new Socket(addr, port);
            connection.setSoTimeout(500);
        } finally {
            connLock.unlock();
        }
    }

    private void readPackets() throws IOException {
        InputStream in = connection.getInputStream();
        boolean anyRead = false;
        while (in.available() > 0) {
            anyRead = true;
            inPacketLock.lock();
            try {
                incomingPackets.addLast(Packets.readPacket(in));
            } finally {
                inPacketLock.unlock();
            }
        }
        if (!anyRead) {
            if (idleSet && idleNanos < System.nanoTime()) {
                // expire socket
                System.err.println("Disconnecting due to idle timeout");
                disconnect();
            }
        } else {
            idleNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(IDLE_TIMEOUT);
            idleSet = true;
        }
    }

    private void writePackets() throws IOException {
        if (connection == null) {
            return;
        }
        OutputStream out = connection.getOutputStream();
        outPacketLock.lock();
        try {
            while (!outgoingPackets.isEmpty()) {
                Message p = outgoingPackets.removeFirst();
                Packets.writePacket(out, p);
            }
        } finally {
            outPacketLock.unlock();
        }
    }

    private void disconnect() {
        connLock.lock();
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            connection = null;
            reconnectNanos = System.nanoTime()
                    + TimeUnit.MILLISECONDS.toNanos(RECONNECT_TIMEOUT);
            connLock.unlock();
        }
    }

}
