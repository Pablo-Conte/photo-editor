package pdi.pablokaue.filters;

import pdi.pablokaue.utils.ImageUtils;
import java.awt.image.BufferedImage;

public class LowPassFilters {
    
    public static BufferedImage convolucao(BufferedImage src, float[][] kernel) {
        int w = src.getWidth(), h = src.getHeight();
        int ks = kernel.length, kr = ks/2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                float sR=0, sG=0, sB=0;
                for (int ky=0; ky<ks; ky++) for (int kx=0; kx<ks; kx++) {
                    int nx = Math.max(0,Math.min(w-1,x+(kx-kr)));
                    int ny = Math.max(0,Math.min(h-1,y+(ky-kr)));
                    int rgb = src.getRGB(nx,ny);
                    float p = kernel[ky][kx];
                    sR += ((rgb>>16)&0xFF)*p;
                    sG += ((rgb>> 8)&0xFF)*p;
                    sB += ( rgb     &0xFF)*p;
                }
                int a = (src.getRGB(x,y)>>24)&0xFF;
                out.setRGB(x,y,(a<<24)|(ImageUtils.clamp(sR)<<16)|(ImageUtils.clamp(sG)<<8)|ImageUtils.clamp(sB));
            }
        return out;
    }

    public static BufferedImage gaussiano(BufferedImage src, int ks, double sigma) {
        if (ks%2==0) ks++;
        float[][] k = new float[ks][ks];
        int r = ks/2; float soma=0;
        for (int y=-r; y<=r; y++) for (int x=-r; x<=r; x++) {
            float v = (float)Math.exp(-(x*x+y*y)/(2*sigma*sigma));
            k[y+r][x+r] = v; soma+=v;
        }
        for (int y=0; y<ks; y++) for (int x=0; x<ks; x++) k[y][x]/=soma;
        return convolucao(src, k);
    }

    public static BufferedImage mediana(BufferedImage src, int ks) {
        int w=src.getWidth(), h=src.getHeight(), kr=ks/2, t=ks*ks;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int[] vR=new int[t], vG=new int[t], vB=new int[t], idx=new int[]{0};
            for (int ky=0; ky<ks; ky++) for (int kx=0; kx<ks; kx++) {
                int nx=Math.max(0,Math.min(w-1,x+(kx-kr)));
                int ny=Math.max(0,Math.min(h-1,y+(ky-kr)));
                int rgb=src.getRGB(nx,ny);
                vR[idx[0]]=(rgb>>16)&0xFF; vG[idx[0]]=(rgb>>8)&0xFF;
                vB[idx[0]++]=rgb&0xFF;
            }
            java.util.Arrays.sort(vR); java.util.Arrays.sort(vG); java.util.Arrays.sort(vB);
            int m=t/2, a=(src.getRGB(x,y)>>24)&0xFF;
            out.setRGB(x,y,(a<<24)|(vR[m]<<16)|(vG[m]<<8)|vB[m]);
        }
        return out;
    }

    public static BufferedImage moda(BufferedImage src, int ks) {
        int w=src.getWidth(), h=src.getHeight(), kr=ks/2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            java.util.HashMap<Integer,Integer> freq = new java.util.HashMap<>();
            for (int ky=0; ky<ks; ky++) for (int kx=0; kx<ks; kx++) {
                int nx=Math.max(0,Math.min(w-1,x+(kx-kr)));
                int ny=Math.max(0,Math.min(h-1,y+(ky-kr)));
                freq.merge(src.getRGB(nx,ny)&0x00FFFFFF, 1, Integer::sum);
            }
            int cor = freq.entrySet().stream()
                    .max(java.util.Map.Entry.comparingByValue()).get().getKey();
            out.setRGB(x,y,((src.getRGB(x,y)>>24)&0xFF)<<24|cor);
        }
        return out;
    }
}
