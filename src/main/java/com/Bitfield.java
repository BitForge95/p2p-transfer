package com;

// Source : https://wiki.theory.org/BitTorrentSpecification#bitfield:_.3Clen.3D0001.2BX.3E.3Cid.3D5.3E.3Cbitfield.3E
// Author : Charan Sai
// Date : 06/02/2026

// Why we used BitField 
// Each of the piece is represented by one bit and is stored in the boolean Array

public class Bitfield {
    private final boolean[] pieces;

    public Bitfield(int numPieces) {
        this.pieces = new boolean[numPieces];
        // By default set all the values in the Array to False
    }

    // Handles the full BITFIELD message (ID=5)
    public void overrideFromBytes(byte[] payload) {
        for (int i = 0; i < payload.length; i++) {
            // Check each of the 8 bits in this byte
            for (int bit = 0; bit < 8; bit++) {
                int pieceIndex = (i * 8) + bit;
                
                // Stop if we go past the total number of pieces
                if (pieceIndex < pieces.length) {
                    // Check if the specific bit is 1
                    // 0x80 is 10000000. We shift it right to check bit 0, 1, 2...
                    if ((payload[i] & (0x80 >> bit)) != 0) { // We have masked the bits 
                        pieces[pieceIndex] = true; // If the piece exists then mark it as True to signif the piece 
                    } else {
                        pieces[pieceIndex] = false;
                    }
                }
            }
        }
    }

    // Handles the single HAVE message (ID=4)
    public void setPiece(int index) {
        if (index >= 0 && index < pieces.length) {
            pieces[index] = true;
        }
    }

    // Checks if the peer possesses a specific piece so we can decide to request it
    public boolean hasPiece(int index) {
        return index >= 0 && index < pieces.length && pieces[index];
    }

    // How many pieces does this peer have?
    public int count() {
        int count = 0;
        for (boolean b : pieces) {
            count += (b ? 1 : 0);
        }
        return count;
    }
}