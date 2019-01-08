package ServerSide;

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;

public class Server {
    static  ArrayList<String> mesHistory = new ArrayList<>();
    final static ArrayList<User> usersList = new ArrayList<>();

    public static void main(String[] args) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
            server.createContext("/", new Handler());
            new findOfflineUsers();
            server.start();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    static class findOfflineUsers implements Runnable {
        Thread t;
        private final static int TIMEOUT = 5000*60;

        findOfflineUsers() {
            t = new Thread(this, "findOfflineUsers");
            t.start();
        }
        @Override
        public void run() {
            try {
                while (true) {
                    Thread.sleep(TIMEOUT);
                    synchronized (usersList) {
                        for(User u : usersList)
                            if (System.currentTimeMillis() - u.getLastActivity() > TIMEOUT)
                                u.setStatus(false);
                    }
                }
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

