package pdi.pablokaue.filters;

import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChallengeFilters {

    public static String readClock(BufferedImage img, ProcessingStepListener listener) {
        try {
            nu.pattern.OpenCV.loadLocally();
        } catch (UnsatisfiedLinkError e) {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }

        // Step 1: Pré-processar imagem (resize + contraste + blur)
        Mat src = bufferedImageToMat(img);
        src = resizeImage(src);
        notifyListener(listener, "1.1 Resize", src);

        Mat hsv = new Mat();
        Imgproc.cvtColor(src, hsv, Imgproc.COLOR_BGR2HSV);
        Core.bitwise_not(hsv, hsv);
        notifyListener(listener, "1.2 HSV (Invertido)", hsv);

        // CLAHE no canal V
        List<Mat> channels = new ArrayList<>();
        Core.split(hsv, channels);
        CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
        clahe.apply(channels.get(2), channels.get(2));
        Core.merge(channels, hsv);
        notifyListener(listener, "1.3 CLAHE (Canal V)", hsv);

        Mat vChannel = channels.get(2);
        Mat thresh = new Mat();
        Imgproc.threshold(vChannel, thresh, 0, 255, Imgproc.THRESH_BINARY | Imgproc.THRESH_OTSU);
        notifyListener(listener, "1.4 Threshold (Otsu)", thresh);

        Mat blurred = new Mat();
        Imgproc.GaussianBlur(thresh, blurred, new Size(5, 5), 0);
        notifyListener(listener, "1.5 Blur Gaussiano", blurred);

        // Step 2: Detectar o círculo do relógio e refinar para a bolinha central
        int[] clock = clockDetection(src, blurred);
        int centerX = clock[0], centerY = clock[1], radius = clock[2];

        // Visualizar círculo e centro real detectado
        Mat circleViz = src.clone();
        Imgproc.circle(circleViz, new Point(centerX, centerY), radius, new Scalar(0, 255, 0), 2);
        Imgproc.circle(circleViz, new Point(centerX, centerY), 4, new Scalar(0, 0, 255), -1);
        notifyListener(listener, "2. Círculo e Pivô Detectados", circleViz);

        // Step 3: Detectar linhas
        List<int[]> lines = lineDetection(blurred);

        if (lines == null || lines.isEmpty()) return "00:00:00";

        // Visualizar linhas detectadas
        Mat linesViz = src.clone();
        for (int[] l : lines) {
            Imgproc.line(linesViz, new Point(l[0], l[1]), new Point(l[2], l[3]), new Scalar(0, 0, 255), 1);
        }
        notifyListener(listener, "3. Linhas Detectadas", linesViz);

        // Step 4: Agrupar linhas próximas e paralelas
        List<LineGroup> groups = groupLinesDetection(lines, centerX, centerY, radius);

        // Visualizar grupos (cores diferentes)
        Mat groupsViz = src.clone();
        Scalar[] colors = {new Scalar(255, 0, 0), new Scalar(0, 255, 0), new Scalar(0, 0, 255), new Scalar(255, 255, 0)};
        int cIdx = 0;
        for (LineGroup g : groups) {
            Scalar c = colors[cIdx % colors.length];
            for (int[] l : g.lines) {
                Imgproc.line(groupsViz, new Point(l[0], l[1]), new Point(l[2], l[3]), c, 2);
            }
            cIdx++;
        }
        notifyListener(listener, "4. Grupos de Linhas", groupsViz);

        // Step 5: Detectar os ponteiros
        List<Hand> hands = handsDetection(groups, centerX, centerY);
        System.out.println(hands);
        if (hands.size() < 2) return "00:00:00";

        // Step 6: Identificar hora, minuto e segundo
        Hand[] identified = getHands(hands);
        Hand hourHand   = identified[0];
        Hand minuteHand = identified[1];
        Hand secondHand = identified[2];

        // Visualizar ponteiros identificados
        Mat handsViz = src.clone();
        if (hourHand   != null) drawHand(handsViz, hourHand,   new Scalar(255, 0, 0),   "H");
        if (minuteHand != null) drawHand(handsViz, minuteHand, new Scalar(0, 255, 0),   "M");
        if (secondHand != null) drawHand(handsViz, secondHand, new Scalar(0, 0, 255),   "S");
        notifyListener(listener, "5-6. Ponteiros Id.", handsViz);

        // Step 7: Calcular ângulos baseados no centro refinado
        double hourAngle   = hourHand   != null ? getAngle(hourHand.line,   centerX, centerY) : 0;
        double minuteAngle = minuteHand != null ? getAngle(minuteHand.line, centerX, centerY) : 0;
        double secondAngle = secondHand != null ? getAngle(secondHand.line, centerX, centerY) : 0;

        // Step 8: Calcular horário
        return getTime(hourAngle, minuteAngle, secondAngle);
    }

    private static void notifyListener(ProcessingStepListener listener, String stepName, Mat mat) {
        if (listener != null) {
            Mat toConvert = mat;
            if (mat.channels() == 1) {
                toConvert = new Mat();
                Imgproc.cvtColor(mat, toConvert, Imgproc.COLOR_GRAY2BGR);
            }
            BufferedImage resultImg = matToBufferedImage(toConvert);
            listener.onStepCompleted(stepName, resultImg);
        }
    }

    private static void drawHand(Mat img, Hand hand, Scalar color, String label) {
        Imgproc.line(img, new Point(hand.line[0], hand.line[1]), new Point(hand.line[2], hand.line[3]), color, 3);
        Imgproc.putText(img, label, new Point(hand.line[0], hand.line[1]), Imgproc.FONT_HERSHEY_SIMPLEX, 1, color, 2);
    }

    private static Mat resizeImage(Mat img) {
        int h = img.rows(), w = img.cols();
        double scale = 1000.0 / Math.max(h, w);
        Mat resized = new Mat();
        Imgproc.resize(img, resized, new Size((int) (w * scale), (int) (h * scale)));
        return resized;
    }

    // -------------------------------------------------------------------------
    // Detecta o círculo externo e refina buscando a bolinha central (pivô)
    // -------------------------------------------------------------------------
    private static int[] clockDetection(Mat img, Mat blurred) {
        Mat circles = new Mat();
        Imgproc.HoughCircles(blurred, circles, Imgproc.HOUGH_GRADIENT,
                1, 400, 50, 100, 100, 500);

        int cx = 0, cy = 0, r = 0;
        boolean foundCircle = false;

        if (circles.cols() > 0) {
            double[] best = null;
            double maxR = 0;
            for (int i = 0; i < circles.cols(); i++) {
                double[] c = circles.get(0, i);
                if (c[2] > maxR) { maxR = c[2]; best = c; }
            }
            cx = (int) best[0];
            cy = (int) best[1];
            r  = (int) best[2];
            foundCircle = true;
        }

        if (!foundCircle) {
            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(blurred.clone(), contours, new Mat(),
                    Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);
            double maxArea = 0;
            Rect maxRect = new Rect(0, 0, img.cols(), img.rows());
            for (MatOfPoint c : contours) {
                double area = Imgproc.contourArea(c);
                if (area > maxArea) { maxArea = area; maxRect = Imgproc.boundingRect(c); }
            }
            cx = maxRect.x + maxRect.width / 2;
            cy = maxRect.y + maxRect.height / 2;
            r  = Math.min(maxRect.width, maxRect.height) / 2;
        }

        // --- Refinamento do centro: Foco na bolinha central ---
        int roiSize = (int) (r * 0.15);
        int roiX = Math.max(0, cx - roiSize);
        int roiY = Math.max(0, cy - roiSize);
        int roiW = Math.min(img.cols() - roiX, roiSize * 2);
        int roiH = Math.min(img.rows() - roiY, roiSize * 2);

        Rect roiRect = new Rect(roiX, roiY, roiW, roiH);
        Mat roi = new Mat(blurred, roiRect);

        List<MatOfPoint> centerContours = new ArrayList<>();
        Imgproc.findContours(roi, centerContours, new Mat(), Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        double minDistance = Double.MAX_VALUE;
        Point trueCenter = new Point(cx, cy);

        for (MatOfPoint cc : centerContours) {
            Moments moments = Imgproc.moments(cc);
            if (moments.m00 > 0) {
                int ccx = (int) (moments.m10 / moments.m00) + roiX;
                int ccy = (int) (moments.m01 / moments.m00) + roiY;

                double dist = Math.hypot(ccx - cx, ccy - cy);
                if (dist < minDistance && Imgproc.contourArea(cc) > 5) {
                    minDistance = dist;
                    trueCenter = new Point(ccx, ccy);
                }
            }
        }

        return new int[]{ (int) trueCenter.x, (int) trueCenter.y, r };
    }

    // -------------------------------------------------------------------------
    // CORRIGIDO: Hough relaxado (minLineLength menor, maxLineGap maior) para
    // capturar o ponteiro fino de minutos inteiro, em vez de fragmentos curtos.
    // -------------------------------------------------------------------------
    private static List<int[]> lineDetection(Mat blurred) {
        Mat edges = new Mat();
        Imgproc.Canny(blurred, edges, 50, 150);

        Mat linesRaw = new Mat();
        // threshold=60, minLineLength=20, maxLineGap=15
        Imgproc.HoughLinesP(edges, linesRaw, 1, Math.PI / 180, 60, 20, 15);

        List<int[]> lines = new ArrayList<>();
        for (int i = 0; i < linesRaw.rows(); i++) {
            double[] d = linesRaw.get(i, 0);
            lines.add(new int[]{ (int) d[0], (int) d[1], (int) d[2], (int) d[3] });
        }
        return lines;
    }

    // -------------------------------------------------------------------------
    // CORRIGIDO (Bug 2): filtro agora usa distância ao centro em vez de
    // descartar linhas longas, para não eliminar o ponteiro de minutos.
    // -------------------------------------------------------------------------
    private static List<LineGroup> groupLinesDetection(
            List<int[]> lines, int cx, int cy, int radius) {

        List<LineGroup> groups = new ArrayList<>();

        for (int[] line : lines) {
            int x1 = line[0], y1 = line[1], x2 = line[2], y2 = line[3];

            double len1 = Math.hypot(x1 - cx, y1 - cy);
            double len2 = Math.hypot(x2 - cx, y2 - cy);
            double maxLen = Math.max(len1, len2);

            // Descarta linhas que ultrapassam muito o raio (prováveis bordas)
            if (maxLen >= radius * 1.05) continue;

            // Descarta linhas que não passam perto do centro (não são ponteiros)
            double distToCenter = pointToLineDist(cx, cy, x1, y1, x2, y2);
            if (distToCenter > radius * 0.15) continue;

            double angle = Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));

            boolean grouped = false;
            for (LineGroup g : groups) {
                double diff = Math.abs(angle - g.meanAngle);
                if (diff < 12 || Math.abs(diff - 180) < 12) {
                    g.lines.add(line);
                    g.meanAngle = g.lines.stream()
                            .mapToDouble(ln -> Math.toDegrees(Math.atan2(ln[3]-ln[1], ln[2]-ln[0])))
                            .average()
                            .orElse(g.meanAngle);
                    grouped = true;
                    break;
                }
            }
            if (!grouped) {
                LineGroup ng = new LineGroup();
                ng.lines.add(line);
                ng.meanAngle = angle;
                groups.add(ng);
            }
        }
        return groups;
    }

    // Distância de um ponto (px, py) a um segmento de reta definido por (x1,y1)→(x2,y2)
    private static double pointToLineDist(int px, int py, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return Math.hypot(px - x1, py - y1);
        return Math.abs(dy * px - dx * py + (double) x2 * y1 - (double) y2 * x1) / len;
    }

    // -------------------------------------------------------------------------
    // CORRIGIDO: a ponta do ponteiro é a EXTREMIDADE REAL mais distante do
    // centro (como no original) — NÃO projetada pela meanAngle, que quebra
    // para ponteiros horizontais (média de atan2 sofre wraparound em ±180°).
    // O eixo para medir a espessura vem da própria direção centro→ponta.
    // -------------------------------------------------------------------------
    private static List<Hand> handsDetection(List<LineGroup> groups, int cx, int cy) {
        List<Hand> hands = new ArrayList<>();

        for (LineGroup group : groups) {
            List<int[]> lines = group.lines;
            if (lines.isEmpty()) continue;

            // 1) Ponta real = extremidade mais distante do centro
            double maxDist = 0;
            int tipX = cx, tipY = cy;
            for (int[] l : lines) {
                double dS = Math.hypot(l[0] - cx, l[1] - cy);
                double dE = Math.hypot(l[2] - cx, l[3] - cy);
                if (dS > maxDist) { maxDist = dS; tipX = l[0]; tipY = l[1]; }
                if (dE > maxDist) { maxDist = dE; tipX = l[2]; tipY = l[3]; }
            }
            if (maxDist < 1) continue;

            // Linha vai da ponta REAL até o centro
            int[] bestLine = { tipX, tipY, cx, cy };

            // 2) Eixo do ponteiro = direção centro→ponta (não depende de meanAngle)
            double dirX = (tipX - cx) / maxDist;
            double dirY = (tipY - cy) / maxDist;
            double perpX = -dirY, perpY = dirX;

            // 3) Espessura robusta: spread das projeções no eixo perpendicular
            List<Double> projs = new ArrayList<>();
            for (int[] l : lines) {
                projs.add(l[0] * perpX + l[1] * perpY);
                projs.add(l[2] * perpX + l[3] * perpY);
            }
            double thickness = robustSpread(projs);
            if (thickness < 1.0) thickness = 1.0;

            double handLength = maxDist; // distância da ponta ao centro
            hands.add(new Hand(bestLine, thickness, handLength));
        }

        // Ordena do maior comprimento (minutos) para o menor (horas)
        hands.sort((a, b) -> Double.compare(b.length, a.length));
        if (hands.size() > 3) hands = hands.subList(0, 3);
        return hands;
    }

    // Largura robusta de um conjunto de projeções: percentil 90 - percentil 10.
    // Menos sensível a uma linha intrusa do que pegar o (max - min).
    private static double robustSpread(List<Double> values) {
        if (values.size() < 2) return 0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        int loIdx = (int) Math.floor(0.10 * (n - 1));
        int hiIdx = (int) Math.ceil(0.90 * (n - 1));
        return sorted.get(hiIdx) - sorted.get(loIdx);
    }

    // -------------------------------------------------------------------------
    // CORRIGIDO (inversão H/M): o desempate por ângulo (múltiplos de 6°/30°)
    // foi substituído pela ESPESSURA — o ponteiro de horas é mais grosso que
    // o de minutos. Isso é um discriminador físico confiável, ao contrário do
    // ângulo, que falha quando os ponteiros estão próximos.
    // -------------------------------------------------------------------------
    private static Hand[] getHands(List<Hand> hands) {
        if (hands.size() < 2) return new Hand[]{ hands.get(0), null, null };

        List<Hand> list = new ArrayList<>(hands);
        Hand secondHand = null;

        // Isola o ponteiro de segundos: o mais fino, se for nitidamente mais fino.
        if (list.size() >= 3) {
            list.sort((a, b) -> Double.compare(a.thickness, b.thickness));
            double thinnest       = list.get(0).thickness;
            double secondThinnest = list.get(1).thickness;
            if (secondThinnest / Math.max(thinnest, 0.1) > 1.5) {
                secondHand = list.remove(0);
            } else {
                // Fallback: se a espessura não separar bem, usa o comprimento.
                // (o ponteiro de segundos costuma ser o mais longo)
                list.sort((a, b) -> Double.compare(b.length, a.length));
                secondHand = list.remove(0);
            }
        }

        // Restam 2 ponteiros: horas e minutos.
        // Critério primário: comprimento (minutos é mais longo, horas mais curto).
        list.sort((a, b) -> Double.compare(a.length, b.length));
        Hand hourHand   = list.get(0);                 // mais curto
        Hand minuteHand = list.get(list.size() - 1);   // mais longo

        // Desempate quando os comprimentos são parecidos (< 20% de diferença):
        // usa a ESPESSURA — horas é mais grosso, minutos é mais fino.
        if (list.size() == 2) {
            double lenRatio = hourHand.length / Math.max(minuteHand.length, 0.1);
            if (lenRatio > 0.80) {
                Hand thicker = list.get(0).thickness >= list.get(1).thickness
                        ? list.get(0) : list.get(1);
                Hand thinner = (thicker == list.get(0)) ? list.get(1) : list.get(0);
                hourHand   = thicker;
                minuteHand = thinner;
            }
        }

        return new Hand[]{ hourHand, minuteHand, secondHand };
    }

    // -------------------------------------------------------------------------
    // CORRIGIDO (Bug 1): vetor calculado de center→tip (ponta do ponteiro),
    // não de tip→center como estava antes.
    // -------------------------------------------------------------------------
    private static double getAngle(int[] hand, int cx, int cy) {
        if (hand == null) return 0;
        // hand[0,1] = ponta do ponteiro; hand[2,3] = centro
        // Vetor deve ir do centro até a ponta:
        double[] u = { hand[0] - cx, hand[1] - cy };
        double[] v = { 0, -100 }; // referência: 12h = vetor apontando para cima

        double dot  = u[0] * v[0] + u[1] * v[1];
        double lenU = Math.sqrt(u[0] * u[0] + u[1] * u[1]);
        double lenV = Math.sqrt(v[0] * v[0] + v[1] * v[1]);
        double cos  = Math.max(-1.0, Math.min(1.0, dot / (lenU * lenV)));
        double theta = Math.toDegrees(Math.acos(cos));

        double cross = u[0] * v[1] - u[1] * v[0];
        return cross > 0 ? 360 - theta : theta;
    }

    private static String getTime(double hourAngle, double minuteAngle, double secondAngle) {
        // Minutos e segundos: divisão direta do ângulo
        int minute = (int) Math.round(minuteAngle / 6.0);
        if (minute == 60) minute = 0;

        int second = (int) Math.round(secondAngle / 6.0);
        if (second == 60) second = 0;

        // -------------------------------------------------------------------------
        // Correção de hora por zona (usando os minutos como âncora):
        //
        // O ponteiro de horas percorre 30° por hora E 0,5° por minuto.
        // Removemos a contribuição dos minutos do ângulo bruto antes de arredondar:
        //   hora_real = round(hourAngle/30 - minute/60.0)
        // -------------------------------------------------------------------------
        double hourRaw = hourAngle / 30.0;
        double minuteContrib = minute / 60.0;
        double hourCorrected = hourRaw - minuteContrib;

        int hour = (int) Math.round(hourCorrected);
        if (hour < 0)  hour += 12;
        if (hour == 0) hour  = 12;
        if (hour > 12) hour -= 12;

        return String.format("%02d:%02d:%02d", hour, minute, second);
    }

    private static double distanceBetweenParallelLines(int[] l1, int[] l2) {
        double[] v1 = { l1[2] - l1[0], l1[3] - l1[1] };
        double[] vb = { l2[0] - l1[0], l2[1] - l1[1] };
        double cross = Math.abs(v1[0] * vb[1] - v1[1] * vb[0]);
        double norm  = Math.sqrt(v1[0] * v1[0] + v1[1] * v1[1]);
        return norm == 0 ? 0 : cross / norm;
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
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);
        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        m.get(0, 0, data);
        return image;
    }

    private static class LineGroup {
        List<int[]> lines = new ArrayList<>();
        double meanAngle  = 0;
    }

    private static class Hand {
        int[]  line;
        double thickness;
        double length;

        Hand(int[] line, double thickness, double length) {
            this.line      = line;
            this.thickness = thickness;
            this.length    = length;
        }

        @Override
        public String toString() {
            return "Hand{length=" + length + ", thickness=" + thickness + "}";
        }
    }
}