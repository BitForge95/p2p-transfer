# JTorrent: Java BitTorrent Client

A lightweight, multi-threaded implementation of a BitTorrent client written entirely from scratch in Java.

This project was developed to demonstrate a deep understanding of low-level network programming, the BitTorrent Peer Wire Protocol, thread concurrency, and binary data manipulation. Rather than relying on existing peer-to-peer libraries, JTorrent implements the core protocol specifications using standard Java Sockets, I/O streams, and native concurrency utilities.

---

## Core Architecture and Features

- **Multi-threaded Swarm Downloading**  
  Utilizes Java `ExecutorService` to deploy a thread pool, allowing simultaneous TCP connections to multiple peers to maximize download bandwidth. Implements strict synchronized state locks to prevent race conditions and duplicate piece requests across threads.

- **Resume Capability**  
  On boot, the client scans the local directory for existing partial downloads, hashes the data, and verifies it against the torrent signature. Verified pieces are seamlessly integrated into the state manager, allowing downloads to pause and resume without data loss.

- **File Assembly and Cleanup (The Stitcher)**  
  Once all individual pieces are completely downloaded and verified, the engine automatically sequences and concatenates the binary chunks into the final target file and safely purges the temporary chunk files from the disk.

- **Zero-Trust Cryptographic Verification**  
  Calculates the SHA-1 hash of downloaded pieces and verifies them against the official torrent signature before writing to disk, ensuring data integrity and automatically dropping malicious or corrupted peers from the swarm.

- **Peer Wire Protocol Implementation**
  - Establishes raw TCP connections with peers across the internet  
  - Performs the strict 68-byte BitTorrent binary handshake  
  - Manages the protocol state machine (Choked, Unchoked, Interested)

- **Piece Mapping and Block Requests**  
  Parses incoming BITFIELD and HAVE messages to map peer data availability. Dynamically calculates block sizes and breaks piece requests into standard 16KB blocks, reassembling them upon receipt.

- **Custom Bencode Parser**  
  Decodes complex, nested dictionaries, lists, and byte strings from torrent metadata files.

- **Tracker Communication**  
  Calculates SHA-1 info hashes, generates client peer IDs, and communicates with HTTP trackers to decode compact binary peer lists.

---

## Technical Specifications

- **Language:** Java (JDK 17+)  
- **Dependencies:**  
  Zero external BitTorrent or networking libraries used. The architecture relies entirely on native packages:
  - `java.net`
  - `java.nio`
  - `java.io`
  - `java.security`
  - `java.util.concurrent`

---

## Getting Started

### Prerequisites

- Java Development Kit (JDK) installed and configured in your system path  
- A valid HTTP-tracked torrent file in the root directory (e.g., `ubuntu.torrent` or `kali.torrent`)

---

## Compilation and Execution

### 1. Clone the repository

```bash
git clone https://github.com/YourUsername/JTorrent.git
cd JTorrent
```

### 2. Compile the source code

```bash
javac -d target/classes src/main/java/com/*.java
```

### 3. Run the client

```bash
java -cp target/classes com.Main
```

---

## Project Roadmap

The current implementation successfully connects to tracker swarms, manages multi-threaded peer connections, and fully downloads and assembles target files. Future iterations will focus on advanced protocol extensions:

- **Rarest-First Algorithm**  
  Prioritize downloading the rarest pieces in the swarm globally rather than sequential downloading to improve swarm health and download speed.

- **Seeding and Tit-for-Tat Algorithm**  
  Implement the upload pipeline to respond to incoming requests, rewarding fast uploaders and choking slow peers based on the standard BitTorrent economic model.

- **UDP Tracker Support**  
  Extend the tracker client to support the lightweight UDP protocol alongside HTTP.

- **DHT (Distributed Hash Table)**  
  Implement Kademlia routing to allow trackerless peer discovery.

---