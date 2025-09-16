package com.EcoChartPro.ui.dashboard.utils;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class ImageProvider {

    private static final OkHttpClient client = new OkHttpClient();
    private static final Logger logger = LoggerFactory.getLogger(ImageProvider.class);
    private static final Map<String, Image> imageCache = new ConcurrentHashMap<>();
    private static final Path PICTURES_DIRECTORY = Paths.get(System.getProperty("user.home"), "Pictures");

    // A list to hold discovered local image paths ---
    private static final List<Path> localImagePaths = new ArrayList<>();

    // A static block to scan for local images at startup ---
    static {
        initializeLocalImages();
    }

    /**
     * Scans the user's "Pictures" directory for images on a background thread.
     */
    private static void initializeLocalImages() {
        new Thread(() -> {
            if (!Files.isDirectory(PICTURES_DIRECTORY)) {
                logger.warn("Local pictures directory not found at: {}", PICTURES_DIRECTORY);
                return;
            }
            try (Stream<Path> stream = Files.list(PICTURES_DIRECTORY)) {
                stream.filter(path -> {
                    String fileName = path.toString().toLowerCase();
                    return fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".png");
                }).forEach(localImagePaths::add);
                logger.info("Found {} local images in Pictures directory.", localImagePaths.size());
            } catch (IOException e) {
                logger.error("Error scanning local pictures directory.", e);
            }
        }).start();
    }

    /**
     * Fetches an image from a given resource identifier (URL or local file path).
     * Uses a cache to avoid re-downloading or re-reading from disk.
     *
     * @param resourceIdentifier The URL string or absolute path string of the image.
     * @param onComplete         The callback to execute with the loaded image.
     */
    public static void fetchImage(String resourceIdentifier, Consumer<Image> onComplete) {
        if (resourceIdentifier == null) return;

        if (imageCache.containsKey(resourceIdentifier)) {
            onComplete.accept(imageCache.get(resourceIdentifier));
            return;
        }

        if (resourceIdentifier.startsWith("http")) {
            fetchRemoteImage(resourceIdentifier, onComplete);
        } else {
            fetchLocalImage(resourceIdentifier, onComplete);
        }
    }
    
    /**
     * Returns a path to a local image based on an index. Cycles through available images.
     *
     * @param index The desired index.
     * @return An Optional containing the Path, or empty if no local images are available.
     */
    public static Optional<Path> getLocalImage(int index) {
        if (localImagePaths.isEmpty()) {
            return Optional.empty();
        }
        // Use modulo to cycle through images if index is out of bounds
        return Optional.of(localImagePaths.get(index % localImagePaths.size()));
    }
    
    private static void fetchLocalImage(String pathString, Consumer<Image> onComplete) {
        new Thread(() -> {
            try {
                Image image = ImageIO.read(new File(pathString));
                if (image != null) {
                    imageCache.put(pathString, image);
                    SwingUtilities.invokeLater(() -> onComplete.accept(image));
                }
            } catch (IOException e) {
                logger.error("Failed to load local image: {}", pathString, e);
            }
        }).start();
    }
    
    private static void fetchRemoteImage(String url, Consumer<Image> onComplete) {
        new Thread(() -> {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("Failed to download image from {}: {}", url, response.code());
                    return;
                }
                try (InputStream inputStream = response.body().byteStream()) {
                    Image image = ImageIO.read(inputStream);
                    if (image != null) {
                        imageCache.put(url, image);
                        SwingUtilities.invokeLater(() -> onComplete.accept(image));
                    }
                }
            } catch (Exception e) {
                logger.error("Exception while fetching image from " + url, e);
            }
        }).start();
    }
}