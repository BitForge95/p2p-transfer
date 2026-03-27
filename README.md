# JTorrent: Java BitTorrent Client

A lightweight, ground-up implementation of a BitTorrent client written in Java.

This project was developed to demonstrate a deep understanding of low-level network programming, the BitTorrent Peer Wire Protocol, and binary data manipulation. Rather than relying on existing peer-to-peer libraries, JTorrent implements the core protocol specifications using standard Java Sockets and I/O streams.

---

## Core Architecture and Features

- **Custom Bencode Parser**  
  Decodes complex, nested dictionaries, lists, and byte strings from torrent metadata files.

- **Tracker Communication**  
  Calculates SHA-1 info hashes, generates client peer IDs, and communicates with HTTP trackers to decode compact binary peer lists.

- **Peer Wire Protocol Implementation**
  - Establishes raw TCP connections with peers across the internet  
  - Performs the strict 68-byte BitTorrent binary handshake  
  - Manages the protocol state machine (Choked, Unchoked, Interested)

- **Piece Mapping and Assembly**  
  Parses incoming BITFIELD and HAVE messages to map peer data availability. Breaks piece requests into standard 16KB blocks and reassembles them upon receipt.

- **Zero-Trust Cryptographic Verification**  
  Calculates the SHA-1 hash of downloaded pieces and verifies them against the official torrent signature before writing to disk, ensuring data integrity and dropping malicious peers.

---

## Technical Specifications

- **Language:** Java (JDK 17+)  
- **Dependencies:**  
  Zero external BitTorrent or networking libraries used. The architecture relies entirely on native:
  - `java.net`
  - `java.nio`
  - `java.io`
  - `java.security`

---

## Getting Started

### Prerequisites

- Java Development Kit (JDK) installed and configured in your system path  
- A valid HTTP-tracked torrent file in the root directory (e.g., `ubuntu.torrent` or `kali.torrent`)

---

### Compilation and Execution

#### 1. Clone the repository

```bash
git clone https://github.com/YourUsername/JTorrent.git
cd JTorrent
```

#### 2. Compile the source code

```bash
javac -d target/classes src/main/java/com/*.java
```

#### 3. Run the client

```bash
java -cp target/classes com.Main
```

---

## Project Roadmap

The current implementation successfully proves the concept by downloading, verifying, and saving a single target piece from the swarm. Future iterations will focus on scaling the architecture:

- **Main Download Loop**  
  Iterate through the bitfield to request and assemble all pieces into the final, complete file.

- **Concurrency**  
  Implement a thread pool to manage simultaneous connections with multiple peers to saturate download bandwidth.

- **Rarest-First Algorithm**  
  Prioritize downloading the rarest pieces in the swarm rather than sequential downloading.

- **UDP Tracker Support**  
  Extend the tracker client to support the UDP protocol alongside HTTP.

---
