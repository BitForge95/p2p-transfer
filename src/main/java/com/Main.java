package com;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.File;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    static boolean[] completedPieces;
    static boolean[] requestedPieces;
    static int totalCompletedPieces = 0;
    static int numPieces = 0;
    static final Object STATE_LOCK = new Object();

    public static void main(String[] args) {
        try {
            System.out.println("--- JTorrent: BitTorrent Client v1.0 (Multi-threaded) ---");

            if (args.length == 0) {
                System.err.println("Error: No torrent file specified.");
                System.out.println("Usage: java -jar JTorrent.jar <path_to_torrent_file>");
                return;
            }

            String filePath = args[0]; 
            
            if (!Files.exists(Paths.get(filePath))) {
                System.err.println("Error: File not found: " + filePath);
                return;
            }
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath));
            
            BencodeParser parser = new BencodeParser(fileBytes);
            Map<String, Object> torrentData = (Map<String, Object>) parser.decode();

            String announceUrl = null;
            if (torrentData.containsKey("announce")) {
                Object announceObj = torrentData.get("announce");
                announceUrl = (announceObj instanceof byte[]) ? 
                    new String((byte[]) announceObj, StandardCharsets.UTF_8) : (String) announceObj;
            }

            byte[] infoHash = null; 
            long fileLength = 0;
            long pieceLength = 0;
            byte[] piecesHashes = null;
            String suggestedName = "downloaded_file.iso";

            if (torrentData.containsKey("info")) {
                Map<String, Object> info = (Map<String, Object>) torrentData.get("info");
                
                if (info.containsKey("name")) {
                    Object nameObj = info.get("name");
                    suggestedName = (nameObj instanceof byte[]) ? 
                        new String((byte[]) nameObj, StandardCharsets.UTF_8) : (String) nameObj;
                }
                if (info.containsKey("length")) {
                    fileLength = (long) info.get("length");
                }
                if (info.containsKey("piece length")) {
                    pieceLength = (long) info.get("piece length");
                }
                if (info.containsKey("pieces")) {
                    Object pObj = info.get("pieces");
                    piecesHashes = (pObj instanceof byte[]) ? (byte[]) pObj : ((String) pObj).getBytes(StandardCharsets.ISO_8859_1); 
                }

                if (pieceLength > 0) {
                    numPieces = (int) Math.ceil((double) fileLength / pieceLength);
                }
                infoHash = calculateInfoHash(torrentData);
            }

            System.out.println("Target File: " + suggestedName);
            System.out.println("Total Pieces: " + numPieces);

            completedPieces = new boolean[numPieces];
            requestedPieces = new boolean[numPieces];

            byte[] myPeerId = generatePeerId();

            List<Peer> peers = null;
            if (announceUrl != null && infoHash != null && announceUrl.startsWith("http")) {
                System.out.println("\nConnecting to Tracker...");
                TrackerClient trackerClient = new TrackerClient();
                byte[] response = trackerClient.requestPeers(announceUrl, infoHash, myPeerId, 54321);
                peers = trackerClient.parseResponse(response);
                System.out.println("Found " + peers.size() + " peers.");
            } else {
                System.err.println("Failed to get peers or unsupported tracker.");
                return;
            }

            if (peers == null || peers.isEmpty()) {
                System.out.println("No peers available to connect to.");
                return;
            }

            int threadCount = Math.min(20, peers.size());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            System.out.println("\n--- Initiating Swarm Connection (" + threadCount + " threads) ---");

            for (Peer targetPeer : peers) {
                executor.submit(new PeerWorker(targetPeer, infoHash, myPeerId, pieceLength, fileLength, piecesHashes));
            }

            executor.shutdown();

            while (totalCompletedPieces < numPieces) {
                Thread.sleep(3000);
                
                synchronized (STATE_LOCK) {
                    System.out.println(String.format("[MAIN] Global Progress: %d / %d pieces (%.1f%%)", 
                        totalCompletedPieces, numPieces, ((double)totalCompletedPieces/numPieces)*100));
                }

                if (executor.isTerminated() && totalCompletedPieces < numPieces) {
                    System.err.println("[MAIN] CRITICAL: All peer threads died, but download is incomplete.");
                    break;
                }
            }

            if (totalCompletedPieces == numPieces) {
                System.out.println("\n*** ALL PIECES DOWNLOADED ***");
                System.out.println("Starting the Stitcher...");
                stitchFiles(numPieces, suggestedName);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static class PeerWorker implements Runnable {
        Peer peer;
        byte[] infoHash;
        byte[] myPeerId;
        long pieceLength;
        long fileLength;
        byte[] piecesHashes;

        public PeerWorker(Peer peer, byte[] infoHash, byte[] myPeerId, long pieceLength, long fileLength, byte[] piecesHashes) {
            this.peer = peer;
            this.infoHash = infoHash;
            this.myPeerId = myPeerId;
            this.pieceLength = pieceLength;
            this.fileLength = fileLength;
            this.piecesHashes = piecesHashes;
        }

        @Override
        public void run() {
            int targetPieceIndex = -1;
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peer.getIp(), peer.getPort()), 10000);
                
                OutputStream out = socket.getOutputStream();
                InputStream in = socket.getInputStream();

                out.write(Handshake.buildHandshake(infoHash, myPeerId));
                
                byte[] response = new byte[68];
                int bytesRead = 0;
                while(bytesRead < 68) {
                    int count = in.read(response, bytesRead, 68 - bytesRead);
                    if(count == -1) break; 
                    bytesRead += count;
                }

                if (bytesRead < 68 || !Handshake.verify(response, infoHash)) {
                    return; 
                }

                DataInputStream dataIn = new DataInputStream(in);
                Bitfield peerBitfield = new Bitfield(numPieces);
                
                int downloadedBytes = 0;
                byte[] pieceBuffer = null; 
                int actualPieceLength = 0;
                boolean isRequesting = false; 

                out.write(Message.build(2)); 

                while (true) {
                    synchronized (STATE_LOCK) {
                        if (totalCompletedPieces >= numPieces) break;
                    }

                    int length = dataIn.readInt();
                    if (length == 0) continue;
                    
                    byte id = dataIn.readByte();
                    byte[] payload = new byte[length - 1];
                    if (length - 1 > 0) dataIn.readFully(payload);
                    
                    if (id == 5) { 
                        peerBitfield.overrideFromBytes(payload);
                    } 
                    else if (id == 4) { 
                        peerBitfield.setPiece(ByteBuffer.wrap(payload).getInt());
                    }
                    else if (id == 1) {
                        targetPieceIndex = -1;
                        
                        synchronized (STATE_LOCK) {
                            for (int i = 0; i < numPieces; i++) {
                                if (!completedPieces[i] && !requestedPieces[i] && peerBitfield.hasPiece(i)) {
                                    targetPieceIndex = i;
                                    requestedPieces[i] = true; 
                                    break; 
                                }
                            }
                        }
                        
                        if (targetPieceIndex != -1) {
                            actualPieceLength = (targetPieceIndex == numPieces - 1 && fileLength % pieceLength != 0) ? 
                                                (int) (fileLength % pieceLength) : (int) pieceLength;
                            pieceBuffer = new byte[actualPieceLength];
                            downloadedBytes = 0;
                            
                            int blockSize = Math.min(16384, actualPieceLength - downloadedBytes);
                            out.write(Message.buildRequest(targetPieceIndex, downloadedBytes, blockSize));
                            isRequesting = true;
                        }
                    }
                    else if (id == 7 && isRequesting) {
                        ByteBuffer blockBuffer = ByteBuffer.wrap(payload);
                        int pIndex = blockBuffer.getInt();
                        int pBegin = blockBuffer.getInt();
                        
                        if (pIndex != targetPieceIndex || pBegin != downloadedBytes) continue;
                        
                        byte[] data = new byte[payload.length - 8];
                        blockBuffer.get(data);
                        
                        System.arraycopy(data, 0, pieceBuffer, pBegin, data.length);
                        downloadedBytes += data.length;
                        
                        if (downloadedBytes < actualPieceLength) {
                            int nextBlockSize = Math.min(16384, actualPieceLength - downloadedBytes);
                            out.write(Message.buildRequest(targetPieceIndex, downloadedBytes, nextBlockSize));
                        } else {
                            MessageDigest pieceDigest = MessageDigest.getInstance("SHA-1");
                            byte[] calculatedHash = pieceDigest.digest(pieceBuffer);
                            int hashOffset = targetPieceIndex * 20;
                            byte[] expectedHash = Arrays.copyOfRange(piecesHashes, hashOffset, hashOffset + 20);
                            
                            if (Arrays.equals(calculatedHash, expectedHash)) {
                                String fileName = "downloaded_piece_" + targetPieceIndex + ".dat";
                                try (FileOutputStream fos = new FileOutputStream(fileName)) {
                                    fos.write(pieceBuffer);
                                }
                                
                                synchronized (STATE_LOCK) {
                                    completedPieces[targetPieceIndex] = true;
                                    totalCompletedPieces++;
                                }
                            } else {
                                synchronized (STATE_LOCK) {
                                    requestedPieces[targetPieceIndex] = false;
                                }
                                break; 
                            }
                            
                            isRequesting = false;
                            targetPieceIndex = -1;
                            
                            synchronized (STATE_LOCK) {
                                for (int i = 0; i < numPieces; i++) {
                                    if (!completedPieces[i] && !requestedPieces[i] && peerBitfield.hasPiece(i)) {
                                        targetPieceIndex = i;
                                        requestedPieces[i] = true;
                                        break; 
                                    }
                                }
                            }

                            if (targetPieceIndex != -1) {
                                actualPieceLength = (targetPieceIndex == numPieces - 1 && fileLength % pieceLength != 0) ? 
                                                    (int) (fileLength % pieceLength) : (int) pieceLength;
                                pieceBuffer = new byte[actualPieceLength];
                                downloadedBytes = 0;
                                
                                int blockSize = Math.min(16384, actualPieceLength - downloadedBytes);
                                out.write(Message.buildRequest(targetPieceIndex, downloadedBytes, blockSize));
                                isRequesting = true;
                            } else {
                                break;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (targetPieceIndex != -1) {
                    synchronized (STATE_LOCK) {
                        if (!completedPieces[targetPieceIndex]) {
                            requestedPieces[targetPieceIndex] = false;
                        }
                    }
                }
            }
        }
    }

    private static void stitchFiles(int totalPieces, String outputFileName) {
        System.out.println("Assembling " + totalPieces + " pieces into " + outputFileName + "...");
        try (FileOutputStream fos = new FileOutputStream(outputFileName)) {
            
            for (int i = 0; i < totalPieces; i++) {
                File chunkFile = new File("downloaded_piece_" + i + ".dat");
                if (chunkFile.exists()) {
                    try (FileInputStream fis = new FileInputStream(chunkFile)) {
                        byte[] buffer = new byte[1024 * 1024];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    chunkFile.delete(); 
                } else {
                    System.err.println("CRITICAL ERROR: Missing piece " + i);
                    return;
                }
            }
            System.out.println("\n*** ASSEMBLY COMPLETE! ***");
            System.out.println("File saved successfully as: " + outputFileName);
            
        } catch (Exception e) {
            System.err.println("Error assembling files: " + e.getMessage());
        }
    }

    private static byte[] calculateInfoHash(Map<String, Object> torrentData) throws Exception {
        Map<String, Object> infoMap = (Map<String, Object>) torrentData.get("info");
        BencodeEncoder encoder = new BencodeEncoder();
        byte[] infoBytes = encoder.encode(infoMap);
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(infoBytes);
    }
      
    private static byte[] generatePeerId() {
        byte[] peerId = new byte[20];
        byte[] prefix = "-JT1000-".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(prefix, 0, peerId, 0, prefix.length);
        Random random = new Random();
        for (int i = prefix.length; i < 20; i++) {
            peerId[i] = (byte) (random.nextInt(10) + '0'); 
        }
        return peerId;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}