package multicast;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Iterator;
import java.util.TreeSet;

public class MulticastGroup implements Runnable{
    private Thread t;
    private MulticastSocket s;
    private InetAddress groupAddress;
    private int port;
    private int cnt;
    private TreeSet<String> savedIP;

    MulticastGroup(String address, int port) throws IOException {
        cnt = 0;
        this.port = port;
        groupAddress = InetAddress.getByName(address);
        s = new MulticastSocket(port);
        s.joinGroup(groupAddress);
        savedIP = new TreeSet<>();
        t = new Thread(this, "MulticastGroup");
        t.start();
    }

    public void send() throws IOException {
        byte[] message = InetAddress.getLocalHost().getHostAddress().getBytes();
        DatagramPacket hi = new DatagramPacket(message, message.length, groupAddress, port);
        s.send(hi);
        System.out.println("Sent");
    }

    public void printStatistic() {
        System.out.println("Number of copies: " + cnt);
    }

    public void collectCopies(String address) {
        Iterator<String> itr = savedIP.iterator();
        String buf;
        boolean contains = false;
        while(itr.hasNext() && !contains) {
            buf = itr.next();
            if (buf.equals(address)) {
                contains = true;
            }
        }
        if (!contains) {
            cnt++;
            savedIP.add(address);
        }
    }

    public static void main(String[] args) throws IOException {
        MulticastGroup group = new MulticastGroup(args[0], Integer.parseInt(args[1]));
        while(true) {
            try {
                group.send();
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        while(true) {
            byte answer[] = new byte[1000];
            DatagramPacket recv = new DatagramPacket(answer, answer.length);
            try {
                s.receive(recv);
                String address = new String(recv.getData());
                System.out.println("Received " + address);
                collectCopies(address);
                printStatistic();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
