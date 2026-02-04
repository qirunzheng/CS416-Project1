import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Host {

    private final String hostId;

    private String myIp;
    private int myPort;
    private String switchIp;
    private int switchPort;

    private DatagramSocket socket;

    public Host(String hostId, String configFile) throws Exception {
        this.hostId = hostId.trim();
        parseConfigDeviceLink(configFile);

        if (myIp == null || myPort <= 0) {
            throw new IllegalStateException("Host IP/port not parsed correctly");
        }
        if (switchIp == null || switchPort <= 0) {
            throw new IllegalStateException("Neighbor switch not parsed correctly");
        }

        socket = new DatagramSocket(myPort);
        socket.setReuseAddress(true);

        System.out.println("Host " + hostId + " started at " + myIp + ":" + myPort);
        System.out.println("Connected switch at " + switchIp + ":" + switchPort);
        System.out.println();
    }

    private void parseConfigDeviceLink(String configFile) throws Exception {
        Map<String, String> ipMap = new HashMap<>();
        Map<String, Integer> portMap = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        File file = new File(configFile);

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int lineno = 0;

            while ((line = br.readLine()) != null) {
                lineno++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] t = line.split("\\s+");
                String kind = t[0].toUpperCase(Locale.ROOT);

                if ("DEVICE".equals(kind)) {
                    if (t.length < 4) {
                        throw new IllegalArgumentException("Bad DEVICE at line " + lineno);
                    }
                    String id = t[1];
                    String ip = t[2];
                    int port = Integer.parseInt(t[3]);

                    ipMap.put(id, ip);
                    portMap.put(id, port);
                    adj.putIfAbsent(id, new ArrayList<>());

                } else if ("LINK".equals(kind)) {
                    if (t.length < 3) {
                        throw new IllegalArgumentException("Bad LINK at line " + lineno);
                    }
                    String a = t[1];
                    String b = t[2];

                    adj.putIfAbsent(a, new ArrayList<>());
                    adj.putIfAbsent(b, new ArrayList<>());
                    adj.get(a).add(b);
                    adj.get(b).add(a);

                } else {
                    throw new IllegalArgumentException("Unknown config entry at line " + lineno);
                }
            }
        }


        if (!ipMap.containsKey(hostId)) {
            throw new IllegalArgumentException("Config missing DEVICE for host " + hostId);
        }
        myIp = ipMap.get(hostId);
        myPort = portMap.get(hostId);


        List<String> neighbors = adj.getOrDefault(hostId, List.of());
        if (neighbors.isEmpty()) {
            throw new IllegalArgumentException("Host " + hostId + " has no LINK neighbor");
        }

        String switchId = neighbors.get(0);
        switchIp = ipMap.get(switchId);
        switchPort = portMap.get(switchId);

        if (switchIp == null || switchPort <= 0) {
            throw new IllegalArgumentException("Switch " + switchId + " missing DEVICE entry");
        }
    }

    private void startReceiver() {
        Thread receiver = new Thread(() -> {
            byte[] buf = new byte[4096];

            while (true) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);

                    String frame = new String(
                            packet.getData(), 0, packet.getLength(),
                            StandardCharsets.UTF_8
                    ).trim();

                    handleFrame(frame);

                } catch (IOException e) {
                    System.out.println("Receiver error on host " + hostId);
                    e.printStackTrace();
                }
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private void handleFrame(String frame) {
        String[] parts = frame.split(":", 3);
        if (parts.length < 3) return;

        String src = parts[0].trim();
        String dst = parts[1].trim();
        String msg = parts[2];

        if (dst.equals(hostId)) {
            System.out.println("Message: \"" + msg + "\"  Source: " + src);
        } else {
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
            byte[] data = frame.getBytes(StandardCharsets.UTF_8);

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
