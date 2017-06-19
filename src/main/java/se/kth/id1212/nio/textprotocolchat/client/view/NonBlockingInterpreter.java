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
package se.kth.id1212.nio.textprotocolchat.client.view;

import java.net.InetSocketAddress;
import java.util.Scanner;
import se.kth.id1212.nio.textprotocolchat.client.net.CommunicationListener;
import se.kth.id1212.nio.textprotocolchat.client.net.ServerConnection;

/**
 * Reads and interprets user commands. The command interpreter will run in a separate thread, which
 * is started by calling the <code>start</code> method. A command is executed in a separate thread,
 * a new prompt will be displayed as soon as a command is submitted, without waiting for command
 * execution to complete.
 */
public class NonBlockingInterpreter implements Runnable {
    private static final String PROMPT = "> ";
    private final Scanner console = new Scanner(System.in);
    private final ThreadSafeStdOut outMgr = new ThreadSafeStdOut();
    private boolean receivingCmds = false;
    private ServerConnection server;

    /**
     * Starts the interpreter. The interpreter will be waiting for user input when this method
     * returns. Calling <code>start</code> on an interpreter that is already started has no effect.
     */
    public void start() {
        if (receivingCmds) {
            return;
        }
        receivingCmds = true;
        server = new ServerConnection();
        new Thread(this).start();
    }

    /**
     * Interprets and performs user commands.
     */
    @Override
    public void run() {
        while (receivingCmds) {
            try {
                CmdLine cmdLine = new CmdLine(readNextLine());
                switch (cmdLine.getCmd()) {
                    case QUIT:
                        receivingCmds = false;
                        server.disconnect();
                        break;
                    case CONNECT:
                        server.addCommunicationListener(new ConsoleOutput());
                        server.connect(cmdLine.getParameter(0),
                                       Integer.parseInt(cmdLine.getParameter(1)));
                        break;
                    case USER:
                        server.sendUsername(cmdLine.getParameter(0));
                        break;
                    default:
                        server.sendChatEntry(cmdLine.getUserInput());
                }
            } catch (Exception e) {
                outMgr.println("Operation failed");
            }
        }
    }

    private String readNextLine() {
        outMgr.print(PROMPT);
        return console.nextLine();
    }

    private class ConsoleOutput implements CommunicationListener {
        @Override
        public void recvdMsg(String msg) {
            printToConsole(msg);
        }

        @Override
        public void connected(InetSocketAddress serverAddress) {
            printToConsole("Connected to " + serverAddress.getHostName() + ":"
                           + serverAddress.getPort());
        }

        @Override
        public void disconnected() {
            printToConsole("Disconnected from server.");
        }

        private void printToConsole(String output) {
            outMgr.println(output);
            outMgr.print(PROMPT);
        }
    }
}
