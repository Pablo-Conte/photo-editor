package pdi.pablokaue.filters;

import pdi.pablokaue.utils.MathUtils;
import java.awt.image.BufferedImage;

public class GeometricFilters {
    
    public static BufferedImage aplicarTransformacao(BufferedImage src, double[][] matriz) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double[][] inv = MathUtils.inverterMatriz(matriz);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double[] o = MathUtils.multiplicarMatrizVetor(inv, new double[]{x, y, 1});
                int ox = (int) Math.round(o[0]);
                int oy = (int) Math.round(o[1]);
                if (ox >= 0 && ox < w && oy >= 0 && oy < h)
                    out.setRGB(x, y, src.getRGB(ox, oy));
            }
        return out;
    }

    public static BufferedImage transladar(BufferedImage src, int tx, int ty) {
        return aplicarTransformacao(src, new double[][]{{1,0,tx},{0,1,ty},{0,0,1}});
    }

    public static BufferedImage escalar(BufferedImage src, double sx, double sy) {
        return aplicarTransformacao(src, new double[][]{{sx,0,0},{0,sy,0},{0,0,1}});
    }

    public static BufferedImage rotacionar(BufferedImage src, double angulo) {
        double rad = Math.toRadians(angulo);
        double cos = Math.cos(rad), sen = Math.sin(rad);
        int cx = src.getWidth()/2, cy = src.getHeight()/2;
        double[][] m = MathUtils.multiplicarMatrizes(
                new double[][]{{1,0,cx},{0,1,cy},{0,0,1}},
                MathUtils.multiplicarMatrizes(
                        new double[][]{{cos,-sen,0},{sen,cos,0},{0,0,1}},
                        new double[][]{{1,0,-cx},{0,1,-cy},{0,0,1}}
                )
        );
        return aplicarTransformacao(src, m);
    }

    public static BufferedImage espelharHorizontal(BufferedImage src) {
        return aplicarTransformacao(src,
                new double[][]{{-1,0,src.getWidth()-1},{0,1,0},{0,0,1}});
    }

    public static BufferedImage espelharVertical(BufferedImage src) {
        return aplicarTransformacao(src,
                new double[][]{{1,0,0},{0,-1,src.getHeight()-1},{0,0,1}});
    }
}
