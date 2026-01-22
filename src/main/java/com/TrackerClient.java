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

    //Safely URL-encodes a byte array (for Info Hash and Peer ID).
    //Standard Java URLEncoder is designed for Strings, not raw bytes, so we do this manually.

    private String urlEncodeBytes(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            //Check if character is unreserved (A-Z, a-z, 0-9, -, ., _, ~)
            //If so, append it directly. If not, percent-encode it.
            if ((b >= '0' && b <= '9') ||
                (b >= 'A' && b <= 'Z') ||
                (b >= 'a' && b <= 'z') ||
                b == '.' || b == '-' || b == '_' || b == '~') {
                sb.append((char) b);
            } else {
                //Convert to %XY format
                sb.append(String.format("%%%02x", b));
            }
        }
        return sb.toString();
    }


    // From Stack Overflow 
    public byte[] requestPeers(String announceUrl, byte[] infoHash, byte[] peerId, int port) {
        try {
            // 1. Construct the URL with parameters
            String url = String.format("%s?info_hash=%s&peer_id=%s&port=%d&uploaded=0&downloaded=0&left=0&compact=1&event=started",
                    announceUrl,
                    urlEncodeBytes(infoHash),
                    urlEncodeBytes(peerId),
                    port
            );

            System.out.println("Contacting Tracker: " + url);

            // 2. Build the HTTP Request
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            // 3. Send and get response as byte array (because the response is Bencoded!)
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
            // Decodeing the bencoded tracker Response
            BencodeParser parser = new BencodeParser(responseBody);
            Map<String,Object> responseMap = (Map<String,Object>) parser.decode();
    
            // Safety check just in case the Hash Info is Invalid
            if (responseMap.containsKey("failure reason")) {
                    String reason = new String((byte[]) responseMap.get("failure reason"));
                    throw new RuntimeException("Tracker Error: " + reason);
                }
    
            if (!responseMap.containsKey("peers")) {
                    throw new RuntimeException("Tracker response missing 'peers' field");
                }
               
            // Check for the Peers field in the Tracker REsponse
            byte[] peersBlob = (byte[]) responseMap.get("peers");
            return parseCompactPeers(peersBlob);  

        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tracker response", e);
        }
    }

    private List<Peer> parseCompactPeers(byte[] blob) {
        List<Peer> peers = new ArrayList<>();
        ByteBuffer buffer = ByteBuffer.wrap(blob);

        // Iterate 6 bytes at a time , cause now we switch from the old dictionary to the Compact Format
        // Where every 6bytes represents a person
        // Bytes 1-4: The IP Address (4 bytes = 32 bits, standard IPv4).
        // Bytes 5-6: The Port Number (2 bytes = 16 bits, standard Port).
        // Source : Theory.org
        while (buffer.remaining() >= 6) {
            // 1. Read IP (4 bytes)
            byte[] ipBytes = new byte[4];
            buffer.get(ipBytes);
            String ip = (ipBytes[0] & 0xFF) + "." + 
                        (ipBytes[1] & 0xFF) + "." + 
                        (ipBytes[2] & 0xFF) + "." + 
                        (ipBytes[3] & 0xFF);

            // Read the PORT , I have Treated the PORT number as unsigned 
            int port = buffer.getShort() & 0xFFFF;

            peers.add(new Peer(ip, port));
        }
        return peers;
    }
}
