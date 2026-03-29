package com;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.nio.ByteBuffer;

public class TrackerClient {
    // Encodes raw bytes for tracker query parameters.

    private String urlEncodeBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            if ((b >= '0' && b <= '9') ||
                (b >= 'A' && b <= 'Z') ||
                (b >= 'a' && b <= 'z') ||
                b == '.' || b == '-' || b == '_' || b == '~') {
                sb.append((char) b);
            } else {
                sb.append(String.format("%%%02x", b));
            }
        }
        return sb.toString();
    }

    public byte[] requestPeers(String announceUrl, byte[] infoHash, byte[] peerId, int port) {
        try {
            String url = String.format("%s?info_hash=%s&peer_id=%s&port=%d&uploaded=0&downloaded=0&left=0&compact=1&event=started",
                    announceUrl,
                    urlEncodeBytes(infoHash),
                    urlEncodeBytes(peerId),
                    port
            );

            System.out.println("Contacting Tracker: " + url);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Tracker request failed. Status Code: " + response.statusCode());
            }

            System.out.println("Tracker responded! Response size: " + response.body().length + " bytes");
            return response.body();

        } catch (Exception e) {
            throw new RuntimeException("Error contacting tracker", e);
        }
    }

    public List<Peer> parseResponse(byte[] responseBody) {

        try {
            BencodeParser parser = new BencodeParser(responseBody);
            Map<String,Object> responseMap = (Map<String,Object>) parser.decode();
    
            if (responseMap.containsKey("failure reason")) {
                    String reason = new String((byte[]) responseMap.get("failure reason"));
                    throw new RuntimeException("Tracker Error: " + reason);
                }
    
            if (!responseMap.containsKey("peers")) {
                    throw new RuntimeException("Tracker response missing 'peers' field");
                }

            byte[] peersBlob = (byte[]) responseMap.get("peers");
            return parseCompactPeers(peersBlob);  

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tracker response", e);
        }
    }

    private List<Peer> parseCompactPeers(byte[] blob) {
        List<Peer> peers = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(blob);

        // Compact peers format uses 6 bytes per peer: 4 for IP and 2 for port.
        while (buffer.remaining() >= 6) {
            byte[] ipBytes = new byte[4];
            buffer.get(ipBytes);
            String ip = (ipBytes[0] & 0xFF) + "." + 
                        (ipBytes[1] & 0xFF) + "." + 
                        (ipBytes[2] & 0xFF) + "." + 
                        (ipBytes[3] & 0xFF);

            int port = buffer.getShort() & 0xFFFF;

            peers.add(new Peer(ip, port));
        }
        return peers;
    }
}
