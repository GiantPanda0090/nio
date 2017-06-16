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
package se.kth.id1212.nio.objprotocolchat.server.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ForkJoinPool;
import se.kth.id1212.nio.objprotocolchat.common.MessageException;
import se.kth.id1212.nio.objprotocolchat.common.MsgType;
import se.kth.id1212.nio.objprotocolchat.common.Message;

/**
 * Handles all communication with one particular chat client.
 */
class ClientHandler implements Runnable {
    private static final String JOIN_MESSAGE = " joined conversation.";
    private static final String LEAVE_MESSAGE = " left conversation.";
    private static final String USERNAME_DELIMETER = ": ";
    private static final int MAX_MSG_LENGTH = 8192;

    private final ChatServer server;
    private final SocketChannel clientChannel;
    private String username = "anonymous";
    private final ByteBuffer msgFromClient = ByteBuffer.allocate(MAX_MSG_LENGTH);

    /**
     * Creates a new instance, which will handle communication with one specific client connected to
     * the specified channel.
     *
     * @param clientChannel The socket to which this handler's client is connected.
     */
    ClientHandler(ChatServer server, SocketChannel clientChannel) {
        this.server = server;
        this.clientChannel = clientChannel;
    }

    /**
     * The run loop handling all communication with the connected client.
     */
    @Override
    public void run() {
        try {
            Message msg;
            try (ObjectInputStream fromBuffer = new ObjectInputStream(new ByteArrayInputStream(
                    msgFromClient.array()))) {
                msg = (Message) fromBuffer.readObject();
            }
            switch (msg.getType()) {
                case USER:
                    username = msg.getBody();
                    server.broadcast(username + JOIN_MESSAGE);
                    break;
                case ENTRY:
                    server.broadcast(username + USERNAME_DELIMETER + msg.getBody());
                    break;
                case DISCONNECT:
                    disconnectClient();
                    server.broadcast(username + LEAVE_MESSAGE);
                    break;
                default:
                    throw new MessageException("Received corrupt message: " + msg);
            }
        } catch (IOException | ClassNotFoundException e) {
            disconnectClient();
            throw new MessageException(e);
        }
    }

    /**
     * Sends the specified message to the connected client.
     *
     * @param msgBody The message to send.
     * @throws MessageException if unable to send message.
     */
    void sendMsg(String msgBody) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream(MAX_MSG_LENGTH);
            try (ObjectOutputStream toClient = new ObjectOutputStream(byteStream)) {
                Message msg = new Message(MsgType.BROADCAST, msgBody);
                toClient.writeObject(msg);
            }
            ByteBuffer msgToClient = ByteBuffer.wrap(byteStream.toByteArray());
            clientChannel.write(msgToClient);
            if (msgToClient.hasRemaining()) {
                throw new MessageException("Could not send message");
            }
        } catch (IOException ioe) {
            disconnectClient();
        }
    }

    private void disconnectClient() {
        try {
            clientChannel.close();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void readMsg() {
        msgFromClient.clear();
        int numOfReadBytes = 0;
        try {
            numOfReadBytes = clientChannel.read(msgFromClient);
        } catch (IOException ex) {
            disconnectClient();
        }
        if (numOfReadBytes == -1) {
            disconnectClient();
        }
        ForkJoinPool.commonPool().execute(this);
    }
}
