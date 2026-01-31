package com;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {

    public static void main(String[] args) {

    // Generated from AI just to check whether the bencode parser is able to read the .torrent file or not
        try {
            System.out.println("--- JTorrent: BitTorrent Client v1.0 ---");

            // 1. Read the .torrent file
            // Make sure "ubuntu.torrent" is in your project root folder (next to pom.xml)
            String filePath = "kali.torrent"; 
            if (!Files.exists(Paths.get(filePath))) {
                System.err.println("Error: File not found: " + filePath);
                return;
            }
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            System.out.println("Loaded file: " + filePath);

            // 2. Parse the Bencoded data
            BencodeParser parser = new BencodeParser(fileBytes);
            Map<String, Object> torrentData = (Map<String, Object>) parser.decode();

            System.out.println("\n--- Metadata Parsed ---");
            
            // 3. Print Tracker URL (Announce) and store it for Day 4
            String announceUrl = null;
            if (torrentData.containsKey("announce")) {
                // Some torrents use byte[] for strings, so we cast to byte[] then new String()
                Object announceObj = torrentData.get("announce");
                announceUrl = (announceObj instanceof byte[]) ? 
                    new String((byte[]) announceObj, StandardCharsets.UTF_8) : (String) announceObj;
                    
                System.out.println("Tracker URL: " + announceUrl);
            }

            // 4. Print File Info
            byte[] infoHash = null; // Store for Day 4 usage
            if (torrentData.containsKey("info")) {
                Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
                
                if (info.containsKey("name")) {
                    Object nameObj = info.get("name");
                    String name = (nameObj instanceof byte[]) ? 
                        new String((byte[]) nameObj, StandardCharsets.UTF_8) : (String) nameObj;
                    System.out.println("File Name:   " + name);
                }
                
                if (info.containsKey("length")) {
                    System.out.println("File Size:   " + info.get("length") + " bytes");
                }

                // 5. Calculate Info Hash
                System.out.println("\n--- Cryptography ---");
                infoHash = calculateInfoHash(torrentData);
                String hexHash = bytesToHex(infoHash);
                System.out.println("Info Hash:   " + hexHash);
                // Sucessfully calculated INfo Hash , verified from ubuntu website 
            }

            // 6. Generate Peer ID
            byte[] myPeerId = generatePeerId();
            System.out.println("My Peer ID:  " + new String(myPeerId, StandardCharsets.UTF_8));

            // Now we use the data we parsed to ask the Tracker for peers
            List<Peer> peers = null;

            if (announceUrl != null && infoHash != null) {
                if (announceUrl.startsWith("http")) {
                    System.out.println("\n--- Connecting to Tracker ---");
                    
                    TrackerClient trackerClient = new TrackerClient();
                    // Port 6881 is standard for BitTorrent
                    // TODO : Shift the PORT from 6881 to 52347

                    byte[] response = trackerClient.requestPeers(announceUrl, infoHash, myPeerId, 54321);
                    
                    System.out.println("Tracker Response received. Parsing...");
                    peers = trackerClient.parseResponse(response);
                    
                    System.out.println("Found " + peers.size() + " peers:");
                    for (Peer p : peers) {
                        System.out.println(" - " + p);
                    }
                    System.out.println("This binary data contains the IP addresses of peers.");
                } else {
                    System.err.println("\nWARNING: This torrent uses a UDP tracker (" + announceUrl + ").");
                    System.err.println("This client currently only supports HTTP trackers.");
                }
            }
            
            // TCP Handshake
            if (peers != null && !peers.isEmpty()) {
                System.out.println("\n--- Starting Handshake Sequence ---");
                System.out.println("We have " + peers.size() + " candidates.");

                boolean connected = false;

                for (Peer targetPeer : peers) {
                    System.out.println("\nTrying Peer: " + targetPeer + "...");
                    
                    try (Socket socket = new Socket()) {
                        // 1. Connect (Timeout 3 seconds to fail fast)
                        socket.connect(new InetSocketAddress(targetPeer.getIp(), targetPeer.getPort()), 3000);
                        System.out.println("  -> TCP Connection established!");

                        OutputStream out = socket.getOutputStream();
                        InputStream in = socket.getInputStream();

                        // 2. Send Handshake
                        byte[] handshakeMsg = Handshake.buildHandshake(infoHash, myPeerId);
                        out.write(handshakeMsg);
                        
                        // 3. Read Response
                        byte[] response = new byte[68];
                        // We strictly expect 68 bytes
                        int bytesRead = 0;
                        while(bytesRead < 68) {
                            int count = in.read(response, bytesRead, 68 - bytesRead);
                            if(count == -1) break; // End of stream
                            bytesRead += count;
                        }

                        if (bytesRead < 68) {
                            System.err.println("  -> Failed: Peer closed connection early.");
                        } else {
                            // 4. Verify Response
                            if (Handshake.verify(response, infoHash)) {
                                System.out.println("  -> SUCCESS: Handshake verified!"); 
                                System.out.println("  -> Peer ID: " + new String(Arrays.copyOfRange(response, 48, 68)));
                                connected = true;
                                break; // Stop the loop, we found one!
                            } else {
                                System.err.println("  -> Error: Info Hash mismatch.");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("  -> Connection failed: " + e.getMessage());
                    }
                }
                
                if (!connected) {
                    System.out.println("\n--- FAILURE: Could not connect to any peers. ---");
                }

            } else {
                System.out.println("No peers found to connect to.");
            }

        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    //  Calculates the SHA-1 hash of the "info" dictionary.
    //  This requires the BencodeEncoder to be implemented correctly!

    private static byte[] calculateInfoHash(Map<String, Object> torrentData) throws Exception {
        // Extract the 'info' dictionary map
        Map<String, Object> infoMap = (Map<String, Object>) torrentData.get("info");
        
        // Re-encode it back to raw bytes using the Encoder we built in Commit 7
        BencodeEncoder encoder = new BencodeEncoder();
        byte[] infoBytes = encoder.encode(infoMap);
        
        // Compute SHA-1
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(infoBytes);
    }
      
    // Generates a random 20-byte Peer ID.
    // Format: -JT1000- followed by 12 random numbers.
     
    private static byte[] generatePeerId() {
        byte[] peerId = new byte[20];
        
        // Prefix: -JT1000-
        byte[] prefix = "-JT1000-".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(prefix, 0, peerId, 0, prefix.length);
        
        // Random numbers for the rest
        Random random = new Random();
        for (int i = prefix.length; i < 20; i++) {
            peerId[i] = (byte) (random.nextInt(10) + '0'); 
        }
        return peerId;
    }

    // Helper to convert raw bytes to a Hex String (e.g., [10, 15] -> "0a0f")
     
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}