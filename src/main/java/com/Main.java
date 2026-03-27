package com;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream; 
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.io.DataInputStream;

public class Main {

    public static void main(String[] args) {

    // Generated from AI just to check whether the bencode parser is able to read the .torrent file or not
        try {
            System.out.println("--- JTorrent: BitTorrent Client v1.0 ---");

            // 1. Read the .torrent file
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
            
            // 3. Print Tracker URL (Announce)
            String announceUrl = null;
            if (torrentData.containsKey("announce")) {
                // Some torrents use byte[] for strings, so we cast to byte[] then new String()
                Object announceObj = torrentData.get("announce");
                announceUrl = (announceObj instanceof byte[]) ? 
                    new String((byte[]) announceObj, StandardCharsets.UTF_8) : (String) announceObj;
                    
                System.out.println("Tracker URL: " + announceUrl);
            }

            // 4. Print File Info & Calculate Total Pieces
            byte[] infoHash = null; // Store for tracker request usage
            long fileLength = 0;
            long pieceLength = 0;
            int numPieces = 0;
            byte[] piecesHashes = null;

            if (torrentData.containsKey("info")) {
                Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
                
                if (info.containsKey("name")) {
                    Object nameObj = info.get("name");
                    String name = (nameObj instanceof byte[]) ? 
                        new String((byte[]) nameObj, StandardCharsets.UTF_8) : (String) nameObj;
                    System.out.println("File Name:   " + name);
                }
                
                if (info.containsKey("length")) {
                    fileLength = (long) info.get("length");
                    System.out.println("File Size:   " + fileLength + " bytes");
                }
                
                if (info.containsKey("piece length")) {
                    pieceLength = (long) info.get("piece length");
                    System.out.println("Piece Length: " + pieceLength + " bytes");
                }

                if (info.containsKey("pieces")) {
                    Object pObj = info.get("pieces");
                    if (pObj instanceof byte[]) {
                        piecesHashes = (byte[]) pObj;
                    } else if (pObj instanceof String) {
                        piecesHashes = ((String) pObj).getBytes(StandardCharsets.ISO_8859_1); // Bencode strings
                    }
                }

                // Calculate Total Pieces: ceil(Total Size / Piece Size)
                if (pieceLength > 0) {
                    numPieces = (int) Math.ceil((double) fileLength / pieceLength);
                    System.out.println("Total Pieces: " + numPieces);
                }

                // 5. Calculate Info Hash
                System.out.println("\n--- Cryptography ---");
                infoHash = calculateInfoHash(torrentData);
                String hexHash = bytesToHex(infoHash);
                System.out.println("Info Hash:   " + hexHash);
                // Sucessfully calculated Info Hash , verified from ubuntu website 
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
                    // Port 54321 is standard for BitTorrent
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
            
            // TCP Handshake & Message Loop
            if (peers != null && !peers.isEmpty()) {
                System.out.println("\n--- Starting Handshake Sequence ---");
                System.out.println("We have " + peers.size() + " candidates.");

                boolean completelyFinished = false;

                for (Peer targetPeer : peers) {
                    if (completelyFinished) {
                        break;
                    }

                    System.out.println("\nTrying Peer: " + targetPeer + "...");
                    
                    try (Socket socket = new Socket()) {
                        // 1. Connect (Timeout 10 seconds)
                        socket.connect(new InetSocketAddress(targetPeer.getIp(), targetPeer.getPort()), 10000);
                        System.out.println("  -> TCP Connection established!");

                        OutputStream out = socket.getOutputStream();
                        InputStream in = socket.getInputStream();

                        // 2. Send Handshake
                        byte[] handshakeMsg = Handshake.buildHandshake(infoHash, myPeerId);
                        out.write(handshakeMsg);
                        
                        // 3. Read Response
                        byte[] response = new byte[68];
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
            
                                // We use DataInputStream because it helps read 4-byte integers easily
                                DataInputStream dataIn = new DataInputStream(in);
                                
                                Bitfield peerBitfield = new Bitfield(numPieces);
                                
                                // --- STATE VARIABLES FOR DOWNLOADING ---
                                int targetPieceIndex = -1;
                                int downloadedBytes = 0;
                                byte[] pieceBuffer = new byte[(int) pieceLength]; // Holds the full assembled piece
                                boolean isRequesting = false; // Guardrail against duplicate UNCHOKEs
                                // -----------------------------------------------

                                // A. Send INTERESTED (ID = 2)
                                out.write(Message.build(2));
                                System.out.println("  -> Sent: INTERESTED");

                                System.out.println("Listening for messages...");
                                
                                while (true) {
                                    // 1. Read Length (4 bytes)
                                    int length = dataIn.readInt();
                                    
                                    if (length == 0) {
                                        // Keep-Alive message
                                        continue;
                                    }
                                    
                                    // 2. Read Message ID (1 byte)
                                    byte id = dataIn.readByte();
                                    
                                    // 3. Read Payload
                                    byte[] payload = new byte[length - 1];
                                    if (length - 1 > 0) {
                                        dataIn.readFully(payload);
                                    }
                                    
                                    // Mapping the pieces in the Bitfield
                                    if (id == 5) { // BITFIELD
                                        peerBitfield.overrideFromBytes(payload);
                                        System.out.println("     [Map Update] Received Bitfield. Peer has " + 
                                            peerBitfield.count() + "/" + numPieces + " pieces.");
                                    } 
                                    else if (id == 4) { // HAVE
                                        int pieceIndex = ByteBuffer.wrap(payload).getInt();
                                        peerBitfield.setPiece(pieceIndex);
                                        // Only print every 100th piece to avoid console spam
                                        if(pieceIndex % 100 == 0) System.out.println("     [Map Update] Peer just got Piece #" + pieceIndex);
                                    }
                                    else if (id == 0) {
                                        System.out.println("     [State] We are CHOKED. Cannot request data.");
                                    } 
                                    else if (id == 1) { // UNCHOKE
                                        System.out.println("     [State] We are UNCHOKED!");
                                        
                                        // If we haven't picked a piece yet, find one
                                        if (targetPieceIndex == -1) {
                                            for (int i = 0; i < numPieces; i++) {
                                                if (peerBitfield.hasPiece(i)) {
                                                    targetPieceIndex = i;
                                                    break; // Grab the first available piece
                                                }
                                            }
                                        }
                                        
                                        if (targetPieceIndex != -1 && !isRequesting) {
                                            System.out.println("     [Strategy] Starting download for Piece #" + targetPieceIndex + "...");
                                            
                                            // Request the FIRST block. Max block size is usually 16KB (16384 bytes)
                                            int blockSize = Math.min(16384, (int)pieceLength - downloadedBytes);
                                            byte[] requestMsg = Message.buildRequest(targetPieceIndex, downloadedBytes, blockSize);
                                            
                                            out.write(requestMsg);
                                            isRequesting = true;
                                            
                                        } else if (targetPieceIndex == -1) {
                                            System.out.println("     Warning: Peer has no pieces we can download.");
                                        }
                                    }
                                    else if (id == 7) { // PIECE
                                        // Payload format: [Index (4 bytes)] [Begin (4 bytes)] [Block Data (X bytes)]
                                        ByteBuffer blockBuffer = ByteBuffer.wrap(payload);
                                        
                                        int pIndex = blockBuffer.getInt();
                                        int pBegin = blockBuffer.getInt();
                                        
                                        // --- GUARDRAIL: Check for out-of-order blocks ---
                                        if (pIndex != targetPieceIndex || pBegin != downloadedBytes) {
                                            System.out.println("     [Warning] Ignored out-of-order block.");
                                            continue; 
                                        }
                                        
                                        // The rest is the actual file data
                                        byte[] data = new byte[payload.length - 8];
                                        blockBuffer.get(data);
                                        
                                        // Assemble the piece
                                        // Copy the incoming block into our large pieceBuffer
                                        System.arraycopy(data, 0, pieceBuffer, pBegin, data.length);
                                        downloadedBytes += data.length;
                                        
                                        // Print progress
                                        System.out.println(String.format("  -> Download Progress: %d / %d bytes (%.1f%%)", 
                                            downloadedBytes, pieceLength, ((double)downloadedBytes/pieceLength)*100));
                                            
                                        // Check if the piece is fully downloaded
                                        if (downloadedBytes < pieceLength) {
                                            // Request the NEXT block
                                            int nextBlockSize = Math.min(16384, (int)pieceLength - downloadedBytes);
                                            byte[] requestMsg = Message.buildRequest(targetPieceIndex, downloadedBytes, nextBlockSize);
                                            out.write(requestMsg);
                                        } else {
                                            // SUCCESS! The piece is completely assembled.
                                            System.out.println("\n  -> SUCCESS: Piece #" + targetPieceIndex + " completely downloaded!");
                                            
                                            // --- VERIFY THE PIECE HASH ---
                                            System.out.println("     [Verification] Calculating SHA-1 hash of downloaded data...");
                                            MessageDigest pieceDigest = MessageDigest.getInstance("SHA-1");
                                            byte[] calculatedHash = pieceDigest.digest(pieceBuffer);
                                            
                                            // Extract the expected 20-byte hash from the torrent metadata
                                            int hashOffset = targetPieceIndex * 20;
                                            byte[] expectedHash = Arrays.copyOfRange(piecesHashes, hashOffset, hashOffset + 20);
                                            
                                            if (Arrays.equals(calculatedHash, expectedHash)) {
                                                System.out.println("  -> [Verification] PASSED! Hash matches perfectly.");
                                                
                                                // Save to disk ONLY if it passes
                                                String fileName = "downloaded_piece_" + targetPieceIndex + ".dat";
                                                try (FileOutputStream fos = new FileOutputStream(fileName)) {
                                                    fos.write(pieceBuffer);
                                                }
                                                System.out.println("  -> Saved verified data to disk as: " + fileName);
                                                completelyFinished = true; // Mark process as totally complete
                                                break; // Break the message loop
                                                
                                            } else {
                                                System.err.println("  -> [Verification] FAILED! Hash mismatch. Malicious or corrupted data.");
                                                System.out.println("     Expected: " + bytesToHex(expectedHash));
                                                System.out.println("     Got:      " + bytesToHex(calculatedHash));
                                                System.out.println("     Dropping malicious peer and trying the next one...");
                                                break; // Break message loop, completelyFinished remains false to try next peer
                                            }
                                        }
                                    }
                                }
                                
                            } else {
                                System.err.println("  -> Error: Info Hash mismatch.");
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("  -> Connection failed: " + e.getMessage());
                    }
                }
                
                if (completelyFinished) {
                    System.out.println("\n*** BITTORRENT CLIENT SUCCESS ***");
                } else {
                    System.out.println("\n--- FAILURE: Ran out of peers or could not verify any data. ---");
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