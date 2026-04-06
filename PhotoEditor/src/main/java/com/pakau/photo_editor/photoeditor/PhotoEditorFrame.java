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

    // --- Estado ---
    private BufferedImage originalImage = null;
    private BufferedImage filteredImage = null;
    private String activeFilter = null;

    // --- Componentes ---
    private JPanel inputPanel, outputPanel;
    private JLabel inputImageLabel, outputImageLabel;
    private JLabel inputPlaceholderLabel, outputPlaceholderLabel;
    private JLabel statusLabel;

    // Botões — Arquivo
    private JButton btnLoadImage, btnSaveImage;

    // Botões — Geométrico
    private JButton btnTranslate, btnAmpliar, btnReduzir;
    private JButton btnRotacionar, btnEspelharH, btnEspelharV;

    // Botões — Vizinhança
    private JButton btnConvolucao, btnMediana, btnModa, btnGauss;

    // Botões — Bordas
    private JButton btnRoberts, btnSobel, btnRobinson, btnFreiChen;
    private JButton btnMarrHildreth, btnCanny;

    // Botões — Cor
    private JButton btnBrilho, btnContraste;

    // ── Sliders de cor ──────────────────────────────────────────────────────────
    private JSlider sliderBrilho, sliderContraste, sliderThreshold;
    private JLabel  valBrilho,    valContraste,    valThreshold;

    // ── Sliders de bordas ───────────────────────────────────────────────────────
    // Roberts — limiar de magnitude (0–255 após normalização, default 30)
    private JSlider sliderRobertsThresh;
    private JLabel  valRobertsThresh;

    // Sobel — limiar de magnitude (0–255, default 60)
    private JSlider sliderSobelThresh;
    private JLabel  valSobelThresh;

    // Robinson — limiar de magnitude (0–255, default 30)
    private JSlider sliderRobinsonThresh;
    private JLabel  valRobinsonThresh;

    // Frei-Chen — limiar de projeção ×100, range 0–100 (default 30 → 0.30)
    private JSlider sliderFreiChenThresh;
    private JLabel  valFreiChenThresh;

    // Marr-Hildreth — sigma ×10, range 5–30 (default 14 → 1.4)
    private JSlider sliderMarrSigma;
    private JLabel  valMarrSigma;

    // Canny — limiar baixo e alto (0–255)
    private JSlider sliderCannyLow, sliderCannyHigh;
    private JLabel  valCannyLow,    valCannyHigh;

    // Cores do tema
    private static final Color BG_DARK      = new Color(15, 15, 19);
    private static final Color BG_PANEL     = new Color(22, 22, 28);
    private static final Color BG_CARD      = new Color(30, 30, 38);
    private static final Color BG_SECTION   = new Color(18, 18, 24);
    private static final Color ACCENT       = new Color(255, 75, 75);
    private static final Color ACCENT2      = new Color(75, 180, 255);
    private static final Color BTN_NORMAL   = new Color(42, 42, 55);
    private static final Color BTN_HOVER    = new Color(58, 58, 74);
    private static final Color BTN_ACTIVE   = new Color(255, 75, 75);
    private static final Color TEXT_PRIMARY = new Color(235, 235, 245);
    private static final Color TEXT_MUTED   = new Color(100, 100, 125);
    private static final Color TEXT_LABEL   = new Color(160, 160, 185);
    private static final Color BORDER_COLOR = new Color(45, 45, 60);

    public PhotoEditorFrame() {
        initComponents();
    }

    private void initComponents() {
        setTitle("FOTO EDITOR — v2.0");
        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 720));
        setBackground(BG_DARK);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        setContentPane(root);

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2, 8, 0));
        center.setBackground(BG_DARK);
        center.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        inputPanel  = buildImagePanel("ORIGINAL", true);
        outputPanel = buildImagePanel("RESULTADO", false);
        center.add(inputPanel);
        center.add(outputPanel);
        root.add(center, BorderLayout.CENTER);

        root.add(buildSidebar(), BorderLayout.WEST);

        pack();
        setLocationRelativeTo(null);
    }

    // ── Header ───────────────────────────────────────────────────────────────────
    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(BG_PANEL);
        header.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);

        JLabel dot = new JLabel("■");
        dot.setFont(new Font("Monospaced", Font.BOLD, 14));
        dot.setForeground(ACCENT);

        JLabel title = new JLabel("FOTO EDITOR");
        title.setFont(new Font("Monospaced", Font.BOLD, 18));
        title.setForeground(TEXT_PRIMARY);

        JLabel version = new JLabel("v2.0");
        version.setFont(new Font("Monospaced", Font.PLAIN, 11));
        version.setForeground(TEXT_MUTED);

        left.add(dot); left.add(title); left.add(version);
        header.add(left, BorderLayout.WEST);

        statusLabel = new JLabel("Nenhuma imagem carregada");
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        header.add(statusLabel, BorderLayout.EAST);

        return header;
    }

    // ── Painel de imagem ─────────────────────────────────────────────────────────
    private JPanel buildImagePanel(String labelText, boolean isInput) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(BG_CARD);
        card.setBorder(new LineBorder(BORDER_COLOR, 1, true));

        JPanel ph = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
        ph.setBackground(BG_SECTION);
        ph.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Dialog", Font.PLAIN, 8));
        dot.setForeground(isInput ? ACCENT : ACCENT2);

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 10));
        lbl.setForeground(isInput ? ACCENT : ACCENT2);

        ph.add(dot); ph.add(lbl);
        card.add(ph, BorderLayout.NORTH);

        JPanel imageArea = new JPanel(new GridBagLayout());
        imageArea.setBackground(BG_CARD);

        if (isInput) {
            inputImageLabel = new JLabel();
            inputImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            inputImageLabel.setVerticalAlignment(SwingConstants.CENTER);

            inputPlaceholderLabel = new JLabel(
                    "<html><center>" +
                            "<div style='font-size:32px;margin-bottom:8px'>📂</div>" +
                            "<div style='color:#646478;font-size:10px;font-family:monospace;line-height:1.8'>" +
                            "CLIQUE PARA CARREGAR<br/>JPG &nbsp;·&nbsp; PNG &nbsp;·&nbsp; BMP" +
                            "</div></center></html>"
            );
            inputPlaceholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageArea.add(inputPlaceholderLabel);

            imageArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imageArea.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { loadImage(); }
                @Override public void mouseEntered(MouseEvent e) { imageArea.setBackground(new Color(36, 36, 46)); }
                @Override public void mouseExited (MouseEvent e) { imageArea.setBackground(BG_CARD); }
            });
        } else {
            outputImageLabel = new JLabel();
            outputImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            outputImageLabel.setVerticalAlignment(SwingConstants.CENTER);

            outputPlaceholderLabel = new JLabel(
                    "<html><center>" +
                            "<div style='font-size:32px;margin-bottom:8px'>🖼️</div>" +
                            "<div style='color:#646478;font-size:10px;font-family:monospace;line-height:1.8'>" +
                            "RESULTADO AQUI<br/>SELECIONE UMA TRANSFORMAÇÃO" +
                            "</div></center></html>"
            );
            outputPlaceholderLabel.setHorizontalAlignment(SwingConstants.CENTER);
            imageArea.add(outputPlaceholderLabel);
        }

        card.add(imageArea, BorderLayout.CENTER);
        return card;
    }

    // ── Sidebar ──────────────────────────────────────────────────────────────────
    private JPanel buildSidebar() {
        JPanel inner = new JPanel();
        inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));
        inner.setBackground(BG_PANEL);
        inner.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        // ── ARQUIVO ──────────────────────────────────────────────────────────
        addSection(inner, "ARQUIVO");
        inner.add(Box.createVerticalStrut(4));

        btnLoadImage = makeActionButton("📂  Abrir Imagem", ACCENT);
        btnLoadImage.addActionListener(e -> loadImage());
        addSideBtn(inner, btnLoadImage);
        inner.add(Box.createVerticalStrut(4));

        btnSaveImage = makeActionButton("💾  Salvar Resultado", new Color(45, 170, 110));
        btnSaveImage.addActionListener(e -> saveImage());
        addSideBtn(inner, btnSaveImage);
        inner.add(Box.createVerticalStrut(16));

        // ── GEOMÉTRICO ───────────────────────────────────────────────────────
        addSection(inner, "GEOMÉTRICO");
        inner.add(Box.createVerticalStrut(4));

        btnTranslate  = makeFilterButton("↗  Transladar",     "TRANSLADAR");
        btnAmpliar    = makeFilterButton("⊕  Ampliar 1.5×",   "AMPLIAR");
        btnReduzir    = makeFilterButton("⊖  Reduzir 0.5×",   "REDUZIR");
        btnRotacionar = makeFilterButton("↻  Rotacionar 45°", "ROTACIONAR");
        btnEspelharH  = makeFilterButton("↔  Espelhar H",     "ESPELHAR_H");
        btnEspelharV  = makeFilterButton("↕  Espelhar V",     "ESPELHAR_V");

        for (JButton b : new JButton[]{btnTranslate, btnAmpliar, btnReduzir,
                btnRotacionar, btnEspelharH, btnEspelharV}) {
            addSideBtn(inner, b);
            inner.add(Box.createVerticalStrut(3));
        }
        inner.add(Box.createVerticalStrut(13));

        // ── VIZINHANÇA ───────────────────────────────────────────────────────
        addSection(inner, "VIZINHANÇA");
        inner.add(Box.createVerticalStrut(4));

        btnConvolucao = makeFilterButton("⊞  Convolução", "CONVOLUCAO");
        btnMediana    = makeFilterButton("◎  Mediana",    "MEDIANA");
        btnModa       = makeFilterButton("◈  Moda",       "MODA");
        btnGauss      = makeFilterButton("≋  Gaussiano",  "GAUSS");

        for (JButton b : new JButton[]{btnConvolucao, btnMediana, btnModa, btnGauss}) {
            addSideBtn(inner, b);
            inner.add(Box.createVerticalStrut(3));
        }
        inner.add(Box.createVerticalStrut(13));

        // ── DETECÇÃO DE BORDAS ───────────────────────────────────────────────
        addSection(inner, "DETECÇÃO DE BORDAS");
        inner.add(Box.createVerticalStrut(6));

        // ── Roberts ──────────────────────────────────────────────────────────
        // limiar: após normalizar a magnitude para 0–255, pixels >= limiar → borda
        sliderRobertsThresh = makeSlider(0, 255, 30);
        valRobertsThresh    = makeValLabel("30");
        sliderRobertsThresh.addChangeListener(e -> {
            valRobertsThresh.setText(String.valueOf(sliderRobertsThresh.getValue()));
            if ("ROBERTS".equals(activeFilter)) applyFilterAsync("ROBERTS");
        });
        btnRoberts = makeFilterButton("⟁  Roberts Cross", "ROBERTS");
        addSliderBlockLabeled(inner, "limiar", sliderRobertsThresh, valRobertsThresh, btnRoberts);
        inner.add(Box.createVerticalStrut(10));

        // ── Sobel ────────────────────────────────────────────────────────────
        // limiar: mesma lógica que Roberts, mas Sobel gera magnitudes maiores
        sliderSobelThresh = makeSlider(0, 255, 60);
        valSobelThresh    = makeValLabel("60");
        sliderSobelThresh.addChangeListener(e -> {
            valSobelThresh.setText(String.valueOf(sliderSobelThresh.getValue()));
            if ("SOBEL".equals(activeFilter)) applyFilterAsync("SOBEL");
        });
        btnSobel = makeFilterButton("⊟  Sobel", "SOBEL");
        addSliderBlockLabeled(inner, "limiar", sliderSobelThresh, valSobelThresh, btnSobel);
        inner.add(Box.createVerticalStrut(10));

        // ── Robinson ─────────────────────────────────────────────────────────
        // limiar: máximo entre as 8 direções, normalizado para 0–255
        sliderRobinsonThresh = makeSlider(0, 255, 30);
        valRobinsonThresh    = makeValLabel("30");
        sliderRobinsonThresh.addChangeListener(e -> {
            valRobinsonThresh.setText(String.valueOf(sliderRobinsonThresh.getValue()));
            if ("ROBINSON".equals(activeFilter)) applyFilterAsync("ROBINSON");
        });
        btnRobinson = makeFilterButton("✦  Robinson Compass", "ROBINSON");
        addSliderBlockLabeled(inner, "limiar", sliderRobinsonThresh, valRobinsonThresh, btnRobinson);
        inner.add(Box.createVerticalStrut(10));

        // ── Frei-Chen ────────────────────────────────────────────────────────
        // limiar%: projeção no subespaço de borda em %, range 0–100
        // valor baixo = detecta mais bordas; valor alto = apenas bordas fortes
        sliderFreiChenThresh = makeSlider(0, 100, 30);
        valFreiChenThresh    = makeValLabel("30%");
        sliderFreiChenThresh.addChangeListener(e -> {
            valFreiChenThresh.setText(sliderFreiChenThresh.getValue() + "%");
            if ("FREI_CHEN".equals(activeFilter)) applyFilterAsync("FREI_CHEN");
        });
        btnFreiChen = makeFilterButton("⋈  Frei-Chen", "FREI_CHEN");
        addSliderBlockLabeled(inner, "limiar%", sliderFreiChenThresh, valFreiChenThresh, btnFreiChen);
        inner.add(Box.createVerticalStrut(10));

        // ── Marr-Hildreth ────────────────────────────────────────────────────
        // sigma: desvio padrão do Gaussiano (×10 para usar inteiro no slider)
        // sigma pequeno = bordas finas/detalhadas; sigma grande = bordas grosseiras
        sliderMarrSigma = makeSlider(5, 30, 14);
        valMarrSigma    = makeValLabel("1.4");
        sliderMarrSigma.addChangeListener(e -> {
            valMarrSigma.setText(String.format("%.1f", sliderMarrSigma.getValue() / 10.0));
            if ("MARR_HILDRETH".equals(activeFilter)) applyFilterAsync("MARR_HILDRETH");
        });
        btnMarrHildreth = makeFilterButton("◉  Marr-Hildreth", "MARR_HILDRETH");
        addSliderBlockLabeled(inner, "sigma", sliderMarrSigma, valMarrSigma, btnMarrHildreth);
        inner.add(Box.createVerticalStrut(10));

        // ── Canny ────────────────────────────────────────────────────────────
        // low:  bordas fracas (candidatas)  — ficam brancas só se tocam uma borda forte
        // high: bordas fortes (confirmadas) — sempre brancas
        // restrição: low <= high (aplicada nos listeners)
        sliderCannyLow  = makeSlider(0, 254, 50);
        valCannyLow     = makeValLabel("50");
        sliderCannyHigh = makeSlider(1, 255, 150);
        valCannyHigh    = makeValLabel("150");

        sliderCannyLow.addChangeListener(e -> {
            int lo = sliderCannyLow.getValue();
            if (lo >= sliderCannyHigh.getValue()) sliderCannyHigh.setValue(lo + 1);
            valCannyLow.setText(String.valueOf(lo));
            if ("CANNY".equals(activeFilter)) applyFilterAsync("CANNY");
        });
        sliderCannyHigh.addChangeListener(e -> {
            int hi = sliderCannyHigh.getValue();
            if (hi <= sliderCannyLow.getValue()) sliderCannyLow.setValue(hi - 1);
            valCannyHigh.setText(String.valueOf(hi));
            if ("CANNY".equals(activeFilter)) applyFilterAsync("CANNY");
        });
        btnCanny = makeFilterButton("◆  Canny", "CANNY");

        addSubLabel(inner, "low thresh");
        addSliderRow(inner, sliderCannyLow,  valCannyLow);
        inner.add(Box.createVerticalStrut(2));
        addSubLabel(inner, "high thresh");
        addSliderRow(inner, sliderCannyHigh, valCannyHigh);
        inner.add(Box.createVerticalStrut(4));
        addSideBtn(inner, btnCanny);
        inner.add(Box.createVerticalStrut(13));

        // ── BRILHO ───────────────────────────────────────────────────────────
        addSection(inner, "BRILHO");
        inner.add(Box.createVerticalStrut(4));

        sliderBrilho = makeSlider(-100, 100, 0);
        valBrilho    = makeValLabel("0");
        sliderBrilho.addChangeListener(e -> {
            valBrilho.setText(String.valueOf(sliderBrilho.getValue()));
            if ("BRILHO".equals(activeFilter)) applyFilterAsync("BRILHO");
        });
        btnBrilho = makeFilterButton("☀  Aplicar Brilho", "BRILHO");
        addSliderBlock(inner, sliderBrilho, valBrilho, btnBrilho);
        inner.add(Box.createVerticalStrut(13));

        // ── CONTRASTE ────────────────────────────────────────────────────────
        addSection(inner, "CONTRASTE");
        inner.add(Box.createVerticalStrut(4));

        sliderContraste = makeSlider(-100, 100, 0);
        valContraste    = makeValLabel("0");
        sliderContraste.addChangeListener(e -> {
            valContraste.setText(String.valueOf(sliderContraste.getValue()));
            if ("CONTRASTE".equals(activeFilter)) applyFilterAsync("CONTRASTE");
        });
        btnContraste = makeFilterButton("◑  Aplicar Contraste", "CONTRASTE");
        addSliderBlock(inner, sliderContraste, valContraste, btnContraste);
        inner.add(Box.createVerticalStrut(13));

        // ── THRESHOLD ────────────────────────────────────────────────────────
        addSection(inner, "THRESHOLD");
        inner.add(Box.createVerticalStrut(4));

        sliderThreshold = makeSlider(0, 255, 128);
        valThreshold    = makeValLabel("128");
        sliderThreshold.addChangeListener(e -> {
            valThreshold.setText(String.valueOf(sliderThreshold.getValue()));
            if (originalImage != null) {
                activeFilter = "THRESHOLD";
                repaintButtons();
                applyFilterAsync("THRESHOLD");
            }
        });
        addSliderRow(inner, sliderThreshold, valThreshold);
        JLabel hint = new JLabel("arraste para aplicar ao vivo");
        hint.setFont(new Font("Monospaced", Font.PLAIN, 9));
        hint.setForeground(TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(Box.createVerticalStrut(3));
        inner.add(hint);
        inner.add(Box.createVerticalStrut(13));

        inner.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(inner,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(220, 0));
        scroll.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_COLOR));
        scroll.getViewport().setBackground(BG_PANEL);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBorder(BorderFactory.createEmptyBorder());

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(BG_PANEL);
        wrapper.add(scroll);
        return wrapper;
    }

    // ── Helpers de sidebar ───────────────────────────────────────────────────────

    private void addSection(JPanel parent, String text) {
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 9));
        lbl.setForeground(TEXT_MUTED);
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_COLOR);
        line.add(lbl, BorderLayout.WEST);
        line.add(sep, BorderLayout.CENTER);
        parent.add(line);
    }

    /** Rótulo pequeno de parâmetro (ex: "limiar", "sigma", "low thresh") */
    private void addSubLabel(JPanel parent, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
        lbl.setForeground(TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(1));
    }

    /** Linha única: slider + label de valor numérico */
    private void addSliderRow(JPanel parent, JSlider slider, JLabel val) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        slider.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.add(slider, BorderLayout.CENTER);
        row.add(val,    BorderLayout.EAST);
        parent.add(row);
    }

    /** Bloco: rótulo + slider + botão aplicar */
    private void addSliderBlockLabeled(JPanel parent, String label,
                                       JSlider slider, JLabel val, JButton btn) {
        addSubLabel(parent, label);
        addSliderRow(parent, slider, val);
        parent.add(Box.createVerticalStrut(3));
        addSideBtn(parent, btn);
    }

    /** Bloco padrão sem rótulo extra: slider + botão */
    private void addSliderBlock(JPanel parent, JSlider slider, JLabel val, JButton btn) {
        addSliderRow(parent, slider, val);
        parent.add(Box.createVerticalStrut(4));
        addSideBtn(parent, btn);
    }

    private void addSideBtn(JPanel parent, JButton btn) {
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(btn);
    }

    private JLabel makeValLabel(String text) {
        JLabel lbl = new JLabel(text, SwingConstants.RIGHT);
        lbl.setFont(new Font("Monospaced", Font.BOLD, 11));
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setPreferredSize(new Dimension(38, 20));
        return lbl;
    }

    private JSlider makeSlider(int min, int max, int value) {
        JSlider s = new JSlider(min, max, value);
        s.setOpaque(false);
        s.setBackground(BG_PANEL);
        s.setForeground(TEXT_MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        return s;
    }

    // ── Fábrica de botões ─────────────────────────────────────────────────────────
    private JButton makeFilterButton(String text, String filterKey) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = filterKey.equals(activeFilter);
                Color bg = active ? BTN_ACTIVE : getModel().isRollover() ? BTN_HOVER : BTN_NORMAL;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                if (active) {
                    g2.setColor(new Color(255, 160, 160));
                    g2.fillRoundRect(0, 4, 3, getHeight()-8, 2, 2);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_LABEL);
        btn.setFont(new Font("Monospaced", Font.PLAIN, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(200, 32));
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
                Color bg = getModel().isRollover() ? color.brighter() : color.darker();
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Monospaced", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 8));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(200, 32));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited (MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    // ── Carregar imagem ──────────────────────────────────────────────────────────
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
                updateStatus(file.getName() + "  —  " +
                        originalImage.getWidth() + " × " + originalImage.getHeight() + " px");
                repaintButtons();
            } catch (Exception ex) {
                logger.log(java.util.logging.Level.SEVERE, "Erro ao carregar", ex);
                JOptionPane.showMessageDialog(this, "Não foi possível abrir a imagem.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Salvar imagem ────────────────────────────────────────────────────────────
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
                logger.log(java.util.logging.Level.SEVERE, "Erro ao salvar", ex);
                JOptionPane.showMessageDialog(this, "Não foi possível salvar.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ── Aplicar filtro ────────────────────────────────────────────────────────────
    private void applyFilter(String filterKey) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        activeFilter = filterKey;
        repaintButtons();
        applyFilterAsync(filterKey);
    }

    // ── Executa o filtro em background ───────────────────────────────────────────
    private void applyFilterAsync(String filterKey) {
        if (originalImage == null) return;

        // Captura todos os valores dos sliders na EDT antes de entrar na thread
        final int    vBrilho            = sliderBrilho.getValue();
        final int    vContraste         = sliderContraste.getValue();
        final int    vThreshold         = sliderThreshold.getValue();
        final int    vRobertsThresh     = sliderRobertsThresh.getValue();
        final int    vSobelThresh       = sliderSobelThresh.getValue();
        final int    vRobinsonThresh    = sliderRobinsonThresh.getValue();
        final double vFreiChenThresh    = sliderFreiChenThresh.getValue() / 100.0;
        final double vMarrSigma         = sliderMarrSigma.getValue() / 10.0;
        final double vCannyLow          = sliderCannyLow.getValue();
        final double vCannyHigh         = sliderCannyHigh.getValue();

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                return switch (filterKey) {
                    // Geométrico
                    case "TRANSLADAR"    -> transladar(originalImage, 15, 10);
                    case "AMPLIAR"       -> escalar(originalImage, 1.5, 1.5);
                    case "REDUZIR"       -> escalar(originalImage, 0.5, 0.5);
                    case "ROTACIONAR"    -> rotacionar(originalImage, 45);
                    case "ESPELHAR_H"    -> espelharHorizontal(originalImage);
                    case "ESPELHAR_V"    -> espelharVertical(originalImage);
                    // Vizinhança
                    case "CONVOLUCAO"    -> convolucao(originalImage, new float[][]{
                            {1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f}
                    });
                    case "MEDIANA"       -> mediana(originalImage, 3);
                    case "MODA"          -> moda(originalImage, 3);
                    case "GAUSS"         -> gaussiano(originalImage, 3, 1.0);
                    // Bordas — parâmetros dos sliders
                    case "ROBERTS"       -> robertsCross(originalImage, vRobertsThresh);
                    case "SOBEL"         -> sobel(originalImage, vSobelThresh);
                    case "ROBINSON"      -> robinsonCompass(originalImage, vRobinsonThresh);
                    case "FREI_CHEN"     -> freiChen(originalImage, vFreiChenThresh);
                    case "MARR_HILDRETH" -> marrHildreth(originalImage, 5, vMarrSigma);
                    case "CANNY"         -> canny(originalImage, vCannyLow, vCannyHigh);
                    // Cor
                    case "BRILHO"        -> brilho(originalImage, vBrilho);
                    case "CONTRASTE"     -> contraste(originalImage, vContraste);
                    case "THRESHOLD"     -> threshold(originalImage, vThreshold);
                    default              -> originalImage;
                };
            }
            @Override protected void done() {
                try {
                    filteredImage = get();
                    displayImage(filteredImage, false);
                    updateStatus("Filtro: " + filterKey);
                } catch (Exception ex) {
                    logger.log(java.util.logging.Level.SEVERE, "Erro no filtro", ex);
                }
            }
        };
        worker.execute();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TRANSFORMAÇÕES GEOMÉTRICAS
    // ══════════════════════════════════════════════════════════════════════════

    private double[] multiplicarMatrizVetor(double[][] m, double[] v) {
        double[] r = new double[3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                r[i] += m[i][j] * v[j];
        return r;
    }

    private double[][] multiplicarMatrizes(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++)
                for (int k = 0; k < 3; k++)
                    r[i][j] += a[i][k] * b[k][j];
        return r;
    }

    private BufferedImage aplicarTransformacao(BufferedImage src, double[][] matriz) {
        int w = src.getWidth(), h = src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double[][] inv = inverterMatriz(matriz);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double[] o = multiplicarMatrizVetor(inv, new double[]{x, y, 1});
                int ox = (int) Math.round(o[0]);
                int oy = (int) Math.round(o[1]);
                if (ox >= 0 && ox < w && oy >= 0 && oy < h)
                    out.setRGB(x, y, src.getRGB(ox, oy));
            }
        return out;
    }

    private double[][] inverterMatriz(double[][] m) {
        double det = m[0][0]*(m[1][1]*m[2][2]-m[1][2]*m[2][1])
                - m[0][1]*(m[1][0]*m[2][2]-m[1][2]*m[2][0])
                + m[0][2]*(m[1][0]*m[2][1]-m[1][1]*m[2][0]);
        double[][] inv = new double[3][3];
        inv[0][0] =  (m[1][1]*m[2][2]-m[1][2]*m[2][1])/det;
        inv[0][1] = -(m[0][1]*m[2][2]-m[0][2]*m[2][1])/det;
        inv[0][2] =  (m[0][1]*m[1][2]-m[0][2]*m[1][1])/det;
        inv[1][0] = -(m[1][0]*m[2][2]-m[1][2]*m[2][0])/det;
        inv[1][1] =  (m[0][0]*m[2][2]-m[0][2]*m[2][0])/det;
        inv[1][2] = -(m[0][0]*m[1][2]-m[0][2]*m[1][0])/det;
        inv[2][0] =  (m[1][0]*m[2][1]-m[1][1]*m[2][0])/det;
        inv[2][1] = -(m[0][0]*m[2][1]-m[0][1]*m[2][0])/det;
        inv[2][2] =  (m[0][0]*m[1][1]-m[0][1]*m[1][0])/det;
        return inv;
    }

    private BufferedImage transladar(BufferedImage src, int tx, int ty) {
        return aplicarTransformacao(src, new double[][]{{1,0,tx},{0,1,ty},{0,0,1}});
    }

    private BufferedImage escalar(BufferedImage src, double sx, double sy) {
        return aplicarTransformacao(src, new double[][]{{sx,0,0},{0,sy,0},{0,0,1}});
    }

    private BufferedImage rotacionar(BufferedImage src, double angulo) {
        double rad = Math.toRadians(angulo);
        double cos = Math.cos(rad), sen = Math.sin(rad);
        int cx = src.getWidth()/2, cy = src.getHeight()/2;
        double[][] m = multiplicarMatrizes(
                new double[][]{{1,0,cx},{0,1,cy},{0,0,1}},
                multiplicarMatrizes(
                        new double[][]{{cos,-sen,0},{sen,cos,0},{0,0,1}},
                        new double[][]{{1,0,-cx},{0,1,-cy},{0,0,1}}
                )
        );
        return aplicarTransformacao(src, m);
    }

    private BufferedImage espelharHorizontal(BufferedImage src) {
        return aplicarTransformacao(src,
                new double[][]{{-1,0,src.getWidth()-1},{0,1,0},{0,0,1}});
    }

    private BufferedImage espelharVertical(BufferedImage src) {
        return aplicarTransformacao(src,
                new double[][]{{1,0,0},{0,-1,src.getHeight()-1},{0,0,1}});
    }

    // ══════════════════════════════════════════════════════════════════════════
    // VIZINHANÇA
    // ══════════════════════════════════════════════════════════════════════════

    private BufferedImage convolucao(BufferedImage src, float[][] kernel) {
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
                out.setRGB(x,y,(a<<24)|(clamp(sR)<<16)|(clamp(sG)<<8)|clamp(sB));
            }
        return out;
    }

    private BufferedImage gaussiano(BufferedImage src, int ks, double sigma) {
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

    private BufferedImage mediana(BufferedImage src, int ks) {
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

    private BufferedImage moda(BufferedImage src, int ks) {
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

    // ══════════════════════════════════════════════════════════════════════════
    // DETECÇÃO DE BORDAS — utilitários compartilhados
    // ══════════════════════════════════════════════════════════════════════════

    /** Luminância BT.601: array [h][w] com valores 0–255. */
    private int[][] toGray(BufferedImage src) {
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

    /**
     * Converte magnitudes double[][] para imagem ARGB.
     *
     * @param thresh  >= 0 → binarização: pixels (normalizados a 0–255) >= thresh ficam brancos.
     *                < 0  → normalização contínua 0–255.
     */
    private BufferedImage magnitudeToImage(double[][] mag, BufferedImage src, double thresh) {
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

    // ── Roberts Cross ────────────────────────────────────────────────────────────
    /**
     * Gradiente diagonal 2×2.
     *   Gx = p(x,y) − p(x+1,y+1)
     *   Gy = p(x+1,y) − p(x,y+1)
     *   magnitude = sqrt(Gx² + Gy²)
     *
     * @param thresh  limiar de magnitude (0–255 normalizado). 0 = tudo borda, 255 = nada.
     */
    private BufferedImage robertsCross(BufferedImage src, int thresh) {
        int[][] g = toGray(src);
        int h = g.length, w = g[0].length;
        double[][] mag = new double[h][w];
        for (int y = 0; y < h-1; y++)
            for (int x = 0; x < w-1; x++) {
                double gx = g[y][x]   - g[y+1][x+1];
                double gy = g[y][x+1] - g[y+1][x];
                mag[y][x] = Math.sqrt(gx*gx + gy*gy);
            }
        return magnitudeToImage(mag, src, thresh);
    }

    // ── Sobel ────────────────────────────────────────────────────────────────────
    /**
     * Gradiente 3×3 com ponderação central ±2.
     *   magnitude = sqrt(Gx² + Gy²)
     *
     * @param thresh  limiar de magnitude (0–255 normalizado).
     */
    private BufferedImage sobel(BufferedImage src, int thresh) {
        int[][] g = toGray(src);
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
        return magnitudeToImage(mag, src, thresh);
    }

    // ── Robinson Compass ─────────────────────────────────────────────────────────
    /**
     * 8 máscaras direcionais — magnitude = máximo entre as 8 respostas.
     *
     * @param thresh  limiar de magnitude (0–255 normalizado).
     */
    private BufferedImage robinsonCompass(BufferedImage src, int thresh) {
        int[][] g = toGray(src);
        int h = g.length, w = g[0].length;

        int[][][] masks = {
                {{-1,-2,-1},{0,0,0},{1,2,1}},   // N
                {{0,-1,-2},{1,0,-1},{2,1,0}},    // NE
                {{1,0,-1},{2,0,-2},{1,0,-1}},    // E
                {{2,1,0},{1,0,-1},{0,-1,-2}},    // SE
                {{1,2,1},{0,0,0},{-1,-2,-1}},    // S
                {{0,1,2},{-1,0,1},{-2,-1,0}},    // SO
                {{-1,0,1},{-2,0,2},{-1,0,1}},    // O
                {{-2,-1,0},{-1,0,1},{0,1,2}}     // NO
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
        return magnitudeToImage(mag, src, thresh);
    }

    // ── Frei-Chen ────────────────────────────────────────────────────────────────
    /**
     * Projeção da janela 3×3 no subespaço das 4 máscaras ortonormais de borda.
     * Magnitude = proporção de energia no subespaço [0.0–1.0].
     *
     * @param thresh  limiar de projeção [0.0–1.0]. Quanto maior, mais seletivo.
     *                Internamente comparado diretamente com a magnitude (não normalizada).
     */
    private BufferedImage freiChen(BufferedImage src, double thresh) {
        int[][] g = toGray(src);
        int h = g.length, w = g[0].length;
        double s2 = Math.sqrt(2);

        double[][][] masks = {
                {{  1, s2,  1},{  0,  0,  0},{ -1,-s2, -1}},  // W1
                {{  1,  0, -1},{ s2,  0,-s2},{  1,  0, -1}},  // W2
                {{  0,  1, s2},{ -1,  0,  1},{-s2, -1,  0}},  // W3
                {{ s2,  1,  0},{  1,  0, -1},{  0, -1,-s2}}   // W4
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

        // mag está em [0,1] — thresh também em [0,1]
        // magnitudeToImage normaliza internamente, então passamos thresh*255
        return magnitudeToImage(mag, src, thresh * 255.0);
    }

    // ── Marr-Hildreth (LoG) ───────────────────────────────────────────────────────
    /**
     * Gaussiano(sigma) → Laplaciano → zero-crossings.
     * Resultado exibido em escala contínua (normalizado) para melhor visualização.
     *
     * @param ks     tamanho do kernel Gaussiano
     * @param sigma  desvio padrão (slider: 0.5–3.0)
     */
    private BufferedImage marrHildreth(BufferedImage src, int ks, double sigma) {
        BufferedImage suavizada = gaussiano(src, ks, sigma);
        int[][] g = toGray(suavizada);
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
        return magnitudeToImage(mag, src, -1); // contínuo — sigma já controla a espessura
    }

    // ── Canny ────────────────────────────────────────────────────────────────────
    /**
     * Pipeline: gaussiano → Sobel → NMS → histerese → rastreamento.
     *
     * @param lowThresh   limiar baixo (bordas fracas candidatas)
     * @param highThresh  limiar alto  (bordas fortes confirmadas)
     */
    private BufferedImage canny(BufferedImage src, double lowThresh, double highThresh) {
        int h = src.getHeight(), w = src.getWidth();

        BufferedImage suavizada = gaussiano(src, 5, 1.4);
        int[][] g = toGray(suavizada);

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

        // Supressão de não-máximos
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

        // Histerese
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

        // Rastreamento de bordas fracas
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

    // ══════════════════════════════════════════════════════════════════════════
    // AJUSTES DE COR
    // ══════════════════════════════════════════════════════════════════════════

    private BufferedImage brilho(BufferedImage src, int delta) {
        int aj = (int)(delta*2.55);
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int rgb=src.getRGB(x,y), a=(rgb>>24)&0xFF;
            int r=clamp(((rgb>>16)&0xFF)+aj);
            int g=clamp(((rgb>> 8)&0xFF)+aj);
            int b=clamp(( rgb     &0xFF)+aj);
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return out;
    }

    private BufferedImage contraste(BufferedImage src, int delta) {
        int d=(int)(delta*2.55);
        double f=(259.0*(d+255))/(255.0*(259-d));
        int w=src.getWidth(), h=src.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            int rgb=src.getRGB(x,y), a=(rgb>>24)&0xFF;
            int r=clamp((int)(f*(((rgb>>16)&0xFF)-128)+128));
            int g=clamp((int)(f*(((rgb>> 8)&0xFF)-128)+128));
            int b=clamp((int)(f*(( rgb     &0xFF)-128)+128));
            out.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }
        return out;
    }

    private BufferedImage threshold(BufferedImage src, int limiar) {
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

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

    private int clamp(int v)   { return Math.min(255, Math.max(0, v)); }
    private int clamp(float v) { return Math.min(255, Math.max(0, (int)v)); }

    @SuppressWarnings("unused")
    private BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        WritableRaster r = bi.copyData(bi.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, r, cm.isAlphaPremultiplied(), null);
    }

    private void displayImage(BufferedImage img, boolean isInput) {
        JPanel area = getImageArea(isInput);
        if (area == null) return;
        int pw = area.getWidth()  > 0 ? area.getWidth()  - 20 : 480;
        int ph = area.getHeight() > 0 ? area.getHeight() - 20 : 400;
        double scale = Math.min((double)pw/img.getWidth(), (double)ph/img.getHeight());
        Image scaled = img.getScaledInstance(
                (int)(img.getWidth()*scale), (int)(img.getHeight()*scale), Image.SCALE_SMOOTH);
        area.removeAll();
        JLabel lbl = isInput ? inputImageLabel : outputImageLabel;
        lbl.setIcon(new ImageIcon(scaled));
        lbl.setText("");
        area.add(lbl);
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
            if (c instanceof JPanel && ((JPanel)c).getLayout() instanceof GridBagLayout)
                return (JPanel)c;
        return null;
    }

    private void updateStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void repaintButtons() {
        SwingUtilities.invokeLater(() -> {
            for (JButton b : new JButton[]{
                    btnTranslate, btnAmpliar, btnReduzir, btnRotacionar,
                    btnEspelharH, btnEspelharV, btnConvolucao, btnMediana,
                    btnModa, btnGauss,
                    btnRoberts, btnSobel, btnRobinson, btnFreiChen,
                    btnMarrHildreth, btnCanny,
                    btnBrilho, btnContraste
            }) if (b != null) b.repaint();
        });
    }

    // ── Main ─────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName()); break;
                }
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        java.awt.EventQueue.invokeLater(() -> new PhotoEditorFrame().setVisible(true));
    }
}