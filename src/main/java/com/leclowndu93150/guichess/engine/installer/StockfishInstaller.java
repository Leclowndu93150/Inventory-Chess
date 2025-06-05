package com.leclowndu93150.guichess.engine.installer;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

/**
 * Handles automatic installation of Stockfish chess engine from official releases.
 * Downloads platform-specific binaries and extracts them to the data directory.
 */
public class StockfishInstaller {
    private static final String WINDOWS_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-windows-x86-64-avx2.zip";
    private static final String LINUX_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-ubuntu-x86-64-avx2.tar";
    private static final String MAC_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-macos-x86-64-avx2.tar";

    private final Path installDir;
    private final Path stockfishExecutable;

    /**
     * Creates a new installer instance for the specified data directory.
     * 
     * @param dataDir The base directory where Stockfish will be installed
     */
    public StockfishInstaller(Path dataDir) {
        this.installDir = dataDir.resolve("stockfish");
        this.stockfishExecutable = installDir.resolve(getExecutableName());
    }

    private String getExecutableName() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win") ? "stockfish.exe" : "stockfish";
    }

    /**
     * Checks if Stockfish is already installed and executable.
     * 
     * @return true if Stockfish is installed and ready to use
     */
    public boolean isInstalled() {
        return Files.exists(stockfishExecutable) && Files.isExecutable(stockfishExecutable);
    }

    /**
     * Gets the path to the Stockfish executable.
     * 
     * @return Path to the executable file
     */
    public Path getExecutablePath() {
        return stockfishExecutable;
    }

    /**
     * Asynchronously installs Stockfish if not already present.
     * Downloads the appropriate platform binary and extracts it.
     * 
     * @return CompletableFuture that completes when installation is done
     */
    public CompletableFuture<Void> installIfNeededAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (isInstalled()) {
                    System.out.println("[GUIChess] Stockfish already installed");
                    return;
                }

                System.out.println("[GUIChess] Installing Stockfish...");
                Files.createDirectories(installDir);

                String os = System.getProperty("os.name").toLowerCase();
                String downloadUrl;
                boolean isZip;

                if (os.contains("win")) {
                    downloadUrl = WINDOWS_URL;
                    isZip = true;
                } else if (os.contains("mac")) {
                    downloadUrl = MAC_URL;
                    isZip = false;
                } else {
                    downloadUrl = LINUX_URL;
                    isZip = false;
                }

                Path tempFile = installDir.resolve(isZip ? "stockfish.zip" : "stockfish.tar");

                try {
                    downloadFile(downloadUrl, tempFile);

                    if (isZip) {
                        extractZip(tempFile);
                    } else {
                        extractTar(tempFile);
                    }

                    if (!os.contains("win")) {
                        makeExecutable();
                    }

                    System.out.println("[GUIChess] Stockfish installed successfully");

                } finally {
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to install Stockfish", e);
            }
        });
    }

    private void downloadFile(String urlString, Path destination) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);

        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
            status == HttpURLConnection.HTTP_MOVED_PERM || 
            status == HttpURLConnection.HTTP_SEE_OTHER) {
            String newUrl = connection.getHeaderField("Location");
            connection = (HttpURLConnection) new URL(newUrl).openConnection();
        }

        long fileSize = connection.getContentLengthLong();
        long downloadedSize = 0;

        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {

            byte[] buffer = new byte[8192];
            int bytesRead;
            int progress = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                downloadedSize += bytesRead;

                if (fileSize > 0) {
                    int newProgress = (int) ((downloadedSize * 100) / fileSize);
                    if (newProgress != progress && newProgress % 25 == 0) {
                        progress = newProgress;
                        System.out.println("[GUIChess] Download progress: " + progress + "%");
                    }
                }
            }
        }
    }

    private void extractZip(Path zipFile) throws IOException {
        boolean found = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                if (!entry.isDirectory() &&
                    (name.equals("stockfish/stockfish-windows-x86-64-avx2.exe") ||
                     name.endsWith("stockfish-windows-x86-64-avx2.exe") ||
                     (name.toLowerCase().contains("stockfish") && name.toLowerCase().endsWith(".exe")))) {

                    Files.copy(zis, stockfishExecutable, StandardCopyOption.REPLACE_EXISTING);
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            throw new IOException("No stockfish executable found in ZIP archive");
        }
    }

    private void extractTar(Path tarFile) throws IOException {
        boolean found = false;
        try (InputStream fileIn = Files.newInputStream(tarFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(buffIn)) {

            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                String name = entry.getName();

                if (!entry.isDirectory() &&
                    (name.contains("stockfish-ubuntu") || name.contains("stockfish-macos") ||
                     (name.contains("stockfish") && name.endsWith("stockfish")))) {

                    Files.copy(tarIn, stockfishExecutable, StandardCopyOption.REPLACE_EXISTING);
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            throw new IOException("No stockfish executable found in TAR archive");
        }
    }

    private void makeExecutable() throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder("chmod", "+x", stockfishExecutable.toString());
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("[GUIChess] Warning: chmod failed with exit code " + exitCode);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while making file executable", e);
        } catch (IOException e) {
            try {
                var perms = Files.getPosixFilePermissions(stockfishExecutable);
                perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
                perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE);
                perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(stockfishExecutable, perms);
            } catch (UnsupportedOperationException uoe) {
                System.err.println("[GUIChess] Warning: Cannot set executable permissions on this system");
            }
        }
    }
}