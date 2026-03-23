/*
 * Photo Editor - UI com dois painéis de imagem e transformações por matrizes
 */
package com.pakau.photo_editor.photoeditor;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * @author 0414249
 */
public class PhotoEditorFrame extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger =
        java.util.logging.Logger.getLogger(PhotoEditorFrame.class.getName());

    // --- Estado da aplicação ---
    private BufferedImage originalImage = null;
    private BufferedImage filteredImage = null;
    private String activeFilter = null;

    // --- Componentes principais ---
    private JPanel inputPanel;
    private JPanel outputPanel;
    private JLabel inputImageLabel;
    private JLabel outputImageLabel;
    private JLabel inputPlaceholderLabel;
    private JLabel outputPlaceholderLabel;
    private JLabel statusLabel;

    // Botões de transformação
    private JButton btnTranslate;
    private JButton btnAmpliar;
    private JButton btnReduzir;
    private JButton btnRotacionar;
    private JButton btnEspelharH;
    private JButton btnEspelharV;
    private JButton btnLoadImage;
    private JButton btnSaveImage;

    // Cores do tema (dark industrial)
    private static final Color BG_DARK      = new Color(18, 18, 22);
    private static final Color BG_PANEL     = new Color(28, 28, 34);
    private static final Color BG_CARD      = new Color(36, 36, 44);
    private static final Color ACCENT       = new Color(255, 82, 82);
    private static final Color BTN_NORMAL   = new Color(50, 50, 62);
    private static final Color BTN_HOVER    = new Color(68, 68, 84);
    private static final Color BTN_ACTIVE   = new Color(255, 82, 82);
    private static final Color TEXT_PRIMARY = new Color(240, 240, 248);
    private static final Color TEXT_MUTED   = new Color(120, 120, 140);
    private static final Color BORDER_COLOR = new Color(55, 55, 68);

    public PhotoEditorFrame() {
        initComponents();
    }

    @SuppressWarnings("unchecked")
    private void initComponents() {
        setTitle("FOTO EDITOR — v1.0");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1100, 720));
        setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.setBackground(BG_DARK);
        center.setBorder(BorderFactory.createEmptyBorder(0, 20, 12, 20));

        inputPanel  = buildImagePanel("ORIGINAL", true);
        outputPanel = buildImagePanel("RESULTADO", false);
        center.add(inputPanel);
        center.add(outputPanel);
        root.add(center, BorderLayout.CENTER);

        root.add(buildControlBar(), BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
    }

    // ── Header ─────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new CompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(14, 24, 14, 24)
        ));

        JLabel title = new JLabel("FOTO EDITOR");
        title.setFont(loadFont("Monospaced", Font.BOLD, 20));
        title.setForeground(TEXT_PRIMARY);

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Dialog", Font.PLAIN, 10));
        dot.setForeground(ACCENT);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(dot);
        left.add(title);
        header.add(left, BorderLayout.WEST);

        statusLabel = new JLabel("Nenhuma imagem carregada");
        statusLabel.setFont(loadFont("Monospaced", Font.PLAIN, 12));
        statusLabel.setForeground(TEXT_MUTED);
        header.add(statusLabel, BorderLayout.EAST);

        return header;
    }

    // ── Painel de imagem ────────────────────────────────────────────────────────
    private JPanel buildImagePanel(String labelText, boolean isInput) {
        JPanel card = new JPanel(new BorderLayout(0, 0));
        card.setBackground(BG_CARD);
        card.setBorder(new CompoundBorder(
            new LineBorder(BORDER_COLOR, 1, true),
            BorderFactory.createEmptyBorder(0, 0, 0, 0)
        ));

        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        panelHeader.setBackground(BG_PANEL);
        panelHeader.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(loadFont("Monospaced", Font.BOLD, 11));
        lbl.setForeground(isInput ? ACCENT : new Color(82, 200, 255));
        panelHeader.add(lbl);
        card.add(panelHeader, BorderLayout.NORTH);

        JPanel imageArea = new JPanel(new GridBagLayout());
        imageArea.setBackground(BG_CARD);

        if (isInput) {
            inputImageLabel = new JLabel();
            inputImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            inputImageLabel.setVerticalAlignment(SwingConstants.CENTER);

            inputPlaceholderLabel = new JLabel(
                "<html><center><span style='font-size:28px'>📂</span><br/>" +
                "<span style='color:#787890;font-size:11px;font-family:monospace'>" +
                "CLIQUE PARA CARREGAR<br/>JPG / PNG / BMP</span></center></html>"
            );
            inputPlaceholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageArea.add(inputPlaceholderLabel);

            imageArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imageArea.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { loadImage(); }
                @Override public void mouseEntered(MouseEvent e) { imageArea.setBackground(new Color(42, 42, 52)); }
                @Override public void mouseExited (MouseEvent e) { imageArea.setBackground(BG_CARD); }
            });
        } else {
            outputImageLabel = new JLabel();
            outputImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            outputImageLabel.setVerticalAlignment(SwingConstants.CENTER);

            outputPlaceholderLabel = new JLabel(
                "<html><center><span style='font-size:28px'>🖼️</span><br/>" +
                "<span style='color:#787890;font-size:11px;font-family:monospace'>" +
                "RESULTADO APARECE AQUI<br/>SELECIONE UMA TRANSFORMAÇÃO</span></center></html>"
            );
            outputPlaceholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageArea.add(outputPlaceholderLabel);
        }

        card.add(imageArea, BorderLayout.CENTER);
        return card;
    }

    // ── Barra de controles ──────────────────────────────────────────────────────
    private JPanel buildControlBar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setBackground(BG_PANEL);
        bar.setBorder(new CompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(14, 24, 14, 24)
        ));

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        filters.setOpaque(false);

        JLabel filterLabel = new JLabel("TRANSFORMAÇÕES:");
        filterLabel.setFont(loadFont("Monospaced", Font.BOLD, 11));
        filterLabel.setForeground(TEXT_MUTED);
        filters.add(filterLabel);

        btnTranslate  = makeFilterButton("Transladar",    "TRANSLADAR");
        btnAmpliar    = makeFilterButton("Ampliar",        "AMPLIAR");
        btnReduzir    = makeFilterButton("Reduzir",        "REDUZIR");
        btnRotacionar = makeFilterButton("Rotacionar 45°", "ROTACIONAR");
        btnEspelharH  = makeFilterButton("Espelhar H",     "ESPELHAR_H");
        btnEspelharV  = makeFilterButton("Espelhar V",     "ESPELHAR_V");

        filters.add(btnAmpliar);
        filters.add(btnReduzir);
        filters.add(btnRotacionar);
        filters.add(btnEspelharH);
        filters.add(btnEspelharV);

        bar.add(filters, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        btnLoadImage = makeActionButton("📂  Abrir", ACCENT);
        btnLoadImage.addActionListener(e -> loadImage());

        btnSaveImage = makeActionButton("💾  Salvar", new Color(50, 180, 120));
        btnSaveImage.addActionListener(e -> saveImage());

        actions.add(btnLoadImage);
        actions.add(btnSaveImage);
        bar.add(actions, BorderLayout.EAST);

        return bar;
    }

    // ── Fábrica de botões ───────────────────────────────────────────────────────
    private JButton makeFilterButton(String text, String filterKey) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = filterKey.equals(activeFilter) ? BTN_ACTIVE
                         : getModel().isRollover()        ? BTN_HOVER
                         : BTN_NORMAL;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_PRIMARY);
        btn.setFont(loadFont("Monospaced", Font.PLAIN, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(130, 36));
        btn.addActionListener(e -> applyFilter(filterKey));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited (MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    private JButton makeActionButton(String text, Color color) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? color.brighter() : color);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(loadFont("Monospaced", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(120, 36));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited (MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    // ── Carregar imagem ─────────────────────────────────────────────────────────
    private void loadImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
            "Imagens (JPG, PNG, BMP)", "jpg", "jpeg", "png", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                originalImage = ImageIO.read(file);
                filteredImage = null;
                activeFilter  = null;
                displayImage(originalImage, true);
                clearOutput();
                updateStatus("Carregado: " + file.getName()
                    + "  (" + originalImage.getWidth() + "×" + originalImage.getHeight() + ")");
                repaintButtons();
            } catch (Exception ex) {
                logger.log(java.util.logging.Level.SEVERE, "Erro ao carregar imagem", ex);
                JOptionPane.showMessageDialog(this, "Não foi possível abrir a imagem.", "Erro",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Salvar imagem ───────────────────────────────────────────────────────────
    private void saveImage() {
        if (filteredImage == null) {
            JOptionPane.showMessageDialog(this, "Aplique uma transformação antes de salvar.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("resultado.png"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG", "png"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".png"))
                    file = new File(file.getPath() + ".png");
                ImageIO.write(filteredImage, "png", file);
                updateStatus("Salvo: " + file.getName());
            } catch (Exception ex) {
                logger.log(java.util.logging.Level.SEVERE, "Erro ao salvar imagem", ex);
                JOptionPane.showMessageDialog(this, "Não foi possível salvar a imagem.", "Erro",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Aplicar transformação ───────────────────────────────────────────────────
    private void applyFilter(String filterKey) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        activeFilter = filterKey;
        repaintButtons();

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                return switch (filterKey) {
                    case "TRANSLADAR"  -> transladar(originalImage, 15, 10);
                    case "AMPLIAR"     -> escalar(originalImage, 1.5, 1.5);
                    case "REDUZIR"     -> escalar(originalImage, 0.5, 0.5);
                    case "ROTACIONAR"  -> rotacionar(originalImage, 45);
                    case "ESPELHAR_H"  -> espelharHorizontal(originalImage);
                    case "ESPELHAR_V"  -> espelharVertical(originalImage);
                    default            -> originalImage;
                };
            }
            @Override protected void done() {
                try {
                    filteredImage = get();
                    displayImage(filteredImage, false);
                    updateStatus("Transformação aplicada: " + filterKey);
                } catch (Exception ex) {
                    logger.log(java.util.logging.Level.SEVERE, "Erro na transformação", ex);
                }
            }
        };
        worker.execute();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSFORMAÇÕES POR MULTIPLICAÇÃO DE MATRIZES
    // ══════════════════════════════════════════════════════════════════════════

    // ── Multiplica matriz 3x3 por vetor homogêneo [x, y, 1] ────────────────────
    private double[] multiplicarMatrizVetor(double[][] m, double[] v) {
        double[] resultado = new double[3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                resultado[i] += m[i][j] * v[j];
        return resultado;
    }

    // ── Multiplica duas matrizes 3x3 ───────────────────────────────────────────
    private double[][] multiplicarMatrizes(double[][] a, double[][] b) {
        double[][] resultado = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                for (int k = 0; k < 3; k++)
                    resultado[i][j] += a[i][k] * b[k][j];
        return resultado;
    }

    // ── Aplica uma matriz de transformação em cada pixel da imagem ──────────────
    private BufferedImage aplicarTransformacao(BufferedImage src, double[][] matriz) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double[] resultado = multiplicarMatrizVetor(matriz, new double[]{x, y, 1});
                int novoX = (int) resultado[0];
                int novoY = (int) resultado[1];
                if (novoX >= 0 && novoX < w && novoY >= 0 && novoY < h)
                    out.setRGB(novoX, novoY, src.getRGB(x, y));
            }
        }
        return out;
    }

    // ── Translação ───────────────────────────────────────────────────────────────
    // Move a imagem tx pixels para direita e ty pixels para baixo
    //
    // | 1  0  tx |   | x |   | x + tx |
    // | 0  1  ty | * | y | = | y + ty |
    // | 0  0   1 |   | 1 |   |   1    |
    private BufferedImage transladar(BufferedImage src, int tx, int ty) {
        double[][] matriz = {
            {1, 0, tx},
            {0, 1, ty},
            {0, 0,  1}
        };
        return aplicarTransformacao(src, matriz);
    }

    // ── Escala (Ampliação / Redução) ─────────────────────────────────────────────
    // sx e sy > 1 ampliam, entre 0 e 1 reduzem
    //
    // | sx  0  0 |   | x |   | x * sx |
    // |  0 sy  0 | * | y | = | y * sy |
    // |  0  0  1 |   | 1 |   |   1    |
    private BufferedImage escalar(BufferedImage src, double sx, double sy) {
        double[][] matriz = {
            {sx,  0, 0},
            { 0, sy, 0},
            { 0,  0, 1}
        };
        return aplicarTransformacao(src, matriz);
    }

    // ── Rotação ──────────────────────────────────────────────────────────────────
    // Rotaciona em torno do centro da imagem no ângulo informado (em graus)
    // Combina 3 matrizes: translada para origem → rotaciona → volta ao centro
    //
    // | cos θ  -sen θ  0 |
    // | sen θ   cos θ  0 |
    // |   0       0    1 |
    private BufferedImage rotacionar(BufferedImage src, double angulo) {
        double rad = Math.toRadians(angulo);
        double cos = Math.cos(rad);
        double sen = Math.sin(rad);
        int cx = src.getWidth()  / 2;
        int cy = src.getHeight() / 2;

        // 1) Leva o centro da imagem para a origem (0,0)
        double[][] paraCentro = {
            {1, 0, -cx},
            {0, 1, -cy},
            {0, 0,   1}
        };
        // 2) Rotaciona em torno da origem
        double[][] rotacao = {
            {cos, -sen, 0},
            {sen,  cos, 0},
            {  0,    0, 1}
        };
        // 3) Devolve de volta para o centro
        double[][] voltarCentro = {
            {1, 0, cx},
            {0, 1, cy},
            {0, 0,  1}
        };

        // Combina as três em uma única matriz
        double[][] matriz = multiplicarMatrizes(voltarCentro,
                                multiplicarMatrizes(rotacao, paraCentro));
        return aplicarTransformacao(src, matriz);
    }

    // ── Espelhamento horizontal ───────────────────────────────────────────────────
    // Inverte da esquerda para direita
    //
    // | -1  0  w-1 |   | x |   | (w-1) - x |
    // |  0  1   0  | * | y | = |     y     |
    // |  0  0   1  |   | 1 |   |     1     |
    private BufferedImage espelharHorizontal(BufferedImage src) {
        int w = src.getWidth();
        double[][] matriz = {
            {-1, 0, w - 1},
            { 0, 1,     0},
            { 0, 0,     1}
        };
        return aplicarTransformacao(src, matriz);
    }

    // ── Espelhamento vertical ─────────────────────────────────────────────────────
    // Inverte de cima para baixo
    //
    // |  1  0    0  |   | x |   |     x     |
    // |  0 -1  h-1  | * | y | = | (h-1) - y |
    // |  0  0    1  |   | 1 |   |     1     |
    private BufferedImage espelharVertical(BufferedImage src) {
        int h = src.getHeight();
        double[][] matriz = {
            {1,  0,     0},
            {0, -1, h - 1},
            {0,  0,     1}
        };
        return aplicarTransformacao(src, matriz);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlpha = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlpha, null);
    }

    private void displayImage(BufferedImage img, boolean isInput) {
        JPanel area = getImageArea(isInput);
        if (area == null) return;

        int pw = area.getWidth()  > 0 ? area.getWidth()  - 20 : 500;
        int ph = area.getHeight() > 0 ? area.getHeight() - 20 : 440;

        double scale = Math.min((double) pw / img.getWidth(), (double) ph / img.getHeight());
        int w = (int)(img.getWidth()  * scale);
        int h = (int)(img.getHeight() * scale);
        Image scaled = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        ImageIcon icon = new ImageIcon(scaled);

        area.removeAll();
        if (isInput) {
            inputImageLabel.setIcon(icon);
            inputImageLabel.setText("");
            area.add(inputImageLabel);
        } else {
            outputImageLabel.setIcon(icon);
            outputImageLabel.setText("");
            area.add(outputImageLabel);
        }
        area.revalidate();
        area.repaint();
    }

    private void clearOutput() {
        JPanel area = getImageArea(false);
        if (area == null) return;
        area.removeAll();
        area.add(outputPlaceholderLabel);
        area.revalidate();
        area.repaint();
    }

    private JPanel getImageArea(boolean isInput) {
        JPanel card = isInput ? inputPanel : outputPanel;
        for (Component c : card.getComponents())
            if (c instanceof JPanel && ((JPanel) c).getLayout() instanceof GridBagLayout)
                return (JPanel) c;
        return null;
    }

    private void updateStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void repaintButtons() {
        SwingUtilities.invokeLater(() -> {
            btnTranslate.repaint();
            btnAmpliar.repaint();
            btnReduzir.repaint();
            btnRotacionar.repaint();
            btnEspelharH.repaint();
            btnEspelharV.repaint();
        });
    }

    private Font loadFont(String name, int style, int size) {
        return new Font(name, style, size);
    }

    // ── Main ────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new PhotoEditorFrame().setVisible(true));
    }
}