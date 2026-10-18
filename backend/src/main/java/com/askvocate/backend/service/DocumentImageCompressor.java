package com.askvocate.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** Compresses oversized document photos before OCR or cloud storage. */
public final class DocumentImageCompressor {
    private static final Logger log = LoggerFactory.getLogger(DocumentImageCompressor.class);
    public static final long TARGET_BYTES = 9L * 1024 * 1024;
    public static final long MAX_INPUT_BYTES = 30L * 1024 * 1024;

    private DocumentImageCompressor() {}

    public static MultipartFile compressIfNeeded(MultipartFile source) throws IOException {
        if (source == null || source.isEmpty()) throw new IOException("Image is empty");
        if (source.getSize() > MAX_INPUT_BYTES) throw new IOException("Image exceeds the 30 MB upload limit");
        if (source.getSize() <= TARGET_BYTES) return source;

        BufferedImage decoded = ImageIO.read(source.getInputStream());
        if (decoded == null) throw new IOException("This image format cannot be compressed on the server. Please use JPG or PNG.");

        int width = decoded.getWidth();
        int height = decoded.getHeight();
        for (int attempt = 0; attempt < 16; attempt++) {
            BufferedImage image = resize(decoded, width, height);
            for (float quality : new float[]{0.88f, 0.80f, 0.72f, 0.64f, 0.56f}) {
                byte[] jpeg = encodeJpeg(image, quality);
                if (jpeg.length <= TARGET_BYTES) {
                    String filename = source.getOriginalFilename();
                    if (filename == null || filename.isBlank()) filename = "document.jpg";
                    else filename = filename.replaceAll("(?i)\\.(png|jpe?g|webp)$", "") + ".jpg";
                    log.info("Compressed document image: inputBytes={}, outputBytes={}, dimensions={}x{}",
                            source.getSize(), jpeg.length, width, height);
                    return new CompressedMultipartFile(filename, jpeg);
                }
            }
            width = Math.max(1, (int) (width * 0.82));
            height = Math.max(1, (int) (height * 0.82));
        }
        throw new IOException("Unable to compress image below 9 MB");
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG encoder is unavailable");
        var writer = writers.next();
        try (var output = new ByteArrayOutputStream(); var imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            var params = writer.getDefaultWriteParam();
            params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private static final class CompressedMultipartFile implements MultipartFile {
        private final String filename;
        private final byte[] bytes;

        private CompressedMultipartFile(String filename, byte[] bytes) {
            this.filename = filename;
            this.bytes = bytes;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return filename; }
        @Override public String getContentType() { return "image/jpeg"; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public java.io.InputStream getInputStream() { return new java.io.ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File dest) throws IOException { java.nio.file.Files.write(dest.toPath(), bytes); }
    }
}
