package pdi.pablokaue.filters;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ThinningFilters {

    private static int[][] getBinaryMatrix(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[][] bin = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int lum = (int)(0.299 * r + 0.587 * g + 0.114 * b);
                bin[y][x] = lum >= 128 ? 1 : 0;
            }
        }
        return bin;
    }

    private static BufferedImage fromBinaryMatrix(int[][] bin, BufferedImage src) {
        int h = bin.length, w = bin[0].length;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = (src.getRGB(x, y) >> 24) & 0xFF;
                int v = bin[y][x] == 1 ? 255 : 0;
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    private static int getP(int[][] bin, int x, int y, int w, int h, int p) {
        switch (p) {
            case 2: return (y - 1 >= 0) ? bin[y - 1][x] : 0;
            case 3: return (y - 1 >= 0 && x + 1 < w) ? bin[y - 1][x + 1] : 0;
            case 4: return (x + 1 < w) ? bin[y][x + 1] : 0;
            case 5: return (y + 1 < h && x + 1 < w) ? bin[y + 1][x + 1] : 0;
            case 6: return (y + 1 < h) ? bin[y + 1][x] : 0;
            case 7: return (y + 1 < h && x - 1 >= 0) ? bin[y + 1][x - 1] : 0;
            case 8: return (x - 1 >= 0) ? bin[y][x - 1] : 0;
            case 9: return (y - 1 >= 0 && x - 1 >= 0) ? bin[y - 1][x - 1] : 0;
            default: return 0;
        }
    }

    private static int getTransitions(int[][] bin, int x, int y, int w, int h) {
        int count = 0;
        int[] p = new int[10];
        for (int i = 2; i <= 9; i++) p[i] = getP(bin, x, y, w, h, i);
        for (int i = 2; i <= 9; i++) {
            int next = (i == 9) ? 2 : i + 1;
            if (p[i] == 0 && p[next] == 1) {
                count++;
            }
        }
        return count;
    }

    private static int getNeighborsSum(int[][] bin, int x, int y, int w, int h) {
        int sum = 0;
        for (int i = 2; i <= 9; i++) sum += getP(bin, x, y, w, h, i);
        return sum;
    }

    public static BufferedImage zhangSuen(BufferedImage src) {
        int[][] bin = getBinaryMatrix(src);
        int h = bin.length, w = bin[0].length;
        boolean hasChanged;

        do {
            hasChanged = false;
            List<Point> toDelete = new ArrayList<>();

            // Sub-iteração 1
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (bin[y][x] == 1) {
                        int a = getTransitions(bin, x, y, w, h);
                        int b = getNeighborsSum(bin, x, y, w, h);
                        int p2 = getP(bin, x, y, w, h, 2);
                        int p4 = getP(bin, x, y, w, h, 4);
                        int p6 = getP(bin, x, y, w, h, 6);
                        int p8 = getP(bin, x, y, w, h, 8);

                        // Mantém endpoints (b >= 2) e não quebra conexões (a == 1)
                        if (b >= 2 && b <= 6 && a == 1 && (p2 * p4 * p6 == 0) && (p4 * p6 * p8 == 0)) {
                            toDelete.add(new Point(x, y));
                        }
                    }
                }
            }
            for (Point p : toDelete) {
                bin[p.y][p.x] = 0;
                hasChanged = true;
            }
            toDelete.clear();

            // Sub-iteração 2
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (bin[y][x] == 1) {
                        int a = getTransitions(bin, x, y, w, h);
                        int b = getNeighborsSum(bin, x, y, w, h);
                        int p2 = getP(bin, x, y, w, h, 2);
                        int p4 = getP(bin, x, y, w, h, 4);
                        int p6 = getP(bin, x, y, w, h, 6);
                        int p8 = getP(bin, x, y, w, h, 8);

                        if (b >= 2 && b <= 6 && a == 1 && (p2 * p4 * p8 == 0) && (p2 * p6 * p8 == 0)) {
                            toDelete.add(new Point(x, y));
                        }
                    }
                }
            }
            for (Point p : toDelete) {
                bin[p.y][p.x] = 0;
                hasChanged = true;
            }

        } while (hasChanged);

        return fromBinaryMatrix(bin, src);
    }

    public static BufferedImage holt(BufferedImage src) {
        int[][] bin = getBinaryMatrix(src);
        int h = bin.length, w = bin[0].length;
        boolean hasChanged;

        do {
            hasChanged = false;
            List<Point> toDelete = new ArrayList<>();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (bin[y][x] == 1) {
                        int a = getTransitions(bin, x, y, w, h);
                        int b = getNeighborsSum(bin, x, y, w, h);
                        int p2 = getP(bin, x, y, w, h, 2);
                        int p4 = getP(bin, x, y, w, h, 4);
                        int p6 = getP(bin, x, y, w, h, 6);
                        int p8 = getP(bin, x, y, w, h, 8);

                        boolean edge1 = (p2 * p4 * p6 == 0) || (getTransitions(bin, x, y - 1, w, h) != 1);
                        boolean edge2 = (p2 * p4 * p8 == 0) || (getTransitions(bin, x + 1, y, w, h) != 1);

                        // Mantém endpoints (b >= 2) e não quebra conexões (a == 1)
                        if (b >= 2 && b <= 6 && a == 1 && edge1 && edge2) {
                            toDelete.add(new Point(x, y));
                        }
                    }
                }
            }
            for (Point p : toDelete) {
                bin[p.y][p.x] = 0;
                hasChanged = true;
            }

        } while (hasChanged);

        return fromBinaryMatrix(bin, src);
    }

    public static BufferedImage stentiford(BufferedImage src) {
        int[][] bin = getBinaryMatrix(src);
        int h = bin.length, w = bin[0].length;
        boolean hasChanged;

        do {
            hasChanged = false;

            for (int step = 1; step <= 4; step++) {
                List<Point> toDelete = new ArrayList<>();
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (bin[y][x] == 1) {
                            int p2 = getP(bin, x, y, w, h, 2);
                            int p4 = getP(bin, x, y, w, h, 4);
                            int p6 = getP(bin, x, y, w, h, 6);
                            int p8 = getP(bin, x, y, w, h, 8);

                            boolean match = false;
                            if (step == 1 && p2 == 0 && p6 == 1) match = true;
                            if (step == 2 && p8 == 0 && p4 == 1) match = true;
                            if (step == 3 && p6 == 0 && p2 == 1) match = true;
                            if (step == 4 && p4 == 0 && p8 == 1) match = true;

                            if (match) {
                                int a = getTransitions(bin, x, y, w, h);
                                int b = getNeighborsSum(bin, x, y, w, h);

                                // Nc = 1 (não quebra conexões) e B > 1 (não remove endpoints)
                                if (a == 1 && b > 1) {
                                    toDelete.add(new Point(x, y));
                                }
                            }
                        }
                    }
                }
                for (Point p : toDelete) {
                    bin[p.y][p.x] = 0;
                    hasChanged = true;
                }
            }

        } while (hasChanged);

        return fromBinaryMatrix(bin, src);
    }
}
