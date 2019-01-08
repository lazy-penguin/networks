package file_transfer;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;

public class Client {
    public static void main(String[] args) {
        String path = args[0];
        String address = args[1];
        int port = Integer.parseInt(args[2]);
        File f = new File(path);
        if (!f.exists()) {
            System.err.println("File doesn't exist");
            System.exit(1);
        }
        if (!f.isFile()) {
            System.err.println("File is not regular");
            System.exit(1);
        }
        String fileName = f.getName();
        long fileSize = f.length();
        if (fileName.length() > 4096) {
            System.err.println("File name cannot be bigger than 4096 bytes");
            System.exit(1);
        }
        if (fileSize > (long)1024*1024*1024*1024) {
            System.err.println("File name cannot be bigger than 1Tb");
            System.exit(1);
        }

        /*name size, file size, name, file*/
        try (Socket s = new Socket(address, port)) {
            try (BufferedInputStream is = new BufferedInputStream(s.getInputStream());
                 BufferedOutputStream os = new BufferedOutputStream(s.getOutputStream())) {

                byte name[] = fileName.getBytes("UTF-8");

                /*name size*/
                os.write(name.length);

                /*file size*/
                ByteBuffer fileSizeBuf = ByteBuffer.allocate(Long.BYTES);
                fileSizeBuf.putLong(fileSize);
                os.write(fileSizeBuf.array());

                /*name*/
                os.write(name);

                /*file*/
                try (BufferedInputStream fis = new BufferedInputStream(new FileInputStream(path))) {
                    byte fileBuf[] = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(fileBuf)) > 0) {
                        os.write(fileBuf, 0, bytesRead);
                    }
                    os.flush();
                }

                /*answer*/
                if (is.read() == 5)
                    System.out.println("File received");
                else
                    System.err.println("Cannot receive the server's answer");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
}