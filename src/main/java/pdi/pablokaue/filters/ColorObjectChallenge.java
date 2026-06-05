package pdi.pablokaue.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.*;

public class ColorObjectChallenge {

    private static final double MIN_AREA = 500.0;

    public static class ColorCountResult {
        private final Map<String, Integer> counts;

        ColorCountResult(Map<String, Integer> counts) {
            this.counts = counts;
        }

        public Map<String, Integer> getCounts() {
            return Collections.unmodifiableMap(counts);
        }

        public String toFormattedString() {
            // Fixed display order
            String[][] colors = {
                {"vermelho",  "vermelhos"},
                {"azul",      "azuis"},
                {"verde",     "verdes"},
                {"amarelo",   "amarelos"},
            };

            List<String> parts = new ArrayList<>();
            for (String[] c : colors) {
                int n = counts.getOrDefault(c[0], 0);
                if (n == 0) continue;
                if (n == 1) {
                    parts.add("1 objeto " + c[0]);
                } else {
                    parts.add(n + " " + c[1]);
                }
            }

            if (parts.isEmpty()) return "Nenhum objeto colorido detectado";
            return String.join(", ", parts);
        }
    }

    public static ColorCountResult countColorObjects(BufferedImage img) {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        Mat src = bufferedImageToMat(img);
        Mat hsv = new Mat();
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Mat redMask  = new Mat();
        Mat blueMask  = new Mat();
        Mat greenMask = new Mat();
        Mat yellowMask = new Mat();
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));

        try {
            Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV);

            // Red wraps in HSV — needs two ranges
            Core.inRange(hsv, new Scalar(0, 100, 100),   new Scalar(10, 255, 255),  redMask1);
            Core.inRange(hsv, new Scalar(160, 100, 100), new Scalar(180, 255, 255), redMask2);
            Core.add(redMask1, redMask2, redMask);

            Core.inRange(hsv, new Scalar(100, 100, 100), new Scalar(130, 255, 255), blueMask);
            Core.inRange(hsv, new Scalar(40,  100, 100), new Scalar(85,  255, 255), greenMask);
            Core.inRange(hsv, new Scalar(20,  100, 100), new Scalar(35,  255, 255), yellowMask);

            // Morphological open: remove small noise
            Imgproc.morphologyEx(redMask,    redMask,    Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(blueMask,   blueMask,   Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(greenMask,  greenMask,  Imgproc.MORPH_OPEN, kernel);
            Imgproc.morphologyEx(yellowMask, yellowMask, Imgproc.MORPH_OPEN, kernel);

            Map<String, Integer> counts = new LinkedHashMap<>();
            counts.put("vermelho", countContours(redMask));
            counts.put("azul",     countContours(blueMask));
            counts.put("verde",    countContours(greenMask));
            counts.put("amarelo",  countContours(yellowMask));

            return new ColorCountResult(counts);
        } finally {
            src.release();
            hsv.release();
            redMask1.release();
            redMask2.release();
            redMask.release();
            blueMask.release();
            greenMask.release();
            yellowMask.release();
            kernel.release();
        }
    }

    private static int countContours(Mat mask) {
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        try {
            Imgproc.findContours(mask.clone(), contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            int count = 0;
            for (MatOfPoint c : contours) {
                if (Imgproc.contourArea(c) > MIN_AREA) count++;
                c.release();
            }
            return count;
        } finally {
            hierarchy.release();
        }
    }

    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(
                bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = converted.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        Mat mat = new Mat(converted.getHeight(), converted.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }
}
