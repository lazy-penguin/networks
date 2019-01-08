package chat_tree;
import java.io.IOException;
import java.net.*;
import java.util.*;

class ReceiverInfo {
    private InetAddress address;
    private int port;
    ReceiverInfo(InetAddress newAddress, int newPort) {
        address = newAddress;
        port = newPort;
    }

    InetAddress getAddress() {return address; }
    int getPort() {return  port; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ReceiverInfo other = (ReceiverInfo) obj;
        if (!address.equals(other.address))
            return false;
        if (port != other.port)
            return false;
        return true;
    }
}

class MessagesInfo implements Cloneable {
    private String text;
    private String id;
    private int attemptNumb;
    private static int REPOST_NUM = 3;
    private ReceiverInfo address;
    private long sendTime;

    MessagesInfo(String text) {
       this.text = text;
       id = UUID.randomUUID().toString();
       attemptNumb = REPOST_NUM;
       sendTime = 0;
    }

    MessagesInfo(String text, String id) {
        this.text = text;
        this.id = id;
        attemptNumb = REPOST_NUM;
        sendTime = 0;
    }

    void setAddress(ReceiverInfo ri) {
        address = ri;
    }
    void setSendTime(long time) {
        sendTime = time;
    }

    String getText() {
        return text;
    }
    String getId() { return id; }
    long getSendTime() {
        return sendTime;
    }
    ReceiverInfo getAddress() { return address; }
    int getAttemptNum() {
        return attemptNumb;
    }
    void decrAttemptNum() {
        attemptNumb--;
    }

    @Override
    public MessagesInfo clone() {
        try {
            return (MessagesInfo) super.clone();
        }
        catch(CloneNotSupportedException e) {
            throw new InternalError();
        }
    }
}

public class Node {
    private static String nodeName;
    private static int lossPercent;
    private static int port;
    private final static LinkedList<MessagesInfo> mesQueue = new LinkedList<>();
    private final static ArrayList<MessagesInfo> sentMesArchive = new ArrayList<>();
    private final static ArrayList<ReceiverInfo> receivers = new ArrayList<>();

    private static void reader() {
        Scanner in = new Scanner(System.in);
        while (true) {
            if (in.hasNext()) {
                MessagesInfo newMessage = new MessagesInfo(nodeName + ": " + in.nextLine());
                synchronized (mesQueue) {
                    mesQueue.add(newMessage);
                    mesQueue.notify();
                }
            }
        }
    }

    private static void init(String[] args) {
        if(args.length == 3 || args.length == 5) {
            nodeName = args[0];
            lossPercent = Integer.parseInt(args[1]);
            port = Integer.parseInt(args[2]);
            try {
                if (args.length == 5) {
                    receivers.add(new ReceiverInfo(InetAddress.getByName(args[3]), Integer.parseInt(args[4])));
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        else {
            System.err.println("Usage: <node name> <loss percent> <port>" +
                    " or <node name> <loss percent> <port> <parent's address> <parent's port>");
            System.exit(-1);
        }
    }

    public static void main(String[] args) {
        init(args);

        try (DatagramSocket s = new DatagramSocket(port)) {
            new Sender(s);
            new Receiver(s);
            new Reposter(s);
            reader();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class Receiver implements Runnable {
        DatagramSocket s;
        Thread t;
        Receiver(DatagramSocket s) {
            this.s = s;
            t = new Thread(this, "Receiver");
            t.start();
        }

        void sendConfirmation(ReceiverInfo ri, String id) {
            try {
                byte[] text = id.getBytes();
                DatagramPacket dp = new DatagramPacket(text, text.length, ri.getAddress(), ri.getPort());
                s.send(dp);
            }
            catch(IOException e) {
                e.printStackTrace();
            }
        }

        void processConfirmation(String id,  ReceiverInfo ri) {
            synchronized (sentMesArchive) {
                sentMesArchive.removeIf(message -> id.equals(message.getId()) && ri.equals(message.getAddress()));
            }
            System.out.println("Delivered");
        }

        void processMessage(String text, String id, ReceiverInfo ri) {
            MessagesInfo message = new MessagesInfo(text, id);
            message.setAddress(ri);
            Date date = new Date(System.currentTimeMillis());
            System.out.println(text + " " + date);
            sendConfirmation(ri, id);
            synchronized (mesQueue) {
                mesQueue.add(message);
                mesQueue.notify();
            }

            synchronized (receivers) {
                if (receivers.indexOf(ri) == -1) {
                    receivers.add(ri);
                }
            }
        }

        @Override
        public void run() {
            while(true) {
                byte answer[] = new byte[2048];
                DatagramPacket dp = new DatagramPacket(answer, answer.length);

                try {
                    s.receive(dp);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                int status = (int) (Math.random() * 100);
                if (status < lossPercent)
                    continue;

                ReceiverInfo ri = new ReceiverInfo(dp.getAddress(), dp.getPort());
                int length = dp.getLength();
                String[] tokens = new String(dp.getData()).split(" ", 2);

                if (tokens.length == 1) {
                    processConfirmation(tokens[0].substring(0, length), ri);
                }
                else if (tokens.length == 2) {
                    processMessage(tokens[1], tokens[0], ri);
                }

            }
        }
    }

    private static class Sender implements  Runnable {
        DatagramSocket s;
        Thread t;
        Sender(DatagramSocket s) {
            this.s = s;
            t = new Thread(this, "Sender");
            t.start();
        }

        @Override
        public void run() {
            try {
                while (true) {
                    MessagesInfo message;
                    synchronized (mesQueue) {
                        while (mesQueue.isEmpty()) {
                            mesQueue.wait();
                        }
                        message = mesQueue.remove(0);
                    }

                    synchronized (receivers) {
                        for (ReceiverInfo ri : receivers) {
                            MessagesInfo copy;
                            if (ri.equals(message.getAddress())) {
                                continue;
                            }
                            copy = message.clone();
                            copy.setAddress(ri);
                            byte[] text =(copy.getId() + " " + copy.getText()).getBytes();
                            DatagramPacket dp = new DatagramPacket(text, text.length, ri.getAddress(), ri.getPort());
                            copy.setSendTime(System.currentTimeMillis());
                            s.send(dp);
                            synchronized (sentMesArchive) {
                                sentMesArchive.add(copy);
                            }
                        }
                    }
                }
            }
            catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static class Reposter implements Runnable {
        DatagramSocket s;
        Thread t;

        Reposter(DatagramSocket s) {
            this.s = s;
            t = new Thread(this, "Reposter");
            t.start();
        }

        @Override
        public void run() {
            try {
                while(true) {
                    Thread.sleep(5000);

                    synchronized (sentMesArchive) {
                        for(Iterator<MessagesInfo> it = sentMesArchive.iterator(); it.hasNext();) {
                            MessagesInfo message = it.next();
                            long currTime = System.currentTimeMillis();
                            if(currTime - message.getSendTime() < 5000) {
                                continue;
                            }
                            ReceiverInfo ri = message.getAddress();
                            byte[] text = (message.getId() + " " + message.getText()).getBytes();
                            DatagramPacket dp = new DatagramPacket(text, text.length, ri.getAddress(), ri.getPort());
                            s.send(dp);
                            message.decrAttemptNum();

                            if (message.getAttemptNum() == 0) {
                                it.remove();
                                synchronized (receivers) {
                                    receivers.remove(ri);
                                }
                            }
                        }
                    }
                }
            }
            catch(IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


