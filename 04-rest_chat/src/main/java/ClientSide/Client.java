package ClientSide;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;
import java.util.Scanner;

public class Client {
    private String rawName;
    private UUID token;
    private Scanner sc;

    private Client() {
        System.out.print("Hello! Create your username: ");
        sc = new Scanner(System.in);
        rawName = sc.nextLine();
    }

    private void setConnection(String address) {
        String pattern = "http://localhost:8080/";
        address = pattern + address;
        HttpURLConnection httpConnection = null;
        URL url = null;
        try {
            url = new URL(address);
            httpConnection = (HttpURLConnection) url.openConnection();
        }
        catch(IOException e) {
            e.printStackTrace();
        }
        ClientConnection connection = new ClientConnection(this, url, httpConnection);
        connection.handle();
    }

    private void startSession() {
        System.out.print("Now you can login: ");
        String address = null;
        while(true) {
            if (sc.hasNext()) {
                address = sc.nextLine();
            }
            setConnection(address);
        }
    }

    public static void main(String[] args) {
        Client c = new Client();
        c.startSession();
    }

    void printResponse(String response) {
        System.out.println(response);
    }

    void printErrorMess(String errMess) {
        System.out.println("Error occurred: "+ errMess);
    }

    void errorAuthorization() {
        System.out.print("Please, enter another username : ");
        rawName = sc.nextLine();
        System.out.print("Now you can login again: ");
    }

    void login(UUID newToken) {
        token = newToken;
        new getMessageList();
    }

    void logout() {
        token = null;
        System.out.print("Please, enter your username: ");
        rawName = sc.nextLine();
        System.out.print("Now you can login again: ");
    }

    UUID getToken() {
        return token;
    }
    String getRawName() {
        return rawName;
    }
    String getMessage() {
        System.out.print("Enter your message: ");
        return sc.nextLine();
    }

    class getMessageList implements  Runnable {
        private final static int TIMEOUT = 10000;

        getMessageList() {
            Thread t = new Thread(this, "getMessageList");
            t.start();
        }

        @Override
        public void run() {
            try {
                while (token != null) {
                    setConnection("messages?offset=0&count=10");
                    Thread.sleep(TIMEOUT);
                }
            }
            catch(InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
