package com;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

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
}
