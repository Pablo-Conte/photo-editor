package pdi.pablokaue.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.*;

public class TrafficSignChallenge {

    private static final double MIN_SIGN_AREA = 4500.0;

    public static class TrafficSignResult {
        public final List<String> signTypes;

        TrafficSignResult(List<String> signTypes) {
            this.signTypes = List.copyOf(signTypes);
        }

        public String toFormattedString() {
            if (signTypes.isEmpty()) return "Nenhuma placa detectada";
            return String.join(", ", signTypes);
        }
    }

    private record Candidate(Rect rect, MatOfPoint contour) {
    }

    public static TrafficSignResult identifyTrafficSigns(BufferedImage img) {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        Mat src = bufferedImageToMat(img);
        Mat hsv = new Mat();
        Mat redMask1 = new Mat();
        Mat redMask2 = new Mat();
        Mat redMask = new Mat();
        Mat cleaned = new Mat();
        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();

        try {
            Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV);
            Core.inRange(hsv, new Scalar(0, 90, 90), new Scalar(10, 255, 255), redMask1);
            Core.inRange(hsv, new Scalar(160, 90, 90), new Scalar(180, 255, 255), redMask2);
            Core.add(redMask1, redMask2, redMask);

            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(5, 5));
            try {
                Imgproc.morphologyEx(redMask, cleaned, Imgproc.MORPH_CLOSE, kernel);
                Imgproc.morphologyEx(cleaned, cleaned, Imgproc.MORPH_OPEN, kernel);
            } finally {
                kernel.release();
            }

            Imgproc.findContours(cleaned.clone(), contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            List<Candidate> candidates = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area < MIN_SIGN_AREA) continue;
                Rect rect = Imgproc.boundingRect(contour);
                if (rect.width < 60 || rect.height < 60) continue;
                candidates.add(new Candidate(rect, contour));
            }

            List<Candidate> selected = nonMaximumSuppression(candidates);
            selected.sort(Comparator.comparingInt(c -> c.rect.x));

            List<String> signs = new ArrayList<>();
            for (Candidate candidate : selected) {
                Rect roiRect = expandAndClampRect(candidate.rect, src.cols(), src.rows(), 0.10);
                Mat signRoi = new Mat(src, roiRect);
                try {
                    String detected = classifySign(candidate.contour, signRoi);
                    if (detected != null) signs.add(detected);
                } finally {
                    signRoi.release();
                }
            }

            return new TrafficSignResult(signs);
        } finally {
            src.release();
            hsv.release();
            redMask1.release();
            redMask2.release();
            redMask.release();
            cleaned.release();
            hierarchy.release();
            for (MatOfPoint contour : contours) {
                contour.release();
            }
        }
    }

    private static List<Candidate> nonMaximumSuppression(List<Candidate> candidates) {
        List<Candidate> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> Double.compare(b.rect.area(), a.rect.area()));

        List<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : sorted) {
            boolean overlaps = false;
            for (Candidate keep : selected) {
                if (iou(candidate.rect, keep.rect) > 0.45) {
                    overlaps = true;
                    break;
                }
            }
            if (!overlaps) selected.add(candidate);
        }
        return selected;
    }

    private static double iou(Rect a, Rect b) {
        int x1 = Math.max(a.x, b.x);
        int y1 = Math.max(a.y, b.y);
        int x2 = Math.min(a.x + a.width, b.x + b.width);
        int y2 = Math.min(a.y + a.height, b.y + b.height);

        int iw = Math.max(0, x2 - x1);
        int ih = Math.max(0, y2 - y1);
        double inter = (double) iw * ih;
        double union = a.area() + b.area() - inter;
        if (union <= 0.0) return 0.0;
        return inter / union;
    }

    private static Rect expandAndClampRect(Rect r, int maxW, int maxH, double marginRatio) {
        int marginX = (int) Math.round(r.width * marginRatio);
        int marginY = (int) Math.round(r.height * marginRatio);
        int x = Math.max(0, r.x - marginX);
        int y = Math.max(0, r.y - marginY);
        int x2 = Math.min(maxW, r.x + r.width + marginX);
        int y2 = Math.min(maxH, r.y + r.height + marginY);
        return new Rect(x, y, Math.max(1, x2 - x), Math.max(1, y2 - y));
    }

    private static String classifySign(MatOfPoint contour, Mat roiColor) {
        double redCoverage = redCoverage(roiColor);
        if (redCoverage > 0.45 || (isStopSign(contour) && redCoverage > 0.38)) return "Pare";

        boolean hasSlash = hasRedDiagonalSlash(roiColor);
        boolean hasParkingShape = looksLikeParkingE(roiColor);
        boolean hasSpeedText = looksLikeSpeedLimitText(roiColor);
        boolean hasDirectionalArrow = looksLikeDirectionalArrow(roiColor);

        if (hasSlash) {
            if (hasParkingShape) return "Proibido estacionar";
            return "Sentido proibido";
        }

        if (hasParkingShape) return "Proibido estacionar";
        if (hasSpeedText) return "Velocidade máxima";
        if (hasDirectionalArrow) return "Sentido obrigatório";

        // Sinal circular sem barra, sem "E" e sem seta tende a ser de velocidade.
        return redCoverage < 0.35 ? "Velocidade máxima" : "Pare";
    }

    private static boolean isStopSign(MatOfPoint contour) {
        MatOfPoint2f curve = new MatOfPoint2f(contour.toArray());
        MatOfPoint2f approx = new MatOfPoint2f();
        try {
            double peri = Imgproc.arcLength(curve, true);
            Imgproc.approxPolyDP(curve, approx, 0.02 * peri, true);

            int vertices = (int) approx.total();
            double area = Imgproc.contourArea(contour);
            Rect rect = Imgproc.boundingRect(contour);
            if (peri <= 0.0) return false;
            double circularity = 4.0 * Math.PI * area / (peri * peri);
            double fillRatio = area / Math.max(1.0, rect.area());
            return vertices >= 7 && vertices <= 9
                    && circularity > 0.70 && circularity < 0.93
                    && fillRatio > 0.55;
        } finally {
            curve.release();
            approx.release();
        }
    }

    private static boolean hasRedDiagonalSlash(Mat roiColor) {
        Mat hsv = new Mat();
        Mat red1 = new Mat();
        Mat red2 = new Mat();
        Mat mask = new Mat();
        Mat center = new Mat();
        Mat lines = new Mat();
        try {
            Imgproc.cvtColor(roiColor, hsv, Imgproc.COLOR_BGR2HSV);
            Core.inRange(hsv, new Scalar(0, 90, 90), new Scalar(10, 255, 255), red1);
            Core.inRange(hsv, new Scalar(160, 90, 90), new Scalar(180, 255, 255), red2);
            Core.add(red1, red2, mask);

            Rect centerRect = normRect(mask.cols(), mask.rows(), 0.15, 0.15, 0.70, 0.70);
            center = new Mat(mask, centerRect);

            double centerDensity = Core.countNonZero(center) / (center.cols() * (double) center.rows());
            if (centerDensity > 0.30) return false;

            Imgproc.HoughLinesP(center, lines, 1, Math.PI / 180, 45,
                    Math.min(center.cols(), center.rows()) * 0.35, 20);

            double centerX = center.cols() / 2.0;
            double centerY = center.rows() / 2.0;
            double diag = Math.hypot(center.cols(), center.rows());

            for (int i = 0; i < lines.rows(); i++) {
                double[] l = lines.get(i, 0);
                if (l == null || l.length < 4) continue;
                double x1 = l[0], y1 = l[1], x2 = l[2], y2 = l[3];
                double dx = x2 - x1;
                double dy = y2 - y1;
                double len = Math.hypot(dx, dy);
                if (len < diag * 0.32) continue;

                double angle = Math.abs(Math.toDegrees(Math.atan2(dy, dx)));
                if (angle > 90) angle = 180 - angle;
                if (angle < 25 || angle > 70) continue;

                double midX = (x1 + x2) / 2.0;
                double midY = (y1 + y2) / 2.0;
                if (Math.abs(midX - centerX) < center.cols() * 0.30
                        && Math.abs(midY - centerY) < center.rows() * 0.30) {
                    return true;
                }
            }
            return false;
        } finally {
            hsv.release();
            red1.release();
            red2.release();
            mask.release();
            center.release();
            lines.release();
        }
    }

    private static boolean looksLikeParkingE(Mat roiColor) {
        Mat gray = new Mat();
        Mat black = new Mat();
        try {
            Imgproc.cvtColor(roiColor, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, black, 80, 255, Imgproc.THRESH_BINARY_INV);

            int w = black.cols();
            int h = black.rows();

            Rect stemLeft = normRect(w, h, 0.30, 0.22, 0.14, 0.58);
            Rect armTopRight = normRect(w, h, 0.48, 0.24, 0.24, 0.14);
            Rect armMidRight = normRect(w, h, 0.48, 0.47, 0.20, 0.12);
            Rect armBottomRight = normRect(w, h, 0.48, 0.67, 0.24, 0.14);

            double dStemLeft = density(black, stemLeft);
            double dArmTop = density(black, armTopRight);
            double dArmMid = density(black, armMidRight);
            double dArmBottom = density(black, armBottomRight);

            return dStemLeft > 0.14
                    && dArmTop > 0.07
                    && dArmMid > 0.05
                    && dArmBottom > 0.07;
        } finally {
            gray.release();
            black.release();
        }
    }

    private static boolean looksLikeDirectionalArrow(Mat roiColor) {
        Mat gray = new Mat();
        Mat black = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        try {
            Imgproc.cvtColor(roiColor, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, black, 80, 255, Imgproc.THRESH_BINARY_INV);

            Imgproc.findContours(black.clone(), contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            double roiArea = (double) black.cols() * black.rows();
            for (MatOfPoint c : contours) {
                double area = Imgproc.contourArea(c);
                if (area < roiArea * 0.03) continue;
                Rect r = Imgproc.boundingRect(c);
                double ratio = r.height / (double) Math.max(1, r.width);
                if (ratio < 1.3) continue;

                int topY = r.y;
                int bottomY = r.y + r.height;
                if (topY > black.rows() * 0.35) continue;
                if (bottomY < black.rows() * 0.75) continue;
                return true;
            }
            return false;
        } finally {
            gray.release();
            black.release();
            hierarchy.release();
            for (MatOfPoint c : contours) c.release();
        }
    }

    private static boolean looksLikeSpeedLimitText(Mat roiColor) {
        Mat gray = new Mat();
        Mat black = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        try {
            Imgproc.cvtColor(roiColor, gray, Imgproc.COLOR_BGR2GRAY);
            Imgproc.threshold(gray, black, 90, 255, Imgproc.THRESH_BINARY_INV);

            Imgproc.findContours(black.clone(), contours, hierarchy,
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            double roiArea = (double) black.cols() * black.rows();
            Rect center = normRect(black.cols(), black.rows(), 0.20, 0.20, 0.60, 0.60);
            int count = 0;

            for (MatOfPoint c : contours) {
                double area = Imgproc.contourArea(c);
                if (area < roiArea * 0.002 || area > roiArea * 0.20) continue;

                Rect r = Imgproc.boundingRect(c);
                if (!intersects(r, center)) continue;

                double ratio = r.height / (double) Math.max(1, r.width);
                if (ratio < 0.35 || ratio > 6.0) continue;
                count++;
            }

            return count >= 4;
        } finally {
            gray.release();
            black.release();
            hierarchy.release();
            for (MatOfPoint c : contours) c.release();
        }
    }

    private static boolean intersects(Rect a, Rect b) {
        return a.x < b.x + b.width && a.x + a.width > b.x
                && a.y < b.y + b.height && a.y + a.height > b.y;
    }

    private static double redCoverage(Mat roiColor) {
        Mat hsv = new Mat();
        Mat red1 = new Mat();
        Mat red2 = new Mat();
        Mat red = new Mat();
        try {
            Imgproc.cvtColor(roiColor, hsv, Imgproc.COLOR_BGR2HSV);
            Core.inRange(hsv, new Scalar(0, 90, 90), new Scalar(10, 255, 255), red1);
            Core.inRange(hsv, new Scalar(160, 90, 90), new Scalar(180, 255, 255), red2);
            Core.add(red1, red2, red);
            double nz = Core.countNonZero(red);
            return nz / (red.cols() * (double) red.rows());
        } finally {
            hsv.release();
            red1.release();
            red2.release();
            red.release();
        }
    }

    private static Rect normRect(int w, int h, double x, double y, double rw, double rh) {
        int rx = (int) Math.round(w * x);
        int ry = (int) Math.round(h * y);
        int rrw = Math.max(1, (int) Math.round(w * rw));
        int rrh = Math.max(1, (int) Math.round(h * rh));
        rx = Math.max(0, Math.min(rx, w - 1));
        ry = Math.max(0, Math.min(ry, h - 1));
        rrw = Math.min(rrw, w - rx);
        rrh = Math.min(rrh, h - ry);
        return new Rect(rx, ry, rrw, rrh);
    }

    private static double density(Mat binaryMask, Rect r) {
        Mat roi = new Mat(binaryMask, r);
        try {
            double nonZero = Core.countNonZero(roi);
            return nonZero / (r.width * (double) r.height);
        } finally {
            roi.release();
        }
    }

    private static Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = converted.createGraphics();
        g.drawImage(bi, 0, 0, null);
        g.dispose();
        Mat mat = new Mat(converted.getHeight(), converted.getWidth(), CvType.CV_8UC3);
        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        mat.put(0, 0, data);
        return mat;
    }
}
