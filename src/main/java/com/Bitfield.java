package com;

public class Bitfield {
    private final boolean[] pieces;

    public Bitfield(int numPieces) {
        this.pieces = new boolean[numPieces];
    }

    // Updates local piece availability from a full bitfield payload.
    public void overrideFromBytes(byte[] payload) {
        for (int i = 0; i < payload.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                int pieceIndex = (i * 8) + bit;
                
                if (pieceIndex < pieces.length) {
                    if ((payload[i] & (0x80 >> bit)) != 0) {
                        pieces[pieceIndex] = true;
                    } else {
                        pieces[pieceIndex] = false;
                    }
                }
            }
        }
    }

    // Marks one piece as available from a HAVE message.
    public void setPiece(int index) {
        if (index >= 0 && index < pieces.length) {
            pieces[index] = true;
        }
    }

    // Returns true if the peer has this piece index.
    public boolean hasPiece(int index) {
        return index >= 0 && index < pieces.length && pieces[index];
    }

    // Counts how many pieces are marked as available.
    public int count() {
        int count = 0;
        for (boolean b : pieces) {
            count += (b ? 1 : 0);
        }
        return count;
    }
}