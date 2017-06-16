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
    private File inFile;
    private File outFile;

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
            BufferedInputStream bis = new BufferedInputStream(
                    new FileInputStream(inFile));
            BufferedOutputStream bos = new BufferedOutputStream(
                    new FileOutputStream(outFile));
            int c = 0;
            while ((c = bis.read()) != -1) {
                bos.write(c);
            }
            bis.close();
            bos.close();
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
            BufferedReader br = new BufferedReader(new FileReader(inFile));
            BufferedWriter bw = new BufferedWriter(new FileWriter(outFile));
            int c = 0;
            while ((c = br.read()) != -1) {
                bw.write(c);
            }
            br.close();
            bw.close();
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
            BufferedReader br = new BufferedReader(new FileReader(inFile));
            PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(
                    outFile)));
            String s;
            while ((s = br.readLine()) != null) {
                pw.println(s);
            }
            br.close();
            pw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return stopTimer();
    }

    /**
     * Uses the InputStream/OutputStream API in java.io, reads lines instead of bytes.
     */
    public long testLineStreamAPI() {
        startTimer();

        try {
            BufferedInputStream bis = new BufferedInputStream(
                    new FileInputStream(inFile));
            PrintStream ps = new PrintStream(new BufferedOutputStream(
                    new FileOutputStream(outFile)));
            byte[] buf = new byte[10];
            while (bis.read(buf, 0, buf.length) != -1) {
                ps.write(buf, 0, buf.length);
            }
            bis.close();
            ps.close();
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
            FileInputStream fis = new FileInputStream(inFile);
            FileOutputStream fos = new FileOutputStream(outFile);
            FileChannel inChannel = fis.getChannel();
            FileChannel outChannel = fos.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int c = 0;
            while ((c = inChannel.read(buffer)) != -1) {
                buffer.flip();
                outChannel.write(buffer);
                buffer.clear();
            }
            fis.close();
            fos.close();
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
            FileInputStream fis = new FileInputStream(inFile);
            FileOutputStream fos = new FileOutputStream(outFile);
            FileChannel inChannel = fis.getChannel();
            FileChannel outChannel = fos.getChannel();
            ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
            int c = 0;
            while ((c = inChannel.read(buffer)) != -1) {
                buffer.flip();
                outChannel.write(buffer);
                buffer.clear();
            }
            fis.close();
            fos.close();
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
            FileInputStream fis = new FileInputStream(inFile);
            FileOutputStream fos = new FileOutputStream(outFile);
            FileChannel inChannel = fis.getChannel();
            FileChannel outChannel = fos.getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            fis.close();
            fos.close();
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

        fct.testReaderWriterAPI(); // warmup.

        System.out.println("Using the ReaderWriter API, copying bytes:"
                           + fct.testReaderWriterAPI() + " ms");
        System.out.println("Using the ReaderWriter API, copying lines:"
                           + fct.testLineReaderWriterAPI() + " ms");
        System.out.println("Using the Stream API, copying bytes:"
                           + fct.testStreamAPI() + " ms");
        System.out.println("Using the Stream API, copying lines:"
                           + fct.testLineStreamAPI() + " ms");
        System.out.println("Using the indirect nio API:"
                           + fct.testIndirectNio() + " ms");
        System.out.println("Using the direct nio API:" + fct.testDirectNio()
                           + " ms");
        System.out.println("Using the transfer nio API:"
                           + fct.testTransferNio() + " ms");
    }

}
