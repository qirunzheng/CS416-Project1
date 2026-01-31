import java.net.*;
import java.io.*;
import java.util.*;


public class Host {

    private String hostId;
    private String myIp;
    private int myPort;


    private String switchIp;
    private int switchPort;

    private DatagramSocket socket;

    public Host(String hostId, String configFile) throws Exception {
        this.hostId = hostId;
        parseConfig(configFile);
        socket = new DatagramSocket(myPort);
        System.out.println("Host " + hostId + " started at " + myIp + ":" + myPort);
    }


    private void parseConfig(String configFile) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(configFile));
        String line;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");

            if (parts[0].equals(hostId)) {
                myIp = parts[1];
                myPort = Integer.parseInt(parts[2]);
                String switchId = parts[3];


                findSwitch(configFile, switchId);
                break;
            }
        }
        br.close();
    }

    private void findSwitch(String configFile, String switchId) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(configFile));
        String line;

        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            String[] parts = line.split("\\s+");
            if (parts[0].equals(switchId)) {
                switchIp = parts[1];
                switchPort = Integer.parseInt(parts[2]);
                break;
            }
        }
        br.close();
    }


    private void startReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buf = new byte[1024];
            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String frame = new String(packet.getData(), 0, packet.getLength());
                    handleFrame(frame);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        receiver.start();
    }


    private void handleFrame(String frame) {

        String[] parts = frame.split(":", 3);
        if (parts.length < 3) return;

        String src = parts[0];
        String dst = parts[1];
        String msg = parts[2];

        System.out.println("Received message from " + src + ": " + msg);

        if (!dst.equals(hostId)) {
            System.out.println("[DEBUG] MAC address mismatch (flooded frame)");
        }
    }

    private void startSender() throws Exception {
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter destination host ID: ");
            String dst = scanner.nextLine().trim();

            System.out.print("Enter message: ");
            String msg = scanner.nextLine().trim();

            String frame = hostId + ":" + dst + ":" + msg;
            byte[] data = frame.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(switchIp),
                    switchPort
            );

            socket.send(packet);
            System.out.println("Frame sent: " + frame);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: java Host <HostID> <ConfigFile>");
            return;
        }

        Host host = new Host(args[0], args[1]);
        host.startReceiver();
        host.startSender();
    }
}
