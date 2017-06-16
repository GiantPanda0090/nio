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
package se.kth.id1212.nio.textprotocolchat.server.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayDeque;
import java.util.Queue;
import se.kth.id1212.nio.textprotocolchat.common.MessageException;
import se.kth.id1212.nio.textprotocolchat.server.controller.Controller;

/**
 * Receives chat messages and broadcasts them to all chat clients. All communication to/from any
 * chat node pass this server.
 */
public class ChatServer {
    private static final int LINGER_TIME = 5000;
    private static final int TIMEOUT_HALF_HOUR = 1800000;
    private final Controller contr = new Controller();
    private int portNo = 8080;
    private Selector selector;
    private ServerSocketChannel listeningSocketChannel;
    private volatile boolean timeToBroadcast = false;
    private final Queue<String> messagesToSend = new ArrayDeque<>();

    /**
     * Sends the specified message to all connected clients
     *
     * @param msg The message to broadcast.
     */
    void broadcast(String msg) {
        contr.appendEntry(msg);
        timeToBroadcast = true;
        synchronized (messagesToSend) {
            messagesToSend.add(msg);
        }
        selector.wakeup();
    }

    private void serve() {
        try {
            initSelector();
            initListeningSocketChannel();
            while (true) {
                if (timeToBroadcast) {
                    writeOperationForAllKeys();
                    appendMsgToAllClientQueues();
                    timeToBroadcast = false;
                }
                selector.select();
                for (SelectionKey key : selector.selectedKeys()) {
                    selector.selectedKeys().remove(key);
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isAcceptable()) {
                        startHandler(key);
                    } else if (key.isReadable()) {
                        recvFromClient(key);
                    } else if (key.isWritable()) {
                        sendToClient(key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Server failure.");
            e.printStackTrace();
        }
    }

    private void startHandler(SelectionKey key) throws IOException {
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverSocketChannel.accept();
        clientChannel.configureBlocking(false);
        ClientHandler handler = new ClientHandler(this, clientChannel);
        clientChannel.register(selector, SelectionKey.OP_WRITE, new Client(handler, contr.
                                                                           getConversation()));
        clientChannel.setOption(StandardSocketOptions.SO_LINGER, LINGER_TIME); //Close will probably block on some JVMs.
        // clientChannel.socket().setSoTimeout(TIMEOUT_HALF_HOUR); Timeout is not supported on 
        // socket channels. Could be implemented using a separate timer that is checked whenever the
        // select() method in the main loop returns.
    }

    private void recvFromClient(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();
        try {
            client.handler.recvMsg();
        } catch (IOException clientHasClosedConnection) {
            removeClient(key);
        }
    }

    private void sendToClient(SelectionKey key) throws IOException {
        Client client = (Client) key.attachment();
        try {
            client.sendAll();
            key.interestOps(SelectionKey.OP_READ);
        } catch (MessageException couldNotSendAllMsgs) {
        } catch (IOException clientHasClosedConnection) {
            removeClient(key);
        }
    }

    private void removeClient(SelectionKey clientKey) throws IOException {
        Client client = (Client) clientKey.attachment();
        client.handler.disconnectClient();
        clientKey.cancel();
    }

    private void parseArguments(String[] arguments) {
        if (arguments.length > 0) {
            try {
                portNo = Integer.parseInt(arguments[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number, using default.");
            }
        }
    }

    private void initSelector() throws IOException {
        selector = Selector.open();
    }

    private void initListeningSocketChannel() throws IOException {
        listeningSocketChannel = ServerSocketChannel.open();
        listeningSocketChannel.configureBlocking(false);
        listeningSocketChannel.bind(new InetSocketAddress(portNo));
        listeningSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    private void writeOperationForAllKeys() {
        for (SelectionKey key : selector.keys()) {
            if (key.channel() instanceof SocketChannel) {
                key.interestOps(SelectionKey.OP_WRITE);
            }
        }
    }

    private void appendMsgToAllClientQueues() {
        synchronized (messagesToSend) {
            String msgToSend;
            while ((msgToSend = messagesToSend.poll()) != null) {
                for (SelectionKey key : selector.keys()) {
                    Client client = (Client) key.attachment();
                    if (client == null) {
                        continue;
                    }
                    synchronized (client.messagesToSend) {
                        client.queueMsgToSend(msgToSend);
                    }
                }
            }
        }
    }

    private static class Client {
        private final ClientHandler handler;
        private final Queue<String> messagesToSend = new ArrayDeque<>();

        private Client(ClientHandler handler, String[] conversation) {
            this.handler = handler;
            for (String entry : conversation) {
                messagesToSend.add(entry);
            }
        }

        private void queueMsgToSend(String msg) {
            synchronized (messagesToSend) {
                messagesToSend.add(msg);
            }
        }

        private void sendAll() throws IOException {
            String msg = null;
            synchronized (messagesToSend) {
                while ((msg = messagesToSend.peek()) != null) {
                    handler.sendMsg(msg);
                    messagesToSend.remove();
                }
            }
        }
    }

    /**
     * @param args Takes one command line argument, the number of the port on which the server will
     *             listen, the default is <code>8080</code>.
     */
    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.parseArguments(args);
        server.serve();
    }
}
