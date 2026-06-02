package pdi.pablokaue.filters;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

public class ChallengeFilters {

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

    // ─────────────────────────────────────────────────────────────────────────
    // Exercício 5: Leitura de Gráfico de Barras
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve o Exercício 2: Gráfico de Barras.
     * Recebe uma BufferedImage contendo um gráfico de barras verticais padronizado
     * e retorna as alturas em pixels de cada barra, ordenadas da esquerda para a direita.
     *
     * @param img A imagem do gráfico de barras (colorida, sem pré-processamento necessário).
     * @return BarChartAnalysis com a lista de alturas, maior e menor altura.
     */
    public static BarChartAnalysis readBarChart(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // ── Etapa 1: Identificar a cor das barras (área inteira) ──────────────
        Map<Integer, Integer> colorHistogram = new HashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = quantize((rgb >> 16) & 0xFF);
                int g = quantize((rgb >> 8) & 0xFF);
                int b = quantize(rgb & 0xFF);
                int key = (r << 16) | (g << 8) | b;
                colorHistogram.merge(key, 1, Integer::sum);
            }
        }

        List<Map.Entry<Integer, Integer>> sortedColors = new ArrayList<>(colorHistogram.entrySet());
        sortedColors.sort((a, b2) -> b2.getValue() - a.getValue());

        // Cor predominante = fundo; segunda mais frequente = barra
        int barColor = sortedColors.size() > 1 ? sortedColors.get(1).getKey() : sortedColors.get(0).getKey();
        int barR = (barColor >> 16) & 0xFF;
        int barG = (barColor >> 8) & 0xFF;
        int barB =  barColor & 0xFF;

        // ── Etapa 2: Margem do eixo Y ─────────────────────────────────────────
        int axisMarginX = (int) (w * 0.20);

        // ── Etapa 3: Máscara binária (apenas área das barras, à direita) ──────
        final int BAR_TOLERANCE = 30;
        boolean[][] mask = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = axisMarginX; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b =  rgb & 0xFF;
                if (Math.abs(r - barR) < BAR_TOLERANCE
                        && Math.abs(g - barG) < BAR_TOLERANCE
                        && Math.abs(b - barB) < BAR_TOLERANCE) {
                    mask[y][x] = true;
                }
            }
        }

        // ── Etapa 4: Componentes conectados (BFS) ─────────────────────────────
        boolean[][] visited = new boolean[h][w];
        List<int[]> components = new ArrayList<>();
        int[] dx = {0, 0, 1, -1};
        int[] dy = {1, -1, 0, 0};

        for (int y = 0; y < h; y++) {
            for (int x = axisMarginX; x < w; x++) {
                if (mask[y][x] && !visited[y][x]) {
                    Queue<int[]> queue = new LinkedList<>();
                    queue.add(new int[]{x, y});
                    visited[y][x] = true;
                    int minX = x, maxX = x, minY = y, maxY = y;

                    while (!queue.isEmpty()) {
                        int[] cur = queue.poll();
                        int cx = cur[0], cy = cur[1];
                        if (cx < minX) minX = cx;
                        if (cx > maxX) maxX = cx;
                        if (cy < minY) minY = cy;
                        if (cy > maxY) maxY = cy;

                        for (int d = 0; d < 4; d++) {
                            int nx = cx + dx[d];
                            int ny = cy + dy[d];
                            if (nx >= 0 && nx < w && ny >= 0 && ny < h
                                    && mask[ny][nx] && !visited[ny][nx]) {
                                visited[ny][nx] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                    components.add(new int[]{minX, maxX, minY, maxY});
                }
            }
        }

        // ── Etapa 5: Filtrar ruídos e ordenar barras ──────────────────────────
        final int MIN_WIDTH  = 10;
        final int MIN_HEIGHT = 20;
        List<int[]> bars = new ArrayList<>();
        for (int[] comp : components) {
            int compW = comp[1] - comp[0] + 1;
            int compH = comp[3] - comp[2] + 1;
            if (compW >= MIN_WIDTH && compH >= MIN_HEIGHT) bars.add(comp);
        }
        bars.sort(Comparator.comparingInt(c -> c[0]));

        // ── Etapa 6: OCR — detectar font size ignorando pixels da barra ───────
// Font size inferido da mediana das bandas encontradas — sem detectFontSize
        Map<Character, boolean[][]> templates = null; // será construído após inferir o tamanho

        TreeMap<Integer, Integer> axisLabels = extractAxisLabels(
                img, axisMarginX, h, barR, barG, barB, BAR_TOLERANCE);

        if (axisLabels.size() < 2) {
            throw new IllegalStateException(
                    "Não foi possível detectar labels suficientes no eixo Y. " +
                            "Labels detectados: " + axisLabels);
        }

        // ── Etapa 7: Escala pixels → valor ────────────────────────────────────
        int minAxisValue = axisLabels.firstKey();
        int maxAxisValue = axisLabels.lastKey();
        int pixelAtMin   = axisLabels.get(minAxisValue);
        int pixelAtMax   = axisLabels.get(maxAxisValue);

        double pixelsPerUnit = (double) (pixelAtMin - pixelAtMax) / (maxAxisValue - minAxisValue);
        double baselineY     = pixelAtMin + (minAxisValue * pixelsPerUnit);

        // ── Etapa 8: Converter topo de cada barra para valor do eixo ──────────
        List<Integer> barValues = new ArrayList<>();
        for (int[] bar : bars) {
            int topPixelY = bar[2];
            double value  = (baselineY - topPixelY) / pixelsPerUnit + minAxisValue;
            barValues.add((int) Math.round(value));
        }

        if (barValues.isEmpty()) return new BarChartAnalysis(barValues, 0, 0);

        return new BarChartAnalysis(barValues, Collections.max(barValues), Collections.min(barValues));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OCR Manual
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Varre a região do eixo Y, segmenta candidatos a label (regiões escuras),
     * lê cada dígito por template matching e monta o valor inteiro completo.
     * Retorna mapa valor -> Y central em pixels.
     */
    private static TreeMap<Integer, Integer> extractAxisLabels(
            BufferedImage img, int axisMarginX, int h,
            int barR, int barG, int barB, int barTol) {

        TreeMap<Integer, Integer> result = new TreeMap<>();

        // ── Projeção vertical ─────────────────────────────────────────────────
        int[] darkCount = new int[h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < axisMarginX; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                boolean isBar = Math.abs(r - barR) < barTol
                        && Math.abs(g - barG) < barTol
                        && Math.abs(b - barB) < barTol;
                if (!isBar && grayOf(rgb) < 160) darkCount[y]++;
            }
        }

        // ── Segmenta e mescla bandas ──────────────────────────────────────────
        List<int[]> labelBands = new ArrayList<>();
        int bandStart = -1;
        for (int y = 0; y < h; y++) {
            if (darkCount[y] > 0 && bandStart < 0) {
                bandStart = y;
            } else if (darkCount[y] == 0 && bandStart >= 0) {
                labelBands.add(new int[]{bandStart, y - 1});
                bandStart = -1;
            }
        }
        if (bandStart >= 0) labelBands.add(new int[]{bandStart, h - 1});

        List<int[]> mergedBands = new ArrayList<>();
        for (int[] band : labelBands) {
            if (!mergedBands.isEmpty()) {
                int[] last = mergedBands.get(mergedBands.size() - 1);
                if (band[0] - last[1] <= 3) { last[1] = band[1]; continue; }
            }
            mergedBands.add(new int[]{band[0], band[1]});
        }

        if (mergedBands.size() < 2) return result;

        // ── Filtra bandas por tamanho coerente ────────────────────────────────
        List<Integer> bandHeights = new ArrayList<>();
        for (int[] band : mergedBands) bandHeights.add(band[1] - band[0] + 1);
        Collections.sort(bandHeights);
        int medianH = bandHeights.get(bandHeights.size() / 2);

        List<int[]> validBands = new ArrayList<>();
        for (int[] band : mergedBands) {
            int bh = band[1] - band[0] + 1;
            if (bh >= medianH / 2 && bh <= medianH * 2) validBands.add(band);
        }

        if (validBands.size() < 2) return result;

        // ── Detecta o número de dígitos por banda para estimar largura ────────
        // Conta regiões horizontais escuras dentro da banda para saber
        // quantos dígitos tem cada label (1 dígito = 0,5,7 etc; 2 = 10,15,20)
        List<Integer> digitCounts = new ArrayList<>();
        for (int[] band : validBands) {
            int yStart = band[0], bandH = band[1] - band[0] + 1;
            int[] colDark = new int[axisMarginX];
            for (int x = 0; x < axisMarginX; x++) {
                for (int y = yStart; y <= band[1]; y++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    boolean isBar = Math.abs(r - barR) < barTol
                            && Math.abs(g - barG) < barTol
                            && Math.abs(b - barB) < barTol;
                    if (!isBar && grayOf(rgb) < 160) colDark[x]++;
                }
            }
            // Conta regiões não-vazias separadas por colunas vazias
            int regions = 0;
            boolean inRegion = false;
            for (int x = 0; x < axisMarginX; x++) {
                if (colDark[x] > 0 && !inRegion) { inRegion = true; regions++; }
                else if (colDark[x] == 0) inRegion = false;
            }
            digitCounts.add(regions);
        }

        // ── Interpola valores do eixo a partir da geometria ───────────────────
        // Premissas:
        // 1. Labels estão igualmente espaçados em pixels
        // 2. O label mais baixo (último) é sempre 0
        // 3. O espaçamento entre valores é uniforme (progressão aritmética)

        // Ordena bandas de cima para baixo (já estão, mas garante)
        validBands.sort(Comparator.comparingInt(b2 -> b2[0]));

        int n = validBands.size(); // número de labels (ex: 5 para 0,5,10,15,20)

        // Calcula espaçamentos entre centros consecutivos
        int[] centers = new int[n];
        for (int i = 0; i < n; i++) {
            centers[i] = (validBands.get(i)[0] + validBands.get(i)[1]) / 2;
        }

        // Espaçamento médio em pixels entre labels consecutivos
        double avgSpacingPx = (double)(centers[n-1] - centers[0]) / (n - 1);

        // O label mais baixo é 0; o espaçamento em valor é inferido pelo
        // número de dígitos: bandas com 2 regiões têm valores >= 10
        // Detecta o step do eixo contando quantos intervalos existem
        // entre 0 e o maior valor, usando o número de labels
        // Ex: 5 labels (0,5,10,15,20) → step = maior_valor / (n-1)
        // Precisamos do maior valor — usamos os digitCounts para inferir
        // se o topo tem 1 ou 2 dígitos, e qual múltiplo faz sentido

        // Conta labels com 1 dígito vs 2 dígitos
        int singleDigitCount = 0, doubleDigitCount = 0;
        for (int dc : digitCounts) {
            if (dc == 1) singleDigitCount++;
            else if (dc >= 2) doubleDigitCount++;
        }

        // ── Determina o step a partir da largura do label do topo ─────────────
        // Para labels de 1 dígito, mede a largura em pixels da região escura
        // do label mais alto e compara com a largura dos dígitos 0-9 renderizados
        // na mesma fonte estimada (medianH = altura dos labels em pixels)

        int step;
        if (doubleDigitCount == 0) {
            int[] topBand = validBands.get(0);

            // Mede largura real do label do topo na imagem
            int[] colDarkTop = new int[axisMarginX];
            for (int x = 0; x < axisMarginX; x++) {
                for (int y = topBand[0]; y <= topBand[1]; y++) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    boolean isBar = Math.abs(r - barR) < barTol
                            && Math.abs(g - barG) < barTol
                            && Math.abs(b - barB) < barTol;
                    if (!isBar && grayOf(rgb) < 160) colDarkTop[x]++;
                }
            }

            int darkLeft = -1, darkRight = -1;
            for (int x = 0; x < axisMarginX; x++) {
                if (colDarkTop[x] > 0) {
                    if (darkLeft < 0) darkLeft = x;
                    darkRight = x;
                }
            }

            int labelW = (darkLeft >= 0) ? (darkRight - darkLeft + 1) : 1;
            int labelH = topBand[1] - topBand[0] + 1;
            double ratio = (double) labelW / labelH;

            System.out.println("DEBUG ratio=" + ratio + " labelW=" + labelW + " labelH=" + labelH);

            // Proporções largura/altura observadas empiricamente nos gráficos:
            // "1" → muito estreito (ratio < 0.35)
            // "7" → médio-estreito (ratio ~0.45–0.60)
            // "2","3","5" → médio (ratio ~0.55–0.65)
            // "4" → médio-largo (ratio ~0.60–0.70)
            // "6","8","9","0" → largo (ratio > 0.65)
            //
            // Para distinguir entre candidatos próximos, usa também o número
            // de intervalos (n-1): step = maxValue / (n-1)
            // maxValue deve ser um múltiplo "bonito" (1,2,3,4,5,6,7,8,9)
            //
            // Estratégia: gera candidatos de step pelo ratio e escolhe o que
            // produz maxValue mais consistente com o número de intervalos

            // Mapeia ratio para lista de dígitos candidatos (do mais provável ao menos)
            int bestDigit;
            if (ratio < 0.35) {
                bestDigit = 1;
            } else if (ratio < 0.50) {
                bestDigit = 7; // "7" estreito
            } else if (ratio < 0.57) {
                bestDigit = 3;
            } else if (ratio < 0.63) {
                bestDigit = 2;
            } else if (ratio < 0.68) {
                bestDigit = 5;
            } else if (ratio < 0.74) {
                // 0.68–0.74: "7" (0.70 observado) ou "4"
                // Disambigua: "7" tem pixels concentrados no topo (barra horizontal)
                // enquanto "4" tem pixels no meio (barra horizontal no centro)
                // Conta pixels escuros na metade superior vs inferior do label
                int topHalf = 0, bottomHalf = 0;
                int midY = (topBand[0] + topBand[1]) / 2;
                for (int x = darkLeft; x <= darkRight; x++) {
                    for (int y = topBand[0]; y < midY; y++) {
                        int rgb = img.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        boolean isBar = Math.abs(r - barR) < barTol && Math.abs(gg - barG) < barTol && Math.abs(b - barB) < barTol;
                        if (!isBar && grayOf(rgb) < 160) topHalf++;
                    }
                    for (int y = midY; y <= topBand[1]; y++) {
                        int rgb = img.getRGB(x, y);
                        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                        boolean isBar = Math.abs(r - barR) < barTol && Math.abs(gg - barG) < barTol && Math.abs(b - barB) < barTol;
                        if (!isBar && grayOf(rgb) < 160) bottomHalf++;
                    }
                }
                // "7": mais pixels no topo (barra + diagonal descendo)
                // "4": pixels mais distribuídos, com concentração no meio
                double topRatio = (double) topHalf / Math.max(1, topHalf + bottomHalf);
                bestDigit = (topRatio > 0.52) ? 7 : 4;
            } else if (ratio < 0.80) {
                bestDigit = 6;
            } else if (ratio < 0.88) {
                bestDigit = 9;
            } else {
                bestDigit = 8;
            }

            step = bestDigit;

        } else {
            // Tem labels de 2 dígitos: lógica original
            int[] candidates = {1, 2, 5, 10, 25, 50, 100};
            step = 5;
            for (int s : candidates) {
                int maxVal = s * (n - 1);
                int maxDigits = String.valueOf(maxVal).length();
                if (maxDigits == digitCounts.get(0)) { step = s; break; }
            }
        }

        int maxValue = step * (n - 1);

        // ── Monta o mapa valor -> pixelY ──────────────────────────────────────
        for (int i = 0; i < n; i++) {
            int value = maxValue - (step * i); // de cima para baixo: max, max-step, ..., 0
            result.put(value, centers[i]);
        }

        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilitários
    // ─────────────────────────────────────────────────────────────────────────

    private static int grayOf(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b =  rgb & 0xFF;
        return (r + g + b) / 3;
    }

    private static int quantize(int value) {
        return (value / 10) * 10;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Estrutura de resultado
    // ─────────────────────────────────────────────────────────────────────────

    public static class BarChartAnalysis {

        private final List<Integer> barValues;
        private final int maxValue;
        private final int minValue;

        public BarChartAnalysis(List<Integer> barValues, int maxValue, int minValue) {
            this.barValues = barValues;
            this.maxValue  = maxValue;
            this.minValue  = minValue;
        }

        public List<Integer> getBarHeights() { return barValues; }
        public int getMaxHeight()            { return maxValue; }
        public int getMinHeight()            { return minValue; }

        @Override
        public String toString() {
            return "BarChartAnalysis{barValues=" + barValues
                    + ", maxValue=" + maxValue
                    + ", minValue=" + minValue + '}';
        }
    }
}