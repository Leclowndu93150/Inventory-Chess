package com.leclowndu93150.guichess.engine;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

public class StockfishInstaller {
    private static final String WINDOWS_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-windows-x86-64-avx2.zip";
    private static final String LINUX_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-ubuntu-x86-64-avx2.tar";
    private static final String MAC_URL = "https://github.com/official-stockfish/Stockfish/releases/latest/download/stockfish-macos-x86-64-avx2.tar";
    
    private final Path installDir;
    private final Path stockfishExecutable;
    
    public StockfishInstaller(Path dataDir) {
        this.installDir = dataDir.resolve("stockfish");
        this.stockfishExecutable = installDir.resolve(getExecutableName());
    }
    
    private String getExecutableName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return "stockfish.exe";
        } else {
            return "stockfish";
        }
    }
    
    public boolean isInstalled() {
        return Files.exists(stockfishExecutable) && Files.isExecutable(stockfishExecutable);
    }
    
    public Path getExecutablePath() {
        return stockfishExecutable;
    }
    
    public CompletableFuture<Void> installIfNeededAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                if (isInstalled()) {
                    System.out.println("[GUIChess] Stockfish already installed at: " + stockfishExecutable);
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
                    // Download
                    downloadFile(downloadUrl, tempFile);
                    
                    // Extract
                    if (isZip) {
                        extractZip(tempFile);
                    } else {
                        extractTar(tempFile);
                    }
                    
                    // Make executable on Unix-like systems
                    if (!os.contains("win")) {
                        makeExecutable();
                    }
                    
                    System.out.println("[GUIChess] Stockfish installed successfully at: " + stockfishExecutable);
                    
                } finally {
                    // Clean up temp file
                    Files.deleteIfExists(tempFile);
                }
            } catch (IOException e) {
                throw new CompletionException("Failed to install Stockfish", e);
            }
        });
    }
    
    private void downloadFile(String urlString, Path destination) throws IOException {
        System.out.println("[GUIChess] Downloading from: " + urlString);
        
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        
        // GitHub redirects to CDN, so we need to follow redirects
        int status = connection.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_SEE_OTHER) {
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
                    if (newProgress != progress && newProgress % 10 == 0) {
                        progress = newProgress;
                        System.out.println("[GUIChess] Download progress: " + progress + "%");
                    }
                }
            }
        }
        
        System.out.println("[GUIChess] Download complete");
    }
    
    private void extractZip(Path zipFile) throws IOException {
        System.out.println("[GUIChess] Extracting ZIP...");
        
        boolean found = false;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                System.out.println("[GUIChess] ZIP entry: " + name);
                
                // Look for the Windows executable: stockfish/stockfish-windows-x86-64-avx2.exe
                if (!entry.isDirectory() && 
                    (name.equals("stockfish/stockfish-windows-x86-64-avx2.exe") || 
                     name.endsWith("stockfish-windows-x86-64-avx2.exe") ||
                     (name.toLowerCase().contains("stockfish") && name.toLowerCase().endsWith(".exe")))) {
                    
                    Path target = stockfishExecutable;
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[GUIChess] Extracted: " + name + " -> " + target);
                    System.out.println("[GUIChess] File exists after extraction: " + Files.exists(target));
                    System.out.println("[GUIChess] File size: " + Files.size(target) + " bytes");
                    found = true;
                    break;
                }
            }
        }
        
        if (!found) {
            System.err.println("[GUIChess] No stockfish executable found in ZIP archive");
            // List all entries for debugging
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
                ZipEntry entry;
                System.out.println("[GUIChess] All ZIP entries:");
                while ((entry = zis.getNextEntry()) != null) {
                    System.out.println("  " + entry.getName());
                }
            }
            throw new IOException("No stockfish executable found in ZIP archive");
        }
    }
    
    private void extractTar(Path tarFile) throws IOException {
        System.out.println("[GUIChess] Extracting TAR...");
        
        boolean found = false;
        try (InputStream fileIn = Files.newInputStream(tarFile);
             BufferedInputStream buffIn = new BufferedInputStream(fileIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(buffIn)) {
            
            TarArchiveEntry entry;
            while ((entry = tarIn.getNextTarEntry()) != null) {
                String name = entry.getName();
                System.out.println("[GUIChess] TAR entry: " + name);
                
                // Look for stockfish executables (Ubuntu/Mac variants)
                if (!entry.isDirectory() && 
                    (name.contains("stockfish-ubuntu") || name.contains("stockfish-macos") || 
                     (name.contains("stockfish") && name.endsWith("stockfish")))) {
                    
                    Path target = stockfishExecutable;
                    Files.copy(tarIn, target, StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[GUIChess] Extracted: " + name + " -> " + target);
                    System.out.println("[GUIChess] File exists after extraction: " + Files.exists(target));
                    System.out.println("[GUIChess] File size: " + Files.size(target) + " bytes");
                    found = true;
                    break;
                }
            }
        }
        
        if (!found) {
            System.err.println("[GUIChess] No stockfish executable found in TAR archive");
            // List all entries for debugging
            try (InputStream fileIn = Files.newInputStream(tarFile);
                 BufferedInputStream buffIn = new BufferedInputStream(fileIn);
                 TarArchiveInputStream tarIn = new TarArchiveInputStream(buffIn)) {
                
                TarArchiveEntry entry;
                System.out.println("[GUIChess] All TAR entries:");
                while ((entry = tarIn.getNextTarEntry()) != null) {
                    System.out.println("  " + entry.getName());
                }
            }
            throw new IOException("No stockfish executable found in TAR archive");
        }
    }
    
    private void makeExecutable() throws IOException {
        try {
            // Use ProcessBuilder to run chmod
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
            // Fallback to Java NIO
            System.out.println("[GUIChess] chmod failed, trying Java NIO method");
            
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