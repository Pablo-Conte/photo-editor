package pdi.pablokaue.filters;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.util.LoadLibs;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;

import java.util.*;

public class LetterRecognitionChallenge {

    public static class LetterResult {
        public final List<Character> letters;

        LetterResult(Set<Character> detected) {
            letters = new ArrayList<>(detected);
            Collections.sort(letters);
        }

        public String toFormattedString() {
            if (letters.isEmpty()) return "Nenhuma letra detectada";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < letters.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(letters.get(i));
            }
            return sb.toString();
        }
    }

    public static LetterResult recognizeLetters(BufferedImage img) {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        BufferedImage processed = preprocess(img);

        Set<Character> detected = new TreeSet<>();
        try {
            String raw = runOCR(processed);
            for (char c : raw.toUpperCase().toCharArray()) {
                if (c >= 'A' && c <= 'Z') detected.add(c);
            }
        } catch (TesseractException e) {
            System.err.println("[LetterRecognition] OCR falhou: " + e.getMessage());
        }

        return new LetterResult(detected);
    }

    // ── Preprocessing ─────────────────────────────────────────────────────

    private static BufferedImage preprocess(BufferedImage img) {
        Mat src     = bufferedImageToMat(img);
        Mat gray    = new Mat();
        Mat blurred = new Mat();
        Mat binary  = new Mat();
        Mat dilated = new Mat();

        try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

            // Gaussian blur: remove high-frequency noise before threshold
            Imgproc.GaussianBlur(gray, blurred, new Size(3, 3), 0);

            // Otsu: auto-detect threshold; invert so letters are white on black
            Scalar mean = Core.mean(blurred);
            int threshType = mean.val[0] > 127
                    ? Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU
                    : Imgproc.THRESH_BINARY     | Imgproc.THRESH_OTSU;
            Imgproc.threshold(blurred, binary, 0, 255, threshType);

            // Dilate: fill gaps in thin strokes, helps Tesseract read thin fonts
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            Imgproc.dilate(binary, dilated, kernel);
            kernel.release();

            // Invert back: Tesseract expects dark letters on white background
            Core.bitwise_not(dilated, dilated);

            return matToBufferedImage(dilated);
        } finally {
            src.release();
            gray.release();
            blurred.release();
            binary.release();
            dilated.release();
        }
    }

    // ── OCR ───────────────────────────────────────────────────────────────

    private static String runOCR(BufferedImage img) throws TesseractException {
        Tesseract tess = new Tesseract();

        // Use tessdata bundled inside the tess4j JAR — no system install of -eng needed
        File tessdata = LoadLibs.extractTessResources("tessdata");
        tess.setDatapath(tessdata.getParent());

        tess.setLanguage("eng");
        tess.setPageSegMode(11);  // PSM_SPARSE_TEXT: letters scattered on page
        tess.setOcrEngineMode(1); // OEM_LSTM_ONLY
        tess.setVariable("tessedit_char_whitelist", "ABCDEFGHIJKLMNOPQRSTUVWXYZ");

        return tess.doOCR(img);
    }

    // ── Mat / BufferedImage helpers ───────────────────────────────────────

    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = converted.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        Mat mat = new Mat(converted.getHeight(), converted.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, ((DataBufferByte) converted.getRaster().getDataBuffer()).getData());
        return mat;
    }

    private static BufferedImage matToBufferedImage(Mat m) {
        int type = m.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage out = new BufferedImage(m.cols(), m.rows(), type);
        m.get(0, 0, ((DataBufferByte) out.getRaster().getDataBuffer()).getData());
        return out;
    }
}
