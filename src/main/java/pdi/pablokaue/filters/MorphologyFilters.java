package pdi.pablokaue.filters;

import java.awt.image.BufferedImage;

public class MorphologyFilters {

    public static BufferedImage aplicarNVezes(BufferedImage img, boolean[][] se, int n, boolean erode) {
        BufferedImage cur = img;
        for (int i = 0; i < n; i++)
            cur = erode ? erode(cur, se) : dilate(cur, se);
        return cur;
    }

    public static boolean[][] getStructuringElement(String key) {
        return switch (key) {
            case "DISCO" -> new boolean[][] {
                    {false, true,  true,  true,  false},
                    {true,  true,  true,  true,  true },
                    {true,  true,  true,  true,  true },
                    {true,  true,  true,  true,  true },
                    {false, true,  true,  true,  false}
            };
            case "CRUZ" -> new boolean[][] {
                    {false, false, true,  false, false},
                    {false, false, true,  false, false},
                    {true,  true,  true,  true,  true },
                    {false, false, true,  false, false},
                    {false, false, true,  false, false}
            };
            case "QUADRADO" -> new boolean[][] {
                    {true, true, true, true, true},
                    {true, true, true, true, true},
                    {true, true, true, true, true},
                    {true, true, true, true, true},
                    {true, true, true, true, true}
            };
            case "HEXAGONO" -> new boolean[][] {
                    {false, true,  true,  true,  false},
                    {true,  true,  true,  true,  true },
                    {true,  true,  true,  true,  true },
                    {true,  true,  true,  true,  true },
                    {false, true,  true,  true,  false}
            };
            case "LINHA" -> new boolean[][] {
                    {false, false, false, false, false},
                    {false, false, false, false, false},
                    {true,  true,  true,  true,  true },
                    {false, false, false, false, false},
                    {false, false, false, false, false}
            };
            case "PONTOS" -> new boolean[][] {
                    {false, false, false, false, false},
                    {false, false, false, false, false},
                    {false, false, true,  false, true },
                    {false, false, false, false, false},
                    {false, false, false, false, false}
            };
            default -> new boolean[][]{{true}};
        };
    }

    public static BufferedImage erode(BufferedImage src, boolean[][] se) {
        int w = src.getWidth(), h = src.getHeight();
        int kr = se.length / 2, kc = se[0].length / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean fit = true;
                outer:
                for (int ky = 0; ky < se.length && fit; ky++) {
                    for (int kx = 0; kx < se[0].length && fit; kx++) {
                        if (!se[ky][kx]) continue;
                        int nx = x + kx - kc;
                        int ny = y + ky - kr;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                            fit = false;
                        } else {
                            int lum = (src.getRGB(nx, ny) >> 16) & 0xFF;
                            if (lum < 128) fit = false;
                        }
                    }
                }
                int v = fit ? 255 : 0;
                int a = (src.getRGB(x, y) >> 24) & 0xFF;
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }

    public static BufferedImage dilate(BufferedImage src, boolean[][] se) {
        int w = src.getWidth(), h = src.getHeight();
        int kr = se.length / 2, kc = se[0].length / 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                boolean hit = false;
                outer:
                for (int ky = 0; ky < se.length; ky++) {
                    for (int kx = 0; kx < se[0].length; kx++) {
                        if (!se[ky][kx]) continue;
                        int nx = x + kx - kc;
                        int ny = y + ky - kr;
                        if (nx < 0 || nx >= w || ny < 0 || ny >= h) continue;
                        int lum = (src.getRGB(nx, ny) >> 16) & 0xFF;
                        if (lum >= 128) { hit = true; break outer; }
                    }
                }
                int v = hit ? 255 : 0;
                int a = (src.getRGB(x, y) >> 24) & 0xFF;
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | v);
            }
        }
        return out;
    }
}
