package com;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Random;

public class Main {

    public static void main(String[] args) {

    //Generated from AI just to check whether the bencode parser is able to read the .torrent file or not
        try {
            System.out.println("--- JTorrent: BitTorrent Client v1.0 ---");

            // 1. Read the .torrent file
            // Make sure "ubuntu.torrent" is in your project root folder (next to pom.xml)
            String filePath = "ubuntu.torrent"; 
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            System.out.println("Loaded file: " + filePath);

            // 2. Parse the Bencoded data
            BencodeParser parser = new BencodeParser(fileBytes);
            Map<String, Object> torrentData = (Map<String, Object>) parser.decode();

            System.out.println("\n--- Metadata Parsed ---");
            
            // 3. Print Tracker URL (Announce)
            if (torrentData.containsKey("announce")) {
                // Some torrents use byte[] for strings, so we cast to byte[] then new String()
                Object announceObj = torrentData.get("announce");
                String announceUrl = (announceObj instanceof byte[]) ? 
                    new String((byte[]) announceObj, StandardCharsets.UTF_8) : (String) announceObj;
                    
                System.out.println("Tracker URL: " + announceUrl);
            }

            // 4. Print File Info
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
                byte[] infoHash = calculateInfoHash(torrentData);
                String hexHash = bytesToHex(infoHash);
                System.out.println("Info Hash:   " + hexHash);
                System.out.println("(Check this against the website where you downloaded the torrent!)");
            }

            // 6. Generate Peer ID
            byte[] myPeerId = generatePeerId();
            System.out.println("My Peer ID:  " + new String(myPeerId, StandardCharsets.UTF_8));

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

    //Sucessfully calculated INfo Hash , verified from ubuntu website 

      
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