package com.askvocate.backend.service;

import com.askvocate.backend.exception.DocumentVerificationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Runs backend-owned document verification locally; no ai-service HTTP dependency. */
@Service
public class VisionVerificationClient {
    private static final Logger log = LoggerFactory.getLogger(VisionVerificationClient.class);
    private static final long TIMEOUT_SECONDS = 60;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String pythonExecutable;
    private final String configuredWorkerPath;

    public VisionVerificationClient(
            @Value("${verification.python-executable:python}") String pythonExecutable,
            @Value("${verification.worker-path:}") String configuredWorkerPath) {
        this.pythonExecutable = pythonExecutable;
        this.configuredWorkerPath = configuredWorkerPath;
    }

    public QrResult verifyAadhaarQr(byte[] image, Map<String, String> printed, String maskedNumber) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("operation", "aadhaarQr");
            request.put("imageBase64", Base64.getEncoder().encodeToString(image));
            request.put("printedData", printed);
            request.put("maskedDocumentNumber", maskedNumber);
            QrResult result = mapper.readValue(invoke(request), QrResult.class);
            return result != null ? result : QrResult.unavailable("QR verifier returned no result.");
        } catch (Exception error) {
            log.warn("Backend Aadhaar QR verification unavailable: {}", error.getClass().getSimpleName());
            return QrResult.unavailable("Aadhaar QR verifier is unavailable; check backend verification dependencies.");
        }
    }

    public Map<String, Object> localOcr(byte[] image) {
        return localOcr(image, "");
    }

    public Map<String, Object> localOcr(byte[] image, String side) {
        try {
            Map<String, Object> request = Map.of("operation", "ocr", "side", side,
                    "imageBase64", Base64.getEncoder().encodeToString(image));
            Map<?, ?> result = mapper.readValue(invoke(request), Map.class);
            if (result == null) {
                throw new DocumentVerificationException("Backend OCR worker returned no response.");
            }
            Object reason = result.get("reason");
            if (reason instanceof String message && !message.isBlank()) {
                throw new DocumentVerificationException(message);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> ocr = (Map<String, Object>) result;
            return Map.copyOf(ocr);
        } catch (DocumentVerificationException error) {
            throw error;
        } catch (Exception error) {
            log.warn("Backend OCR worker failed: {}", error.getClass().getSimpleName());
            throw new DocumentVerificationException(
                    "Backend OCR is unavailable. Check Python, Tesseract, and verification dependencies.", error);
        }
    }

    public SelfieResult verifySelfie(byte[] qrImage, List<byte[]> frames, String expectedTurn) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("operation", "selfie");
            request.put("qrImageBase64", Base64.getEncoder().encodeToString(qrImage));
            request.put("framesBase64", frames.stream().map(Base64.getEncoder()::encodeToString).toList());
            request.put("expectedTurn", expectedTurn);
            SelfieResult result = mapper.readValue(invoke(request), SelfieResult.class);
            return result != null ? result : SelfieResult.unavailable("Selfie verifier returned no result.");
        } catch (Exception error) {
            log.warn("Backend selfie verification unavailable: {}", error.getClass().getSimpleName());
            return SelfieResult.unavailable("Selfie verifier is unavailable; check backend verification dependencies.");
        }
    }

    private String invoke(Map<String, Object> request) {
        Path worker = resolveWorker();
        byte[] payload;
        try {
            payload = mapper.writeValueAsBytes(request);
        } catch (Exception error) {
            throw new DocumentVerificationException("Could not prepare backend verification request.", error);
        }
        for (List<String> interpreter : pythonCommands()) {
            try {
                return invokeWithInterpreter(worker, interpreter, payload);
            } catch (WorkerStartupException error) {
                log.warn("Backend verification worker failed with {}: {}", interpreter.get(0), error.getMessage());
            }
        }
        throw new DocumentVerificationException(
                "Backend verification worker could not run. Check the backend log for its Python startup error.");
    }

    private String invokeWithInterpreter(Path worker, List<String> interpreter, byte[] payload) {
        Process process = null;
        try {
            List<String> command = new ArrayList<>(interpreter);
            command.add("-u");
            command.add(worker.toString());
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(worker.getParent().toFile());
            builder.environment().put("PYTHONIOENCODING", "utf-8");
            process = builder.start();
            Process running = process;
            CompletableFuture<String> output = readStream(running.getInputStream());
            CompletableFuture<String> errors = readStream(running.getErrorStream());
            try (var stdin = process.getOutputStream()) {
                stdin.write(payload);
            }
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new DocumentVerificationException("Backend verification timed out. Please retry.");
            }
            String response = output.get(5, TimeUnit.SECONDS);
            String stderr = errors.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0 || response.isBlank()) {
                throw new WorkerStartupException("exit=" + process.exitValue() + ", " + firstErrorLine(stderr));
            }
            Map<?, ?> envelope = mapper.readValue(response, Map.class);
            if (envelope != null && envelope.get("error") instanceof String error) {
                throw new WorkerStartupException(error + " " + firstErrorLine(stderr));
            }
            return response;
        } catch (DocumentVerificationException error) {
            throw error;
        } catch (WorkerStartupException error) {
            throw error;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new DocumentVerificationException("Backend verification was interrupted.", error);
        } catch (Exception error) {
            throw new WorkerStartupException(error.getClass().getSimpleName() + ": " + error.getMessage());
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private CompletableFuture<String> readStream(java.io.InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException error) {
                throw new RuntimeException(error);
            }
        });
    }

    private String firstErrorLine(String stderr) {
        if (stderr == null || stderr.isBlank()) return "no Python diagnostic";
        String[] lines = stderr.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            String line = lines[index];
            if (line.contains("Error") || line.contains("error") || line.contains("Traceback")) {
                return line.length() > 240 ? line.substring(0, 240) : line;
            }
        }
        String line = lines[lines.length - 1];
        return line.length() > 240 ? line.substring(0, 240) : line;
    }

    private List<List<String>> pythonCommands() {
        if (pythonExecutable != null && !pythonExecutable.isBlank()
                && !"auto".equalsIgnoreCase(pythonExecutable)) {
            return List.of(List.of(pythonExecutable));
        }
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            return List.of(List.of("python3"), List.of("python"));
        }
        List<List<String>> commands = new ArrayList<>();
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            Path installations = Path.of(localAppData, "Programs", "Python");
            if (Files.isDirectory(installations)) {
                try (var entries = Files.list(installations)) {
                    entries.map(directory -> directory.resolve("python.exe"))
                            .filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(Path::toString).reversed())
                            .forEach(executable -> commands.add(List.of(executable.toString())));
                } catch (IOException error) {
                    log.debug("Could not inspect local Python installations", error);
                }
            }
        }
        commands.add(List.of("py", "-3"));
        commands.add(List.of("python"));
        return commands;
    }

    private static final class WorkerStartupException extends RuntimeException {
        private WorkerStartupException(String message) { super(message); }
    }

    private Path resolveWorker() {
        List<Path> candidates = configuredWorkerPath == null || configuredWorkerPath.isBlank()
                ? List.of(Path.of("verification", "worker.py"), Path.of("backend", "verification", "worker.py"))
                : List.of(Path.of(configuredWorkerPath));
        for (Path candidate : candidates) {
            Path resolved = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(resolved)) return resolved;
        }
        throw new DocumentVerificationException(
                "Backend verification worker is missing. Set VERIFICATION_WORKER_PATH to backend/verification/worker.py.");
    }

    public record QrResult(Boolean verified, Map<String, String> data, Boolean photoAvailable,
                           String certificate, List<String> mismatchFields, Boolean printedMismatch, String reason) {
        public static QrResult unavailable(String reason) {
            return new QrResult(false, null, false, null, List.of(), null, reason);
        }
        public boolean matched() {
            return Boolean.TRUE.equals(verified) && Boolean.FALSE.equals(printedMismatch)
                    && data != null && Boolean.TRUE.equals(photoAvailable)
                    && mismatchFields != null && mismatchFields.isEmpty();
        }
    }

    public record SelfieResult(Double faceMatchScore, Double faceMatchThreshold, Boolean livenessPassed,
                               Double livenessScore, Boolean matched, String reason) {
        public static SelfieResult unavailable(String reason) {
            return new SelfieResult(null, null, false, null, false, reason);
        }
    }
}
