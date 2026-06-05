package pdi.pablokaue.filters;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

public class ClockChallenge {

    public static class ClockResult {
        public final String time;
        public final String debug;

        ClockResult(String time, String debug) {
            this.time = time;
            this.debug = debug;
        }
    }

    private static final int N_RAYS = 360; // resolução: 1° por raio

    public static ClockResult readClock(BufferedImage img, ProcessingStepListener listener) {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        // 1. Pré-processar
        Mat src = bufferedImageToMat(img);
        src = resizeImage(src);
        notifyListener(listener, "1.1 Resize", src);

        Mat gray = new Mat();
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY);

        Mat binary = new Mat();
        // Ponteiros pretos → brancos (BINARY_INV), fundo branco → preto
        Imgproc.threshold(gray, binary, 0, 255, Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU);
        notifyListener(listener, "1.2 Threshold (Otsu+Inv)", binary);

        Mat blurred = new Mat();
        Imgproc.GaussianBlur(binary, blurred, new Size(5, 5), 0);
        notifyListener(listener, "1.3 Blur", blurred);

        Mat grayBlurred = new Mat();
        Imgproc.GaussianBlur(gray, grayBlurred, new Size(9, 9), 2);

        // 2. Detectar círculo externo do relógio
        int[] clock = clockDetection(src, grayBlurred);
        int clockCx = clock[0], clockCy = clock[1], radius = clock[2];

        Mat circleViz = src.clone();
        Imgproc.circle(circleViz, new Point(clockCx, clockCy), radius, new Scalar(0, 255, 0), 2);
        notifyListener(listener, "2. Círculo do Relógio", circleViz);

        // 3. Encontrar o pivô (haste — círculo pequeno no interior)
        int[] pivot = findPivot(blurred, clockCx, clockCy, radius);
        int px = pivot[0], py = pivot[1], pr = pivot[2];

        Mat pivotViz = src.clone();
        Imgproc.circle(pivotViz, new Point(px, py), pr + 3, new Scalar(0, 0, 255), 2);
        Imgproc.circle(pivotViz, new Point(px, py), 3, new Scalar(255, 0, 0), -1);
        notifyListener(listener, "3. Pivô (Haste)", pivotViz);

        // 4. Projetar raios a partir do pivô
        int maxRay = (int) (radius * 0.85); // não ultrapassa marcas de tick
        double[] profile = rayCastProfile(binary, px, py, pr, maxRay);

        Mat rayViz = visualizeProfile(src.clone(), px, py, profile);
        notifyListener(listener, "4. Perfil Radial dos Raios", rayViz);

        // 5. Encontrar os dois picos (= dois ponteiros)
        int[] peaks = findTwoPeaks(profile);
        int idx1 = peaks[0], idx2 = peaks[1];

        // Debug: visualiza percurso dos dois scanners (CW=azul, CCW=magenta)
        notifyListener(listener, "5a. Debug Scanners",
                visualizeScanners(src.clone(), px, py, profile, idx1));

        double clockAngle1 = rayIndexToClockAngle(idx1);
        double clockAngle2 = rayIndexToClockAngle(idx2);
        double len1 = profile[idx1];
        double len2 = profile[idx2];

        // Mais longo = minuto, mais curto = hora
        double hourAngle = len1 >= len2 ? clockAngle2 : clockAngle1;
        double minuteAngle = len1 >= len2 ? clockAngle1 : clockAngle2;
        double hourLen = len1 >= len2 ? len2 : len1;
        double minuteLen = len1 >= len2 ? len1 : len2;

        // Visualizar ponteiros identificados
        Mat handsViz = src.clone();
        drawRayHand(handsViz, px, py, hourAngle, hourLen, new Scalar(255, 80, 80), "H");
        drawRayHand(handsViz, px, py, minuteAngle, minuteLen, new Scalar(80, 255, 80), "M");
        notifyListener(listener, "5. Ponteiros Identificados", handsViz);

        // 6. Calcular horário
        String time = getTime(hourAngle, minuteAngle, 0);
        String debug = String.format(
                "Pivô: (%d, %d)  r_haste: %d  Raio relógio: %d%n" +
                        "Ponteiro hora:   ângulo=%.1f°  len=%.1f%n" +
                        "Ponteiro minuto: ângulo=%.1f°  len=%.1f",
                px, py, pr, radius,
                hourAngle, hourLen,
                minuteAngle, minuteLen);

        return new ClockResult(time, debug);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENCONTRAR O PIVÔ (círculo pequeno — a haste)
    // ─────────────────────────────────────────────────────────────────────────
    private static int[] findPivot(Mat binary, int approxCx, int approxCy, int radius) {
        // ROI de 25% do raio ao redor do centro aproximado
        int roiR = (int) (radius * 0.25);
        int roiX = Math.max(0, approxCx - roiR);
        int roiY = Math.max(0, approxCy - roiR);
        int roiW = Math.min(binary.cols() - roiX, roiR * 2);
        int roiH = Math.min(binary.rows() - roiY, roiR * 2);

        Mat roi = new Mat(binary, new Rect(roiX, roiY, roiW, roiH));

        // Tenta detectar círculo pequeno (a haste) com HoughCircles
        Mat circles = new Mat();
        Imgproc.HoughCircles(roi, circles, Imgproc.HOUGH_GRADIENT,
                1.0, roiR, // dp, minDist
                30, 10, // param1 (Canny), param2 (acumulador)
                3, 25); // minRadius, maxRadius (px na imagem 1000px)

        System.out.println("[findPivot] círculos encontrados: " + circles.cols());
        for (int i = 0; i < circles.cols(); i++) {
            double[] c = circles.get(0, i);
            System.out.printf("  [%d] cx=%.0f cy=%.0f r=%.0f%n", i, c[0] + roiX, c[1] + roiY, c[2]);
        }
        if (circles.cols() > 0) {
            // Pega o círculo mais próximo do centro aproximado
            double bestDist = Double.MAX_VALUE;
            int[] best = null;
            for (int i = 0; i < circles.cols(); i++) {
                double[] c = circles.get(0, i);
                int cx = (int) c[0] + roiX;
                int cy = (int) c[1] + roiY;
                double d = Math.hypot(cx - approxCx, cy - approxCy);
                if (d < bestDist) {
                    bestDist = d;
                    best = new int[] { cx, cy, (int) c[2] };
                }
            }
            if (best != null)
                return best;
        }

        // Fallback: usa centro aproximado do círculo do relógio
        return new int[] { approxCx, approxCy, 8 };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PROJEÇÃO RADIAL: para cada ângulo, mede até onde vai o pixel branco
    // ─────────────────────────────────────────────────────────────────────────
    private static double[] rayCastProfile(Mat binary, int px, int py, int pivotR, int maxRadius) {
        // Lê todos os pixels de uma vez para evitar overhead JNI por pixel
        int cols = binary.cols(), rows = binary.rows();
        byte[] data = new byte[cols * rows];
        binary.get(0, 0, data);

        double[] profile = new double[N_RAYS];

        for (int i = 0; i < N_RAYS; i++) {
            double rad = 2 * Math.PI * i / N_RAYS;
            double dx = Math.cos(rad);
            double dy = Math.sin(rad);

            int startStep = pivotR + 4; // começa além do pivô
            double farthest = 0;
            int gap = 0; // pixels pretos consecutivos (tolerância)

            for (int step = startStep; step <= maxRadius; step++) {
                int x = (int) Math.round(px + step * dx);
                int y = (int) Math.round(py + step * dy);
                if (x < 0 || x >= cols || y < 0 || y >= rows)
                    break;

                int val = data[y * cols + x] & 0xFF;
                if (val > 128) {
                    farthest = step - startStep + 1;
                    gap = 0;
                } else {
                    gap++;
                    // Tolera até 4px pretos (artefatos de compressão, anti-aliasing)
                    if (gap > 4)
                        break;
                }
            }
            profile[i] = farthest;
        }

        return profile;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ENCONTRAR DOIS PICOS NO PERFIL RADIAL
    // Dois scanners saem do pico principal em direções opostas.
    // Cada um espera descer num vale antes de subir — garante ponteiros distintos
    // mesmo quando estão a 45° de distância.
    // ─────────────────────────────────────────────────────────────────────────
    private static int[] findTwoPeaks(double[] profile) {
        // Suaviza com média móvel ±2°
        double[] smoothed = new double[N_RAYS];
        int win = 2;
        for (int i = 0; i < N_RAYS; i++) {
            double sum = 0;
            for (int j = -win; j <= win; j++)
                sum += profile[(i + j + N_RAYS) % N_RAYS];
            smoothed[i] = sum / (2 * win + 1);
        }

        // Pico 1: máximo global
        int peak1 = 0;
        for (int i = 1; i < N_RAYS; i++) {
            if (smoothed[i] > smoothed[peak1])
                peak1 = i;
        }

        double threshold = smoothed[peak1] * 0.99; // vale precisa cair abaixo de 15%

        // Scanner horário (peak1 → +360°): espera vale, depois sobe de novo
        int cwPeak = -1;
        boolean cwInValley = false;
        for (int step = 1; step < N_RAYS; step++) {
            int i = (peak1 + step) % N_RAYS;
            if (smoothed[i] < threshold) {
                cwInValley = true;
            } else if (cwInValley) {
                while (smoothed[(i + 1) % N_RAYS] > smoothed[i])
                    i = (i + 1) % N_RAYS;
                cwPeak = i;
                break;
            }
        }

        // Scanner anti-horário (peak1 → -360°): mesmo processo no sentido oposto
        int ccwPeak = -1;
        boolean ccwInValley = false;
        for (int step = 1; step < N_RAYS; step++) {
            int i = (peak1 - step + N_RAYS) % N_RAYS;
            if (smoothed[i] < threshold) {
                ccwInValley = true;
            } else if (ccwInValley) {
                while (smoothed[(i - 1 + N_RAYS) % N_RAYS] > smoothed[i])
                    i = (i - 1 + N_RAYS) % N_RAYS;
                ccwPeak = i;
                break;
            }
        }

        // Escolhe o pico mais forte dos dois lados
        int peak2;
        if (cwPeak >= 0 && ccwPeak >= 0) {
            peak2 = smoothed[cwPeak] >= smoothed[ccwPeak] ? cwPeak : ccwPeak;
        } else if (cwPeak >= 0) {
            peak2 = cwPeak;
        } else if (ccwPeak >= 0) {
            peak2 = ccwPeak;
        } else {
            // Fallback: maior valor excluindo ±30° de peak1
            double[] copy = smoothed.clone();
            for (int i = -30; i <= 30; i++)
                copy[(peak1 + i + N_RAYS) % N_RAYS] = 0;
            peak2 = 0;
            for (int i = 1; i < N_RAYS; i++)
                if (copy[i] > copy[peak2])
                    peak2 = i;
        }

        return new int[] { peak1, peak2 };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CONVERTER ÍNDICE DE RAIO → ÂNGULO DO RELÓGIO (0° = 12h, sentido horário)
    // ─────────────────────────────────────────────────────────────────────────
    private static double rayIndexToClockAngle(int idx) {
        // idx=0: dx=1 dy=0 → direita → 3h = 90°
        // idx=N/4: dx=0 dy=1 → baixo → 6h = 180°
        // idx=N/2: dx=-1 dy=0 → esquerda → 9h = 270°
        // idx=3N/4: dx=0 dy=-1 → cima → 12h = 0°
        return (idx * 360.0 / N_RAYS + 90.0) % 360.0;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CALCULAR HORÁRIO A PARTIR DOS ÂNGULOS
    // ─────────────────────────────────────────────────────────────────────────
    private static String getTime(double hourAngle, double minuteAngle, double secondAngle) {
        int minute = (int) Math.round(minuteAngle / 6.0);
        if (minute == 60)
            minute = 0;

        int second = (int) Math.round(secondAngle / 6.0);
        if (second == 60)
            second = 0;

        // O ponteiro de hora avança 0.5° por minuto → corrigir
        double hourCorrected = hourAngle / 30.0 - minute / 60.0;
        int hour = (int) Math.round(hourCorrected);
        if (hour < 0)
            hour += 12;
        if (hour == 0)
            hour = 12;
        if (hour > 12)
            hour -= 12;

        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DETECTAR CÍRCULO DO RELÓGIO (HoughCircles no grayscale borrado)
    // ─────────────────────────────────────────────────────────────────────────
    private static int[] clockDetection(Mat src, Mat blurred) {
        Mat circles = new Mat();
        Imgproc.HoughCircles(blurred, circles, Imgproc.HOUGH_GRADIENT,
                1, 400, 50, 40, 100, 500);

        int cx = src.cols() / 2, cy = src.rows() / 2, r = Math.min(cx, cy) - 10;
        System.out.println("[clockDetection] círculos encontrados: " + circles.cols());
        for (int i = 0; i < circles.cols(); i++) {
            double[] c = circles.get(0, i);
            System.out.printf("  [%d] cx=%.0f cy=%.0f r=%.0f%n", i, c[0], c[1], c[2]);
        }
        if (circles.cols() > 0) {
            double maxR = 0;
            for (int i = 0; i < circles.cols(); i++) {
                double[] c = circles.get(0, i);
                if (c[2] > maxR) {
                    maxR = c[2];
                    cx = (int) c[0];
                    cy = (int) c[1];
                    r = (int) c[2];
                }
            }
        }
        return new int[] { cx, cy, r };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPERS VISUAIS
    // ─────────────────────────────────────────────────────────────────────────

    // Mostra o percurso de cada scanner: CW=azul, CCW=magenta, pico
    // encontrado=amarelo
    private static Mat visualizeScanners(Mat base, int px, int py, double[] profile, int peak1) {
        double[] smoothed = new double[N_RAYS];
        int win = 2;
        for (int i = 0; i < N_RAYS; i++) {
            double sum = 0;
            for (int j = -win; j <= win; j++)
                sum += profile[(i + j + N_RAYS) % N_RAYS];
            smoothed[i] = sum / (2 * win + 1);
        }
        double threshold = smoothed[peak1] * 0.15;

        // Scanner CW (azul = vale, verde = subida)
        boolean cwInValley = false;
        for (int step = 1; step < N_RAYS; step++) {
            int i = (peak1 + step) % N_RAYS;
            double rad = 2 * Math.PI * i / N_RAYS;
            int ex = (int) (px + smoothed[i] * Math.cos(rad));
            int ey = (int) (py + smoothed[i] * Math.sin(rad));
            Scalar color;
            if (smoothed[i] < threshold) {
                cwInValley = true;
                color = new Scalar(255, 80, 80); // azul = vale CW
            } else if (cwInValley) {
                color = new Scalar(255, 255, 0); // ciano = subida CW (pico encontrado)
                Imgproc.circle(base, new Point(ex, ey), 4, color, -1);
                break;
            } else {
                color = new Scalar(200, 100, 80); // azul escuro = descida do pico1
            }
            if (smoothed[i] > 5)
                Imgproc.line(base, new Point(px, py), new Point(ex, ey), color, 1);
        }

        // Scanner CCW (magenta = vale, laranja = subida)
        boolean ccwInValley = false;
        for (int step = 1; step < N_RAYS; step++) {
            int i = (peak1 - step + N_RAYS) % N_RAYS;
            double rad = 2 * Math.PI * i / N_RAYS;
            int ex = (int) (px + smoothed[i] * Math.cos(rad));
            int ey = (int) (py + smoothed[i] * Math.sin(rad));
            Scalar color;
            if (smoothed[i] < threshold) {
                ccwInValley = true;
                color = new Scalar(80, 80, 255); // vermelho = vale CCW
            } else if (ccwInValley) {
                color = new Scalar(0, 165, 255); // laranja = subida CCW (pico encontrado)
                Imgproc.circle(base, new Point(ex, ey), 4, color, -1);
                break;
            } else {
                color = new Scalar(60, 60, 200); // vermelho escuro = descida do pico1
            }
            if (smoothed[i] > 5)
                Imgproc.line(base, new Point(px, py), new Point(ex, ey), color, 1);
        }

        // Marca o pico1 em branco
        double r1 = 2 * Math.PI * peak1 / N_RAYS;
        int e1x = (int) (px + smoothed[peak1] * Math.cos(r1));
        int e1y = (int) (py + smoothed[peak1] * Math.sin(r1));
        Imgproc.circle(base, new Point(e1x, e1y), 6, new Scalar(255, 255, 255), -1);
        Imgproc.putText(base, "P1", new Point(e1x + 5, e1y), Imgproc.FONT_HERSHEY_SIMPLEX, 0.5,
                new Scalar(255, 255, 255), 1);

        return base;
    }

    private static Mat visualizeProfile(Mat base, int px, int py, double[] profile) {
        // Desenha os raios com comprimento proporcional à resposta
        for (int i = 0; i < N_RAYS; i++) {
            if (profile[i] < 5)
                continue;
            double rad = 2 * Math.PI * i / N_RAYS;
            int ex = (int) (px + profile[i] * Math.cos(rad));
            int ey = (int) (py + profile[i] * Math.sin(rad));
            Imgproc.line(base, new Point(px, py), new Point(ex, ey),
                    new Scalar(0, 180, 255), 1);
        }
        return base;
    }

    private static void drawRayHand(Mat img, int px, int py,
            double clockAngle, double len, Scalar color, String label) {
        // Converte ângulo do relógio de volta para vetor de imagem
        double rad = Math.toRadians(clockAngle - 90); // inverso de rayIndexToClockAngle
        int tipX = (int) (px + len * Math.cos(rad));
        int tipY = (int) (py + len * Math.sin(rad));
        Imgproc.line(img, new Point(px, py), new Point(tipX, tipY), color, 3);
        Imgproc.putText(img, label, new Point(tipX, tipY),
                Imgproc.FONT_HERSHEY_SIMPLEX, 1, color, 2);
    }

    private static void notifyListener(ProcessingStepListener listener, String stepName, Mat mat) {
        if (listener == null)
            return;
        Mat toConvert = mat;
        if (mat.channels() == 1) {
            toConvert = new Mat();
            Imgproc.cvtColor(mat, toConvert, Imgproc.COLOR_GRAY2BGR);
        }
        listener.onStepCompleted(stepName, matToBufferedImage(toConvert));
    }

    private static Mat resizeImage(Mat img) {
        int h = img.rows(), w = img.cols();
        double scale = 1000.0 / Math.max(h, w);
        Mat resized = new Mat();
        Imgproc.resize(img, resized, new Size((int) (w * scale), (int) (h * scale)));
        return resized;
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

    private static BufferedImage matToBufferedImage(Mat m) {
        int type = m.channels() > 1 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        m.get(0, 0, data);
        return image;
    }
}
