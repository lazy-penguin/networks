package file_transfer;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Server implements Runnable {
    private Socket s;
    public Server(Socket s) {
        this.s = s;
        Thread t = new Thread(this, "Server");
        t.start();
    }

    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        try (ServerSocket server = new ServerSocket(port)) {
            System.out.println("Server is started");

            while (true) {
                new Server(server.accept());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try (BufferedInputStream is = new BufferedInputStream(s.getInputStream());
             BufferedOutputStream os = new BufferedOutputStream(s.getOutputStream())) {
            /*name size*/
            int nameSize;
            nameSize = is.read();

            /*file size*/
            byte fileSizeBuf[] = new byte[8];
            is.read(fileSizeBuf, 0, 8);

            ByteBuffer buf = ByteBuffer.allocate(Long.BYTES);
            buf.put(fileSizeBuf);
            buf.flip();
            long fileSize = buf.getLong();

            /*name*/
            byte nameBuf[] = new byte[nameSize];
            is.read(nameBuf, 0, nameSize);
            String fileName = new String(nameBuf, "UTF-8");

            /*file*/
            File file = new File("uploads/" + fileName);
            SpeedCounter speed = new SpeedCounter();
            try (BufferedOutputStream fos = new BufferedOutputStream(new FileOutputStream(file))) {
                byte fileBuf[] = new byte[8192];
                int bytesRead;
                long fileBytes = 0;
                while (fileBytes < fileSize && (bytesRead = is.read(fileBuf)) > 0) {
                    fileBytes += bytesRead;
                    speed.incBytesCounter(bytesRead);
                    fos.write(fileBuf, 0, bytesRead);
                }
                speed.sendStopSignal();
            }
            System.out.println("File " + fileName + " (" + fileSize/1024 + "kb) was received");

            /*send answer*/
            os.write(5);
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }
}

class SpeedCounter implements Runnable {
    private long speed;
    private boolean stopSignal;
    private int bytesCounter;

    SpeedCounter() {
        speed = 0;
        bytesCounter = 0;
        stopSignal = false;
        Thread t = new Thread(this, "SpeedCounter");
        t.start();
    }

    void incBytesCounter(int bytes) {
        bytesCounter += bytes;
    }

    private int getBytesCounter() {
        return bytesCounter;
    }

    private void resetBytesCounter() {
        bytesCounter = 0;
    }

    void sendStopSignal() {
        stopSignal = true;
    }
    public void run() {
        try {
            long start;
            long time;
            while(!stopSignal) {
                start = System.currentTimeMillis();
                Thread.sleep(3000);
                time = System.currentTimeMillis() - start;
                speed = (getBytesCounter() / (1024 * 1024)) / (time/1000);
                resetBytesCounter();
                System.out.println(speed + "mB/sec");
            }
        }
        catch(InterruptedException e) {
            e.printStackTrace();
        }
    }
}

