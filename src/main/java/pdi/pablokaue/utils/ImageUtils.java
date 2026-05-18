package pdi.pablokaue.utils;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;

public class ImageUtils {
    public static int clamp(int v) {
        return Math.min(255, Math.max(0, v));
    }

    public static int clamp(float v) {
        return Math.min(255, Math.max(0, (int)v));
    }

    public static int[][] toGray(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int[][] gray = new int[h][w];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb = src.getRGB(x, y);
                gray[y][x] = (int)(0.299*((rgb>>16)&0xFF)
                        + 0.587*((rgb>> 8)&0xFF)
                        + 0.114*( rgb     &0xFF));
            }
        return gray;
    }

    public static BufferedImage magnitudeToImage(double[][] mag, BufferedImage src, double thresh) {
        int h = mag.length, w = mag[0].length;
        double maxVal = 1e-10;
        for (double[] row : mag) for (double v : row) if (v > maxVal) maxVal = v;

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double normalized = mag[y][x] / maxVal * 255.0;
                int v = (thresh >= 0)
                        ? (normalized >= thresh ? 255 : 0)
                        : clamp((int) normalized);
                int a = (src.getRGB(x,y)>>24)&0xFF;
                out.setRGB(x, y, (a<<24)|(v<<16)|(v<<8)|v);
            }
        return out;
    }

    public static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        WritableRaster r = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, r, cm.isAlphaPremultiplied(), null);
    }
}
