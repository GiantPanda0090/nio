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
package se.kth.id1212.nio.textprotocolchat.common;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.StringJoiner;

/**
 * Handles receiving partial or multiple messages. Received data are sent to this class, and
 * individual messages are extracted. All methods are thread safe.
 */
public class MessageSplitter {
    private StringBuilder recvdChars = new StringBuilder();
    private final Queue<String> messages = new ArrayDeque<>();

    /**
     * Appends a newly received string to previously received strings.
     *
     * @param recvdString The received string.
     */
    public synchronized void appendRecvdString(String recvdString) {
        recvdChars.append(recvdString);
        while(extractMsg());
    }

    /**
     * @return The first received message that has not previously been returned, or
     *         <code>null</code> if there is no complete message available.
     */
    public synchronized String nextMsg() {
        return messages.poll();
    }

    /**
     * @return <code>true</code> if there is at least one unread complete message,
     *         <code>false</code> if there is not.
     */
    public synchronized boolean hasNext() {
        return !messages.isEmpty();
    }

    /**
     * Prepends a length header to the specified message. This method should be used by senders. The
     * returned message can be handled by instances of this class when the message is received.
     *
     * @param msgWithoutHeader A message with no length header
     * @return The specified message, with the appropriate length header prepended.
     */
    public static String prependLengthHeader(String msgWithoutHeader) {
        StringJoiner joiner = new StringJoiner(Constants.MSG_LEN_DELIMETER);
        joiner.add(Integer.toString(msgWithoutHeader.length()));
        joiner.add(msgWithoutHeader);
        return joiner.toString();
    }

    /**
     * Returns the type of the specified message.
     */
    public static MsgType typeOf(String msg) {
        String[] msgParts = msg.split(Constants.MSG_TYPE_DELIMETER);
        return MsgType.valueOf(msgParts[Constants.MSG_TYPE_INDEX].toUpperCase());
    }

    /**
     * Returns the body of the specified message.
     */
    public static String bodyOf(String msg) {
        String[] msgParts = msg.split(Constants.MSG_TYPE_DELIMETER);
        return msgParts[Constants.MSG_BODY_INDEX];
    }

    private boolean extractMsg() {
        String allRecvdChars = recvdChars.toString();
        String[] splitAtHeader = allRecvdChars.split(Constants.MSG_LEN_DELIMETER);
        if (splitAtHeader.length < 2) {
            return false;
        }
        String lengthHeader = splitAtHeader[0];
        int lengthOfFirstMsg = Integer.parseInt(lengthHeader);
        if (hasCompleteMsg(lengthOfFirstMsg, splitAtHeader[1])) {
            String completeMsg = splitAtHeader[1].substring(0, lengthOfFirstMsg);
            messages.add(completeMsg);
            recvdChars.delete(0, lengthHeader.length()
                                 + Constants.MSG_LEN_DELIMETER.length() + lengthOfFirstMsg);
            return true;
        }
        return false;
    }

    private boolean hasCompleteMsg(int msgLen, String recvd) {
        return recvd.length() >= recvd.length();
    }

}
