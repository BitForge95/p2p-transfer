package com;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Main {
    //Generated from AI just to check whether the bencode parser is able to read the .torrent file or not
    public static void main(String[] args) {
        try {
            // 1. Read the file into a byte array
            String filePath = "ubuntu.torrent"; // Make sure this file exists!
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));

            // 2. Parse it
            BencodeParser parser = new BencodeParser(fileBytes);
            Map<String, Object> torrentData = (Map<String, Object>) parser.decode();

            // 3. Extract key info
            System.out.println("Parsing successful!");
            
            // The tracker URL is usually under the key "announce"
            if (torrentData.containsKey("announce")) {
                System.out.println("Tracker URL: " + new String((byte[]) torrentData.get("announce")));
            }
            
            // File info is in a nested dictionary called "info"
            if (torrentData.containsKey("info")) {
                Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
                if (info.containsKey("name")) {
                    System.out.println("File Name: " + new String((byte[]) info.get("name")));
                }
                if (info.containsKey("length")) {
                    System.out.println("File Size: " + info.get("length") + " bytes");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}