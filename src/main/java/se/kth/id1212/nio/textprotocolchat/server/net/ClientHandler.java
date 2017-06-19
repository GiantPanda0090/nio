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
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.StringJoiner;
import java.util.concurrent.ForkJoinPool;
import se.kth.id1212.nio.textprotocolchat.common.Constants;
import se.kth.id1212.nio.textprotocolchat.common.MessageException;
import se.kth.id1212.nio.textprotocolchat.common.MessageSplitter;
import se.kth.id1212.nio.textprotocolchat.common.MsgType;

/**
 * Handles all communication with one particular chat client.
 */
class ClientHandler implements Runnable {
    private static final String JOIN_MESSAGE = " joined conversation.";
    private static final String LEAVE_MESSAGE = " left conversation.";
    private static final String USERNAME_DELIMETER = ": ";

    private final ChatServer server;
    private final SocketChannel clientChannel;
    private final ByteBuffer msgFromClient = ByteBuffer.allocateDirect(Constants.MAX_MSG_LENGTH);
    private final MessageSplitter msgSplitter = new MessageSplitter();
    private String username = "anonymous";

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
     * Receives and handles one message from the connected client.
     */
    @Override
    public void run() {
        while (msgSplitter.hasNext()) {
            Message msg = new Message(msgSplitter.nextMsg());
            switch (msg.msgType) {
                case USER:
                    username = msg.msgBody;
                    server.broadcast(username + JOIN_MESSAGE);
                    break;
                case ENTRY:
                    server.broadcast(username + USERNAME_DELIMETER + msg.msgBody);
                    break;
                case DISCONNECT:
                    server.broadcast(username + LEAVE_MESSAGE);
                    break;
                default:
                    throw new MessageException("Received corrupt message: " + msg.receivedString);
            }
        }
    }

    /**
     * Sends the specified message to the connected client.
     *
     * @param msg The message to send.
     * @throws IOException If failed to send message.
     */
    void sendMsg(String msg) throws IOException {
        StringJoiner joiner = new StringJoiner(Constants.MSG_TYPE_DELIMETER);
        joiner.add(MsgType.BROADCAST.toString());
        joiner.add(msg);
        String messageWithLengthHeader = MessageSplitter.prependLengthHeader(joiner.toString());
        ByteBuffer msgToClient = ByteBuffer.wrap(messageWithLengthHeader.getBytes());
        clientChannel.write(msgToClient);
        if (msgToClient.hasRemaining()) {
            throw new MessageException("Could not send message");
        }
    }

    /**
     * Reads a message from the connected client, then submits a task to the default
     * <code>ForkJoinPool</code>. That task which will handle the received message.
     *
     * @throws IOException If failed to read message
     */
    void recvMsg() throws IOException {
        msgFromClient.clear();
        int numOfReadBytes;
        numOfReadBytes = clientChannel.read(msgFromClient);
        if (numOfReadBytes == -1) {
            throw new IOException("Client has closed connection.");
        }
        String recvdString = extractMessageFromBuffer();
        msgSplitter.appendRecvdString(recvdString);
        ForkJoinPool.commonPool().execute(this);
    }

    private String extractMessageFromBuffer() {
        msgFromClient.flip();
        byte[] bytes = new byte[msgFromClient.remaining()];
        msgFromClient.get(bytes);
        return new String(bytes);
    }

    /**
     * Closes this instance's client connection.
     *
     * @throws IOException If failed to close connection.
     */
    void disconnectClient() throws IOException {
        clientChannel.close();
    }

    private static class Message {
        private MsgType msgType;
        private String msgBody;
        private String receivedString;

        private Message(String receivedString) {
            parse(receivedString);
            this.receivedString = receivedString;
        }

        private void parse(String strToParse) {
            try {
                String[] msgTokens = strToParse.split(Constants.MSG_TYPE_DELIMETER);
                msgType = MsgType.valueOf(msgTokens[Constants.MSG_TYPE_INDEX].toUpperCase());
                if (hasBody(msgTokens)) {
                    msgBody = msgTokens[Constants.MSG_BODY_INDEX].trim();
                }
            } catch (Throwable throwable) {
                throw new MessageException(throwable);
            }
        }

        private boolean hasBody(String[] msgTokens) {
            return msgTokens.length > 1;
        }
    }
}
