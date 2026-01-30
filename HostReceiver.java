import java.util.*;

public class HostReceiver {

    public static class Port {
        public final String ip;
        public final int port;

        public Port(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Port)) return false;
            Port p = (Port) o;
            return port == p.port && ip.equals(p.ip);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }

        @Override
        public String toString() {
            return ip + ":" + port;
        }
    }


    private final Map<String, Port> table = new HashMap<>();



    public boolean learn(String mac, Port ingressPort) {
        if (!table.containsKey(mac)) {
            table.put(mac, ingressPort);
            return true;
        }
        return false;
    }

    public Port lookup(String mac) {
        return table.get(mac);
    }

    public void print(String switchId) {
        System.out.println("=== Switch Table (" + switchId + ") ===");

        if (table.isEmpty()) {
            System.out.println("(empty)");
        } else {
            List<String> macs = new ArrayList<>(table.keySet());
            Collections.sort(macs);   // stable output

            for (String mac : macs) {
                System.out.println(mac + " -> " + table.get(mac));
            }
        }

        System.out.println("===========================");
        System.out.println();
    }
}
