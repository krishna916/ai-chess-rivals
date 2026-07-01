# AI Chess Rivals — Server

Spring Boot backend for the AI Chess Rivals application.

---

## Stockfish Engine Setup

### Why Stockfish binaries are not in the repository

Binary executables change between OS/CPU variants, grow large quickly, and pollute
`git log`. Committing them would force every developer to download a binary they may
never use. Instead, the correct binary is **downloaded on demand** during the Maven
`generate-resources` phase via the `download-maven-plugin`.

---

### Downloading the executable

Run the appropriate Maven profile from the `server/` directory.

**Windows**

```bash
mvn package -Pwindows
```

Downloads `stockfish-windows-x86-64-avx2.zip` from the official Stockfish GitHub
release, extracts it, and renames the executable to:

```
server/stockfish/stockfish.exe
```

**Linux (Render / Railway / Ubuntu)**

```bash
mvn package -Plinux
```

Downloads `stockfish-ubuntu-x86-64-avx2.tar` from the official Stockfish GitHub
release, extracts it, renames the binary to `stockfish`, and sets the executable
bit (`chmod 755`):

```
server/stockfish/stockfish
```

> **Source**: All downloads come from the official Stockfish GitHub repository:
> `https://github.com/official-stockfish/Stockfish/releases`
> No third-party mirrors are used.

---

### Running the application

The application reads the Stockfish path from the `STOCKFISH_PATH` environment
variable (or falls back to `stockfish/stockfish`).

**Windows**

```bash
set STOCKFISH_PATH=stockfish/stockfish.exe
mvn spring-boot:run
```

Or in `application.yaml` (already pre-configured):

```yaml
app:
  chess:
    stockfish:
      path: ${STOCKFISH_PATH:stockfish/stockfish}
```

Set `STOCKFISH_PATH=stockfish/stockfish.exe` in your IDE's run configuration or
your deployment environment variables.

**Linux**

```bash
STOCKFISH_PATH=stockfish/stockfish mvn spring-boot:run
```

The application contains **no OS detection** — it simply executes whatever path
is configured.

---

### Upgrading Stockfish

1. Open `server/pom.xml`.
2. Find the `<stockfish.version>` property:

   ```xml
   <stockfish.version>17.1</stockfish.version>
   ```

3. Change it to the new version tag (e.g. `18`). The tag format is `sf_<version>`.
   Verify the tag exists at:
   `https://github.com/official-stockfish/Stockfish/releases`

4. Re-run the download profile:

   ```bash
   # Windows
   mvn generate-resources -Pwindows

   # Linux
   mvn generate-resources -Plinux
   ```

No other files need to change.

---

### Troubleshooting

#### `IllegalStateException: Stockfish executable not found at: …`

The binary has not been downloaded yet. Run:

```bash
# Windows
mvn generate-resources -Pwindows

# Linux
mvn generate-resources -Plinux
```

Check that `server/stockfish/stockfish.exe` (Windows) or `server/stockfish/stockfish`
(Linux) exists after the download.

#### `Stockfish file exists but is not executable`

On Linux, the `chmod` step in the Maven profile should handle this automatically.
If you are seeing this error, run manually:

```bash
chmod +x server/stockfish/stockfish
```

#### Download fails on CI/CD (Render / Railway)

Add the profile to your build command in the platform's build settings:

```bash
mvn package -Plinux
```

Set the environment variable in the platform dashboard:

```
STOCKFISH_PATH=stockfish/stockfish
```

#### Wrong binary for the platform

Do not mix profiles. If you previously ran `-Pwindows` and are now on Linux,
delete the contents of `server/stockfish/` (except `.gitkeep`) and re-run
`mvn generate-resources -Plinux`.
