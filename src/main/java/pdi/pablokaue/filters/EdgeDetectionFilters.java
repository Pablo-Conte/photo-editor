package pdi.pablokaue.filters;

import pdi.pablokaue.utils.ImageUtils;
import java.awt.image.BufferedImage;

public class EdgeDetectionFilters {

    public static BufferedImage robertsCross(BufferedImage src, int thresh) {
        int[][] g = ImageUtils.toGray(src);
        int h = g.length, w = g[0].length;
        double[][] mag = new double[h][w];
        for (int y = 0; y < h-1; y++)
            for (int x = 0; x < w-1; x++) {
                double gx = g[y][x]   - g[y+1][x+1];
                double gy = g[y][x+1] - g[y+1][x];
                mag[y][x] = Math.sqrt(gx*gx + gy*gy);
            }
        return ImageUtils.magnitudeToImage(mag, src, thresh);
    }

    public static BufferedImage sobel(BufferedImage src, int thresh) {
        int[][] g = ImageUtils.toGray(src);
        int h = g.length, w = g[0].length;
        double[][] mag = new double[h][w];
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++) {
                double gx = -g[y-1][x-1] + g[y-1][x+1]
                        -2*g[y][x-1] + 2*g[y][x+1]
                        -g[y+1][x-1] + g[y+1][x+1];
                double gy = -g[y-1][x-1] - 2*g[y-1][x] - g[y-1][x+1]
                        +g[y+1][x-1] + 2*g[y+1][x] + g[y+1][x+1];
                mag[y][x] = Math.sqrt(gx*gx + gy*gy);
            }
        return ImageUtils.magnitudeToImage(mag, src, thresh);
    }

    public static BufferedImage robinsonCompass(BufferedImage src, int thresh) {
        int[][] g = ImageUtils.toGray(src);
        int h = g.length, w = g[0].length;

        int[][][] masks = {
                {{-1,-2,-1},{0,0,0},{1,2,1}},
                {{0,-1,-2},{1,0,-1},{2,1,0}},
                {{1,0,-1},{2,0,-2},{1,0,-1}},
                {{2,1,0},{1,0,-1},{0,-1,-2}},
                {{1,2,1},{0,0,0},{-1,-2,-1}},
                {{0,1,2},{-1,0,1},{-2,-1,0}},
                {{-1,0,1},{-2,0,2},{-1,0,1}},
                {{-2,-1,0},{-1,0,1},{0,1,2}}
        };

        double[][] mag = new double[h][w];
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++) {
                double maxResp = 0;
                for (int[][] mask : masks) {
                    double resp = 0;
                    for (int ky=0; ky<3; ky++)
                        for (int kx=0; kx<3; kx++)
                            resp += mask[ky][kx] * g[y+ky-1][x+kx-1];
                    maxResp = Math.max(maxResp, Math.abs(resp));
                }
                mag[y][x] = maxResp;
            }
        return ImageUtils.magnitudeToImage(mag, src, thresh);
    }

    public static BufferedImage freiChen(BufferedImage src, double thresh) {
        int[][] g = ImageUtils.toGray(src);
        int h = g.length, w = g[0].length;
        double s2 = Math.sqrt(2);

        double[][][] masks = {
                {{  1, s2,  1},{  0,  0,  0},{ -1,-s2, -1}},
                {{  1,  0, -1},{ s2,  0,-s2},{  1,  0, -1}},
                {{  0,  1, s2},{ -1,  0,  1},{-s2, -1,  0}},
                {{ s2,  1,  0},{  1,  0, -1},{  0, -1,-s2}}
        };

        double[] norms = new double[4];
        for (int i = 0; i < 4; i++) {
            double n = 0;
            for (double[] row : masks[i]) for (double v : row) n += v*v;
            norms[i] = Math.sqrt(n);
        }

        double[][] mag = new double[h][w];
        for (int y = 1; y < h-1; y++)
            for (int x = 1; x < w-1; x++) {
                double[] janela = new double[9];
                double normJanela = 0;
                int idx = 0;
                for (int ky=-1; ky<=1; ky++)
                    for (int kx=-1; kx<=1; kx++) {
                        double v = g[y+ky][x+kx];
                        janela[idx++] = v;
                        normJanela += v*v;
                    }
                normJanela = Math.sqrt(normJanela) + 1e-10;

                double soma = 0;
                for (int i = 0; i < 4; i++) {
                    double dot = 0; idx = 0;
                    for (int ky=0; ky<3; ky++)
                        for (int kx=0; kx<3; kx++)
                            dot += (masks[i][ky][kx]/norms[i]) * janela[idx++];
                    soma += dot*dot;
                }
                mag[y][x] = Math.sqrt(soma) / normJanela;
            }

        return ImageUtils.magnitudeToImage(mag, src, thresh * 255.0);
    }

    public static BufferedImage marrHildreth(BufferedImage src, int ks, double sigma) {
        BufferedImage suavizada = LowPassFilters.gaussiano(src, ks, sigma);
        int[][] g = ImageUtils.toGray(suavizada);
        int h = g.length, w = g[0].length;

        double[][] lap = new double[h][w];
        for (int y=1; y<h-1; y++)
            for (int x=1; x<w-1; x++)
                lap[y][x] = g[y-1][x] + g[y+1][x]
                        + g[y][x-1] + g[y][x+1]
                        - 4.0*g[y][x];

        double[][] mag = new double[h][w];
        for (int y=1; y<h-1; y++)
            for (int x=1; x<w-1; x++) {
                double v = lap[y][x];
                boolean crossing =
                        (v > 0 && (lap[y][x-1]<0 || lap[y][x+1]<0 ||
                                lap[y-1][x]<0 || lap[y+1][x]<0)) ||
                                (v < 0 && (lap[y][x-1]>0 || lap[y][x+1]>0 ||
                                        lap[y-1][x]>0 || lap[y+1][x]>0));
                if (crossing) {
                    double maxDiff = 0;
                    maxDiff = Math.max(maxDiff, Math.abs(v - lap[y][x-1]));
                    maxDiff = Math.max(maxDiff, Math.abs(v - lap[y][x+1]));
                    maxDiff = Math.max(maxDiff, Math.abs(v - lap[y-1][x]));
                    maxDiff = Math.max(maxDiff, Math.abs(v - lap[y+1][x]));
                    mag[y][x] = maxDiff;
                }
            }
        return ImageUtils.magnitudeToImage(mag, src, -1);
    }

    public static BufferedImage canny(BufferedImage src, double lowThresh, double highThresh) {
        int h = src.getHeight(), w = src.getWidth();

        BufferedImage suavizada = LowPassFilters.gaussiano(src, 5, 1.4);
        int[][] g = ImageUtils.toGray(suavizada);

        double[][] gx  = new double[h][w];
        double[][] gy  = new double[h][w];
        double[][] mag = new double[h][w];
        double[][] dir = new double[h][w];
        for (int y=1; y<h-1; y++)
            for (int x=1; x<w-1; x++) {
                gx[y][x] = -g[y-1][x-1] + g[y-1][x+1]
                        -2*g[y][x-1] + 2*g[y][x+1]
                        -g[y+1][x-1] + g[y+1][x+1];
                gy[y][x] = -g[y-1][x-1] - 2*g[y-1][x] - g[y-1][x+1]
                        +g[y+1][x-1] + 2*g[y+1][x] + g[y+1][x+1];
                mag[y][x] = Math.sqrt(gx[y][x]*gx[y][x] + gy[y][x]*gy[y][x]);
                dir[y][x] = Math.toDegrees(Math.atan2(gy[y][x], gx[y][x]));
                if (dir[y][x] < 0) dir[y][x] += 180;
            }

        double[][] nms = new double[h][w];
        for (int y=1; y<h-1; y++)
            for (int x=1; x<w-1; x++) {
                double angle = dir[y][x], m = mag[y][x], n1, n2;
                if      (angle < 22.5 || angle >= 157.5) { n1=mag[y][x-1];   n2=mag[y][x+1];   }
                else if (angle < 67.5)                   { n1=mag[y-1][x+1]; n2=mag[y+1][x-1]; }
                else if (angle < 112.5)                  { n1=mag[y-1][x];   n2=mag[y+1][x];   }
                else                                     { n1=mag[y-1][x-1]; n2=mag[y+1][x+1]; }
                nms[y][x] = (m >= n1 && m >= n2) ? m : 0;
            }

        double maxMag = 1e-10;
        for (double[] row : nms) for (double v : row) if (v > maxMag) maxMag = v;

        int[][] edges = new int[h][w];
        for (int y=0; y<h; y++)
            for (int x=0; x<w; x++) {
                double v = nms[y][x] / maxMag * 255.0;
                if      (v >= highThresh) edges[y][x] = 255;
                else if (v >= lowThresh)  edges[y][x] = 128;
                else                      edges[y][x] = 0;
            }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int y=1; y<h-1; y++)
                for (int x=1; x<w-1; x++)
                    if (edges[y][x] == 128) {
                        outer:
                        for (int dy=-1; dy<=1; dy++)
                            for (int dx=-1; dx<=1; dx++)
                                if (edges[y+dy][x+dx] == 255) {
                                    edges[y][x] = 255; changed = true; break outer;
                                }
                    }
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++)
            for (int x=0; x<w; x++) {
                int v = (edges[y][x] == 255) ? 255 : 0;
                int a = (src.getRGB(x,y)>>24)&0xFF;
                out.setRGB(x, y, (a<<24)|(v<<16)|(v<<8)|v);
            }
        return out;
    }
}
