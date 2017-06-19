/*
 * The MIT License
 *
 * Copyright 2017 Leif Lindbäck <leifl@kth.se>.
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
package se.kth.id1212.nio.textprotocolchat.client.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.StringJoiner;
import java.util.concurrent.ForkJoinPool;
import se.kth.id1212.nio.textprotocolchat.common.Constants;
import se.kth.id1212.nio.textprotocolchat.common.MessageException;
import se.kth.id1212.nio.textprotocolchat.common.MessageSplitter;
import se.kth.id1212.nio.textprotocolchat.common.MsgType;

/**
 * Manages all communication with the server, all operations are non-blocking.
 */
public class ServerConnection implements Runnable {
    private static final String FATAL_COMMUNICATION_MSG = "Lost connection.";
    private static final String FATAL_DISCONNECT_MSG = "Could not disconnect, will leave ungracefully.";

    private final ByteBuffer msgFromServer = ByteBuffer.allocateDirect(Constants.MAX_MSG_LENGTH);
    private final Queue<String> messagesToSend = new ArrayDeque<>();
    private final MessageSplitter msgSplitter = new MessageSplitter();
    private final List<CommunicationListener> listeners = new ArrayList<>();
    private InetSocketAddress serverAddress;
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean connected;
    private volatile boolean timeToSend = false;

    /**
     * The communicating thread, all communication is non-blocking. First, server connection is
     * established. Then the thread sends messages submitted via one of the <code>send</code>
     * methods in this class and receives messages from the server.
     */
    @Override
    public void run() {
        try {
            initConnection();
            initSelector();

            while (connected || !messagesToSend.isEmpty()) {
                if (timeToSend) {
                    socketChannel.keyFor(selector).interestOps(SelectionKey.OP_WRITE);
                    timeToSend = false;
                }

                selector.select();
                for (SelectionKey key : selector.selectedKeys()) {
                    selector.selectedKeys().remove(key);
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isConnectable()) {
                        completeConnection(key);
                    } else if (key.isReadable()) {
                        recvFromServer(key);
                    } else if (key.isWritable()) {
                        sendToServer(key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(FATAL_COMMUNICATION_MSG);
        }
        try {
            doDisconnect();
        } catch (IOException ex) {
            System.err.println(FATAL_DISCONNECT_MSG);
        }
    }

    /**
     * The specified listener will be notified when connecting, disconnecting and receiving a
     * message.
     *
     * @param listener The listener to notify.
     */
    public void addCommunicationListener(CommunicationListener listener) {
        listeners.add(listener);
    }

    /**
     * Starts the communicating thread and connects to the server.
     *
     * @param host Host name or IP address of server.
     * @param port Server's port number.
     * @throws IOException If failed to connect.
     */
    public void connect(String host, int port) {
        serverAddress = new InetSocketAddress(host, port);
        new Thread(this).start();
    }

    private void initSelector() throws IOException {
        selector = Selector.open();
        socketChannel.register(selector, SelectionKey.OP_CONNECT);
    }

    private void initConnection() throws IOException {
        socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.connect(serverAddress);
        connected = true;
    }

    private void completeConnection(SelectionKey key) throws IOException {
        socketChannel.finishConnect();
        key.interestOps(SelectionKey.OP_READ);
        try {
            InetSocketAddress remoteAddress = (InetSocketAddress) socketChannel.getRemoteAddress();
            listeners.forEach((listener)
                    -> ForkJoinPool.commonPool().execute(() -> listener.connected(remoteAddress)));
        } catch (IOException couldNotGetRemAddrUsingDefaultInstead) {
            listeners.forEach((listener)
                    -> ForkJoinPool.commonPool().execute(() -> listener.connected(serverAddress)));
        }
    }

    /**
     * Stops the communicating thread and closes the connection with the server.
     *
     * @throws IOException If failed to close connection.
     */
    public void disconnect() throws IOException {
        connected = false;
        sendMsg(MsgType.DISCONNECT.toString(), null);
    }

    private void doDisconnect() throws IOException {
        socketChannel.close();
        socketChannel.keyFor(selector).cancel();
        listeners.forEach((listener)
                -> ForkJoinPool.commonPool().execute(() -> listener.disconnected()));
    }

    /**
     * Sends the user's username to the server. That username will be prepended to all messages
     * originating from this client, until a new username is specified.
     *
     * @param username The current user's username.
     */
    public void sendUsername(String username) {
        sendMsg(MsgType.USER.toString(), username);
    }

    /**
     * Sends a chat entry to the server, which will broadcast it to all clients including this,
     * sending, client.
     *
     * @param msg The message to broadcast.
     */
    public void sendChatEntry(String msg) {
        sendMsg(MsgType.ENTRY.toString(), msg);
    }

    private void sendMsg(String... parts) {
        StringJoiner joiner = new StringJoiner(Constants.MSG_TYPE_DELIMETER);
        for (String part : parts) {
            joiner.add(part);
        }
        String messageWithLengthHeader = MessageSplitter.prependLengthHeader(joiner.toString());
        synchronized (messagesToSend) {
            messagesToSend.add(messageWithLengthHeader);
        }
        timeToSend = true;
        selector.wakeup();
    }

    private void sendToServer(SelectionKey key) throws IOException {
        String msg;
        synchronized (messagesToSend) {
            while ((msg = messagesToSend.peek()) != null) {
                ByteBuffer msgToServer = ByteBuffer.wrap(msg.getBytes());
                socketChannel.write(msgToServer);
                if (msgToServer.hasRemaining()) {
                    return;
                }
                messagesToSend.remove();
            }
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void recvFromServer(SelectionKey key) throws IOException {
        msgFromServer.clear();
        int numOfReadBytes = socketChannel.read(msgFromServer);
        if (numOfReadBytes == -1) {
            throw new IOException(FATAL_COMMUNICATION_MSG);
        }
        String recvdString = extractMessageFromBuffer();
        msgSplitter.appendRecvdString(recvdString);
        while (msgSplitter.hasNext()) {
            String msg = msgSplitter.nextMsg();
            if (MessageSplitter.typeOf(msg) != MsgType.BROADCAST) {
                throw new MessageException("Received corrupt message: " + msg);
            }
            listeners.forEach((listener)
                    -> ForkJoinPool.commonPool().execute(()
                            -> listener.recvdMsg(MessageSplitter.bodyOf(msg))));
        }
    }

    private String extractMessageFromBuffer() {
        msgFromServer.flip();
        byte[] bytes = new byte[msgFromServer.remaining()];
        msgFromServer.get(bytes);
        return new String(bytes);
    }
}
