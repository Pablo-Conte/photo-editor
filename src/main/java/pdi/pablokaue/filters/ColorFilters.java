package pdi.pablokaue.filters;

import pdi.pablokaue.utils.ImageUtils;
import java.awt.image.BufferedImage;

public class ColorFilters {
    
    public static BufferedImage brilho(BufferedImage src, int delta) {
        int aj = (int)(delta*2.55);
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int rgb=src.getRGB(x,y), a=(rgb>>24)&0xFF;
            int r=ImageUtils.clamp(((rgb>>16)&0xFF)+aj);
            int g=ImageUtils.clamp(((rgb>> 8)&0xFF)+aj);
            int b=ImageUtils.clamp(( rgb     &0xFF)+aj);
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return out;
    }

    public static BufferedImage contraste(BufferedImage src, int delta) {
        int d=(int)(delta*2.55);
        double f=(259.0*(d+255))/(255.0*(259-d));
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int rgb=src.getRGB(x,y), a=(rgb>>24)&0xFF;
            int r=ImageUtils.clamp((int)(f*(((rgb>>16)&0xFF)-128)+128));
            int g=ImageUtils.clamp((int)(f*(((rgb>> 8)&0xFF)-128)+128));
            int b=ImageUtils.clamp((int)(f*(( rgb     &0xFF)-128)+128));
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return out;
    }

    public static BufferedImage threshold(BufferedImage src, int limiar) {
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int rgb=src.getRGB(x,y), a=(rgb>>24)&0xFF;
            int r=(rgb>>16)&0xFF, g=(rgb>>8)&0xFF, b=rgb&0xFF;
            int lum=(int)(0.299*r+0.587*g+0.114*b);
            int cor=lum>=limiar?255:0;
            out.setRGB(x,y,(a<<24)|(cor<<16)|(cor<<8)|cor);
        }
        return out;
    }
}
