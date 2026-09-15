package net.kdt.pojavlaunch.nova;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NovaLanScanner {

    private static final String TAG = "NovaLanScanner";
    private static final String GROUP = "224.0.2.60";
    private static final int PORT = 4445;

    private static final Pattern MOTD = Pattern.compile("\\[MOTD](.*?)\\[/MOTD]");
    private static final Pattern AD = Pattern.compile("\\[AD](.*?)\\[/AD]");

    private NovaLanScanner() {}

    public static final class LanServer {
        public final String motd;
        public final String address;
        public final int port;

        LanServer(String motd, String address, int port) {
            this.motd = motd;
            this.address = address;
            this.port = port;
        }

        public String displayName() {
            String clean = motd == null ? "" : motd.replaceAll("\u00A7.", "").trim();
            return clean.isEmpty() ? address + ":" + port : clean;
        }

        public String connectAddress() {
            return address + ":" + port;
        }
    }

    public interface Listener {
        void onServerFound(LanServer server);
    }

    public static List<LanServer> scan(int timeoutMs, Listener listener) {
        Map<String, LanServer> found = new LinkedHashMap<>();
        MulticastSocket socket = null;
        try {
            socket = new MulticastSocket(PORT);
            socket.setReuseAddress(true);
            socket.setSoTimeout(500);

            InetAddress group = InetAddress.getByName(GROUP);
            NetworkInterface networkInterface = firstUsableInterface();
            try {
                if (networkInterface != null) {
                    socket.joinGroup(new InetSocketAddress(group, PORT), networkInterface);
                } else {
                    socket.joinGroup(group);
                }
            } catch (IOException e) {
                socket.joinGroup(group);
            }

            byte[] buffer = new byte[1024];
            long deadline = System.currentTimeMillis() + Math.max(1000, timeoutMs);

            while (System.currentTimeMillis() < deadline) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(packet);
                } catch (java.net.SocketTimeoutException e) {
                    continue;
                }

                String message = new String(packet.getData(), 0, packet.getLength(),
                        StandardCharsets.UTF_8);
                LanServer server = parse(message, packet.getAddress().getHostAddress());
                if (server == null) continue;

                if (found.put(server.connectAddress(), server) == null && listener != null) {
                    listener.onServerFound(server);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "LAN scan failed", t);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    private static LanServer parse(String message, String senderAddress) {
        Matcher motdMatcher = MOTD.matcher(message);
        Matcher adMatcher = AD.matcher(message);
        if (!adMatcher.find()) return null;
        String portText = adMatcher.group(1);
        if (portText == null) return null;
        portText = portText.trim();
        if (portText.contains(":")) portText = portText.substring(portText.lastIndexOf(':') + 1);
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            return null;
        }
        if (port <= 0 || port > 65535) return null;
        String motd = motdMatcher.find() ? motdMatcher.group(1) : null;
        return new LanServer(motd, senderAddress, port);
    }

    private static NetworkInterface firstUsableInterface() {
        try {
            java.util.Enumeration<NetworkInterface> interfaces =
                    NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface candidate = interfaces.nextElement();
                if (candidate.isLoopback() || !candidate.isUp()) continue;
                if (!candidate.supportsMulticast()) continue;
                if (candidate.getInetAddresses().hasMoreElements()) return candidate;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Interface lookup failed", t);
        }
        return null;
    }
}
