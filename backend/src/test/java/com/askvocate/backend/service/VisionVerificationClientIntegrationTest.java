package com.askvocate.backend.service;

import com.askvocate.backend.model.DocumentType;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class VisionVerificationClientIntegrationTest {

    @Test
    void backendInvokesLocalOcrWithoutAiService() throws Exception {
        assumePython();

        BufferedImage image = new BufferedImage(900, 140, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 38));
        graphics.drawString("AADHAAR TEST", 35, 90);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        var result = new VisionVerificationClient("auto", "").localOcr(png.toByteArray());
        assertTrue(result.get("ocrText").toString().contains("AADHAAR"));

        var verifier = new VisionVerificationClient("auto", "");
        var qr = verifier.verifyAadhaarQr(png.toByteArray(), Map.of(), "XXXX-XXXX-0000");
        assertTrue(Boolean.FALSE.equals(qr.verified()));
        assertTrue(qr.reason().contains("No readable Aadhaar Secure QR"));
        var selfie = verifier.verifySelfie(png.toByteArray(),
                List.of(png.toByteArray(), png.toByteArray(), png.toByteArray()), "left");
        assertTrue(Boolean.FALSE.equals(selfie.matched()));
        assertTrue(selfie.reason().contains("No readable Aadhaar Secure QR"));
    }

    @Test
    void certificateOcrReadsDegreeFields() throws Exception {
        assumePython();

        BufferedImage image = new BufferedImage(1700, 850, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.setColor(Color.BLACK);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 44));
        graphics.drawString("UNIVERSITY OF DELHI", 380, 90);
        graphics.drawString("PROVISIONAL DEGREE CERTIFICATE", 300, 170);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 36));
        graphics.drawString("Name: Test Lawyer", 300, 300);
        graphics.drawString("Bachelor of Laws (LL.B.)", 300, 390);
        graphics.drawString("Year: 2020", 300, 480);
        graphics.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(image, "png", png);

        var result = new VisionVerificationClient("auto", "").localOcr(png.toByteArray(), "certificate");
        String compact = result.get("ocrText").toString().replace(" ", "");
        assertTrue(compact.contains("LL.B") || compact.contains("LLB"),
                "OCR text did not contain LL.B: " + result.get("ocrText"));

        var extraction = new OcrExtractionService().extract(result, DocumentType.DEGREE_CERTIFICATE, "front");
        assertTrue(extraction.success());
        assertEquals("Test Lawyer", extraction.extractedFields().get("name"));
        assertNotNull(extraction.extractedFields().get("degreeType"));
        assertEquals("2020", extraction.extractedFields().get("year"));
    }

    private void assumePython() throws Exception {
        assumeTrue(Files.isRegularFile(Path.of("verification", "worker.py")));
        boolean pythonAvailable = false;
        try {
            Process check = new ProcessBuilder("python", "-c", "import cv2, PIL, cryptography, pytesseract")
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            pythonAvailable = check.waitFor() == 0;
        } catch (Exception ignored) {
        }
        assumeTrue(pythonAvailable, "Local Python verification dependencies are not installed");
    }
}
