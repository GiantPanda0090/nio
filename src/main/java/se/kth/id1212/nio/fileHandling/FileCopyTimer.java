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
package se.kth.id1212.nio.fileHandling;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Compares performance of different IO APIs by copying the content of a file.
 */
public class FileCopyTimer {
    private final File inFile;
    private final File outFile;

    private long startTime;

    /**
     * Constructs a class that copies the content of inFile to outFile.
     */
    public FileCopyTimer(File inFile, File outFile) {
        this.inFile = inFile;
        this.outFile = outFile;
    }

    /**
     * Uses the InputStream/OutputStream API in java.io
     */
    public long testStreamAPI() {
        startTimer();

        try {
            try (FileInputStream bis = new FileInputStream(inFile);
                 FileOutputStream bos = new FileOutputStream(outFile)) {
                int c;
                while ((c = bis.read()) != -1) {
                    bos.write(c);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses the Reader/Writer API in java.io
     */
    public long testReaderWriterAPI() {
        startTimer();

        try {
            try (FileReader br = new FileReader(inFile);
                 FileWriter bw = new FileWriter(outFile)) {
                int c;
                while ((c = br.read()) != -1) {
                    bw.write(c);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses the Reader/Writer API in java.io, reads lines instead of bytes.
     */
    public long testLineReaderWriterAPI() {
        startTimer();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(inFile));
                 PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(
                         outFile)))) {
                String s;
                while ((s = br.readLine()) != null) {
                    pw.println(s);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses the Reader/Writer API in java.io, reads buffers instead of bytes.
     */
    public long testBufferedReaderWriterAPI() {
        startTimer();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(inFile));
                 PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(
                         outFile)))) {
                char[] buf = new char[4096];
                while (br.read(buf, 0, buf.length) != -1) {
                    pw.write(buf, 0, buf.length);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses the InputStream/OutputStream API in java.io, reads buffers instead of bytes.
     */
    public long testBufferedStreamAPI() {
        startTimer();

        try {
            try (BufferedInputStream bis = new BufferedInputStream(
                    new FileInputStream(inFile));
                 PrintStream ps = new PrintStream(new BufferedOutputStream(
                         new FileOutputStream(outFile)))) {
                byte[] buf = new byte[8192];
                while (bis.read(buf, 0, buf.length) != -1) {
                    ps.write(buf, 0, buf.length);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses an indirect buffer in java.nio
     */
    public long testIndirectNio() {
        startTimer();

        try {
            try (FileInputStream fis = new FileInputStream(inFile);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                FileChannel inChannel = fis.getChannel();
                FileChannel outChannel = fos.getChannel();
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                int c = 0;
                while ((c = inChannel.read(buffer)) != -1) {
                    buffer.flip();
                    outChannel.write(buffer);
                    buffer.clear();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();

    }

    /**
     * Uses a direct buffer in java.nio
     */
    public long testDirectNio() {
        startTimer();

        try {
            try (FileInputStream fis = new FileInputStream(inFile);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                FileChannel inChannel = fis.getChannel();
                FileChannel outChannel = fos.getChannel();
                ByteBuffer buffer = ByteBuffer.allocateDirect(8192);
                int c = 0;
                while ((c = inChannel.read(buffer)) != -1) {
                    buffer.flip();
                    outChannel.write(buffer);
                    buffer.clear();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();

    }

    /**
     * Uses java.nio and transfers from channel to channel without buffers.
     */
    public long testTransferNio() {
        startTimer();

        try {
            try (FileInputStream fis = new FileInputStream(inFile);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                FileChannel inChannel = fis.getChannel();
                FileChannel outChannel = fos.getChannel();
                inChannel.transferTo(0, inChannel.size(), outChannel);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();

    }

    private void startTimer() {
        startTime = System.currentTimeMillis();
    }

    private long stopTimer() {
        long time = System.currentTimeMillis() - startTime;
        startTime = 0;
        return time;
    }

    public static void main(String[] args) {
        FileCopyTimer fct = new FileCopyTimer(new File(args[0]), new File(
                                              args[1]));

        System.out.println("Using the ReaderWriter API, copying bytes:"
                           + fct.testReaderWriterAPI() + " ms");
        System.out.println("Using the ReaderWriter API, copying lines:"
                           + fct.testLineReaderWriterAPI() + " ms");
        System.out.println("Using the ReaderWriter API, copying buffers:"
                           + fct.testBufferedReaderWriterAPI() + " ms");
        System.out.println("Using the Stream API, copying bytes:"
                           + fct.testStreamAPI() + " ms");
        System.out.println("Using the Stream API, copying buffers:"
                           + fct.testBufferedStreamAPI() + " ms");
        System.out.println("Using the indirect nio API:"
                           + fct.testIndirectNio() + " ms");
        System.out.println("Using the direct nio API:" + fct.testDirectNio()
                           + " ms");
        System.out.println("Using the transfer nio API:"
                           + fct.testTransferNio() + " ms");
    }

}
