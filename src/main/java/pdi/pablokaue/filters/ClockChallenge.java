package pdi.pablokaue.filters;

import java.awt.image.BufferedImage;

public class ClockChallenge {

    /**
     * Resolve o Exercício 1: Relógio Analógico.
     * Tenta encontrar os ponteiros da hora e minuto na imagem já processada
     * pelos filtros do sistema e retornar o horário em formato digital.
     * @param img A imagem do relógio após passar pelos filtros (ex: Threshold + Afinamento).
     * @return String formatada no padrão "HH:MM".
     */
    public static String readClock(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        int cx = w / 2;
        int cy = h / 2;

        double[] maxDistByAngle = new double[360];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int gray = (r + g + b) / 3;

                // Como usamos os filtros do sistema (Threshold -> Afinamento),
                // a imagem já está binarizada e os ponteiros são os pixels escuros (próximos de 0).
                if (gray < 128) {
                    double dx = x - cx;
                    double dy = y - cy;
                    double dist = Math.sqrt(dx * dx + dy * dy);

                    if (dist > 10) {
                        double angleRad = Math.atan2(dy, dx);
                        double angleDeg = Math.toDegrees(angleRad);

                        double clockAngle = (angleDeg + 90) % 360;
                        if (clockAngle < 0) {
                            clockAngle += 360;
                        }

                        int a = (int) Math.round(clockAngle) % 360;
                        if (dist > maxDistByAngle[a]) {
                            maxDistByAngle[a] = dist;
                        }
                    }
                }
            }
        }

        // Suavizar o histograma
        double[] smoothed = new double[360];
        int window = 5;
        for (int i = 0; i < 360; i++) {
            double sum = 0;
            for (int j = -window; j <= window; j++) {
                int idx = (i + j + 360) % 360;
                sum += maxDistByAngle[idx];
            }
            smoothed[i] = sum / (2 * window + 1);
        }

        // Maior pico = Minutos
        int minuteAngle = -1;
        double maxMinuteDist = -1;
        for (int i = 0; i < 360; i++) {
            if (smoothed[i] > maxMinuteDist) {
                maxMinuteDist = smoothed[i];
                minuteAngle = i;
            }
        }

        // Zerar a região dos minutos
        int excludeWindow = 20;
        for (int i = -excludeWindow; i <= excludeWindow; i++) {
            int idx = (minuteAngle + i + 360) % 360;
            smoothed[idx] = 0;
        }

        // Segundo maior pico = Horas
        int hourAngle = -1;
        double maxHourDist = -1;
        for (int i = 0; i < 360; i++) {
            if (smoothed[i] > maxHourDist) {
                maxHourDist = smoothed[i];
                hourAngle = i;
            }
        }

        int minute = (int) Math.round(minuteAngle / 6.0) % 60;

        int hour = (int) Math.floor(hourAngle / 30.0);
        if (hour == 0) hour = 12;

        return String.format("%02d:%02d", hour, minute);
    }
}
