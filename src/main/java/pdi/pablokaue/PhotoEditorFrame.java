package pdi.pablokaue;

import pdi.pablokaue.filters.*;
import pdi.pablokaue.utils.ImageUtils;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/**
 * @author 0414249
 */
public class PhotoEditorFrame extends JFrame {

    private static final java.util.logging.Logger logger =
            java.util.logging.Logger.getLogger(PhotoEditorFrame.class.getName());

    // --- Estado ---
    private BufferedImage originalImage = null;
    private BufferedImage filteredImage = null;
    // Salva a imagem antes do slider começar a modificar para não acumular
    private BufferedImage preSliderImage = null;
    private String activeFilter = null;
    
    // Histórico de filtros via Configuração
    private class FilterConfig {
        String key;
        String display;
        
        int vBrilho, vContraste, vThreshold, vRobertsThresh, vSobelThresh, vRobinsonThresh;
        double vFreiChenThresh, vMarrSigma, vCannyLow, vCannyHigh;
        String morphOp, selectedStructElement;
        int morphIter;
    }
    private java.util.List<FilterConfig> activeFilters = new ArrayList<>();

    // --- Componentes ---
    private JPanel inputPanel, outputPanel;
    private JLabel inputImageLabel, outputImageLabel;
    private JLabel inputPlaceholderLabel, outputPlaceholderLabel;
    private JLabel statusLabel;

    // Histórico Painel
    private JPanel historyPanel;
    private JPanel historyContainerPanel;
    private JToggleButton btnToggleHistory;

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

    // Botões — Afinamento
    private JButton btnStentiford, btnZhangSuen, btnHolt;

    // Botões — Cor
    private JButton btnBrilho, btnContraste;

    // ── Sliders de cor ──────────────────────────────────────────────────────────
    private JSlider sliderBrilho, sliderContraste, sliderThreshold;
    private JLabel  valBrilho,    valContraste,    valThreshold;

    // ── Sliders de bordas ───────────────────────────────────────────────────────
    private JSlider sliderRobertsThresh;
    private JLabel  valRobertsThresh;

    private JSlider sliderSobelThresh;
    private JLabel  valSobelThresh;

    private JSlider sliderRobinsonThresh;
    private JLabel  valRobinsonThresh;

    private JSlider sliderFreiChenThresh;
    private JLabel  valFreiChenThresh;

    private JSlider sliderMarrSigma;
    private JLabel  valMarrSigma;

    private JSlider sliderCannyLow, sliderCannyHigh;
    private JLabel  valCannyLow,    valCannyHigh;

    // ── Morfologia ──────────────────────────────────────────────────────────────
    private String selectedStructElement = null;
    private BufferedImage morphBaseImage = null;
    private JSlider sliderMorphIter;
    private JLabel  valMorphIter;
    private JButton btnErosao, btnDilatacao, btnAbertura, btnFechamento;
    private JButton[] seButtons;
    private boolean morphThreshEnabled = true;   // toggle: aplicar threshold ao selecionar SE
    
    // Botão reset global
    private JButton btnResetImage;

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
        setupKeyBindings();
    }

    private void setupKeyBindings() {
        JRootPane rootPane = this.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "resetImage");

        actionMap.put("resetImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                resetImage();
            }
        });
    }

    private void initComponents() {
        setTitle("FOTO EDITOR — v2.0");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
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
        
        root.add(buildHistoryPanel(), BorderLayout.EAST);

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

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setOpaque(false);
        
        statusLabel = new JLabel("Nenhuma imagem carregada");
        statusLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        right.add(statusLabel);

        btnToggleHistory = new JToggleButton("Histórico");
        btnToggleHistory.setFont(new Font("Monospaced", Font.PLAIN, 11));
        btnToggleHistory.setForeground(TEXT_PRIMARY);
        btnToggleHistory.setBackground(BTN_NORMAL);
        btnToggleHistory.setFocusPainted(false);
        btnToggleHistory.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btnToggleHistory.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleHistory.addActionListener(e -> {
            historyPanel.setVisible(btnToggleHistory.isSelected());
            revalidate();
            repaint();
        });
        right.add(btnToggleHistory);

        header.add(right, BorderLayout.EAST);

        return header;
    }
    
    // ── Painel de Histórico ──────────────────────────────────────────────────────
    private JPanel buildHistoryPanel() {
        historyPanel = new JPanel(new BorderLayout());
        historyPanel.setBackground(BG_PANEL);
        historyPanel.setBorder(new MatteBorder(0, 1, 0, 0, BORDER_COLOR));
        historyPanel.setPreferredSize(new Dimension(250, 0));
        historyPanel.setVisible(false); // Colapsado por padrão

        JLabel lblTitle = new JLabel("HISTÓRICO");
        lblTitle.setFont(new Font("Monospaced", Font.BOLD, 12));
        lblTitle.setForeground(TEXT_PRIMARY);
        lblTitle.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        historyPanel.add(lblTitle, BorderLayout.NORTH);

        historyContainerPanel = new JPanel();
        historyContainerPanel.setLayout(new BoxLayout(historyContainerPanel, BoxLayout.Y_AXIS));
        historyContainerPanel.setBackground(BG_DARK);
        
        JScrollPane scroll = new JScrollPane(historyContainerPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        historyPanel.add(scroll, BorderLayout.CENTER);

        return historyPanel;
    }

    private void updateHistoryUI() {
        SwingUtilities.invokeLater(() -> {
            historyContainerPanel.removeAll();
            for (int i = 0; i < activeFilters.size(); i++) {
                final int index = i;
                FilterConfig cfg = activeFilters.get(i);
                
                JPanel row = new JPanel(new BorderLayout());
                row.setBackground(BG_CARD);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
                row.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                
                JLabel lbl = new JLabel((i + 1) + ". " + cfg.display);
                lbl.setFont(new Font("Monospaced", Font.PLAIN, 11));
                lbl.setForeground(TEXT_PRIMARY);
                
                JButton btnDel = new JButton("✕");
                btnDel.setFont(new Font("SansSerif", Font.BOLD, 12));
                btnDel.setForeground(ACCENT);
                btnDel.setContentAreaFilled(false);
                btnDel.setBorderPainted(false);
                btnDel.setFocusPainted(false);
                btnDel.setMargin(new Insets(0, 0, 0, 0));
                btnDel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                btnDel.addActionListener(e -> removeFilterAt(index));
                
                row.add(lbl, BorderLayout.CENTER);
                row.add(btnDel, BorderLayout.EAST);
                
                historyContainerPanel.add(row);
                historyContainerPanel.add(Box.createVerticalStrut(2));
            }
            historyContainerPanel.revalidate();
            historyContainerPanel.repaint();
        });
    }

    private void removeFilterAt(int index) {
        if (index >= 0 && index < activeFilters.size()) {
            activeFilters.remove(index);
            recomputeFilters();
        }
    }

    private void recomputeFilters() {
        if (originalImage == null) return;
        
        if (activeFilters.isEmpty()) {
            filteredImage = null;
            preSliderImage = null;
            activeFilter = null;
            clearOutput();
            updateStatus("Filtros removidos");
            repaintButtons();
            updateHistoryUI();
            return;
        }
        
        final java.util.List<FilterConfig> snapshot = new ArrayList<>(activeFilters);
        
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                BufferedImage current = originalImage;
                for (FilterConfig cfg : snapshot) {
                    current = applyConfig(current, cfg);
                }
                return current;
            }
            @Override protected void done() {
                try {
                    filteredImage = get();
                    preSliderImage = filteredImage;
                    displayImage(filteredImage, false);
                    updateStatus("Filtros recalculados: " + snapshot.size());
                    updateHistoryUI();
                } catch (Exception ex) {
                    logger.log(java.util.logging.Level.SEVERE, "Erro ao recalcular", ex);
                }
            }
        };
        worker.execute();
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
        inner.add(Box.createVerticalStrut(4));
        
        btnResetImage = makeActionButton("↺  Resetar Imagem (R)", new Color(70, 70, 100));
        btnResetImage.addActionListener(e -> resetImage());
        addSideBtn(inner, btnResetImage);
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

        // ── PASSA BAIXA ───────────────────────────────────────────────────────
        addSection(inner, "PASSA BAIXA");
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
        addSection(inner, "PASSA-ALTA");
        inner.add(Box.createVerticalStrut(6));

        sliderRobertsThresh = makeSlider(0, 255, 30);
        valRobertsThresh    = makeValLabel("30");
        sliderRobertsThresh.addChangeListener(e -> {
            valRobertsThresh.setText(String.valueOf(sliderRobertsThresh.getValue()));
            if ("ROBERTS".equals(activeFilter)) applyFilterAsync("ROBERTS", true);
        });
        btnRoberts = makeFilterButton("⟁  Roberts Cross", "ROBERTS");
        addSliderBlockLabeled(inner, "limiar", sliderRobertsThresh, valRobertsThresh, btnRoberts);
        inner.add(Box.createVerticalStrut(10));

        sliderSobelThresh = makeSlider(0, 255, 60);
        valSobelThresh    = makeValLabel("60");
        sliderSobelThresh.addChangeListener(e -> {
            valSobelThresh.setText(String.valueOf(sliderSobelThresh.getValue()));
            if ("SOBEL".equals(activeFilter)) applyFilterAsync("SOBEL", true);
        });
        btnSobel = makeFilterButton("⊟  Sobel", "SOBEL");
        addSliderBlockLabeled(inner, "limiar", sliderSobelThresh, valSobelThresh, btnSobel);
        inner.add(Box.createVerticalStrut(10));

        sliderRobinsonThresh = makeSlider(0, 255, 30);
        valRobinsonThresh    = makeValLabel("30");
        sliderRobinsonThresh.addChangeListener(e -> {
            valRobinsonThresh.setText(String.valueOf(sliderRobinsonThresh.getValue()));
            if ("ROBINSON".equals(activeFilter)) applyFilterAsync("ROBINSON", true);
        });
        btnRobinson = makeFilterButton("✦  Robinson Compass", "ROBINSON");
        addSliderBlockLabeled(inner, "limiar", sliderRobinsonThresh, valRobinsonThresh, btnRobinson);
        inner.add(Box.createVerticalStrut(10));

        sliderFreiChenThresh = makeSlider(0, 100, 30);
        valFreiChenThresh    = makeValLabel("30%");
        sliderFreiChenThresh.addChangeListener(e -> {
            valFreiChenThresh.setText(sliderFreiChenThresh.getValue() + "%");
            if ("FREI_CHEN".equals(activeFilter)) applyFilterAsync("FREI_CHEN", true);
        });
        btnFreiChen = makeFilterButton("⋈  Frei-Chen", "FREI_CHEN");
        addSliderBlockLabeled(inner, "limiar%", sliderFreiChenThresh, valFreiChenThresh, btnFreiChen);
        inner.add(Box.createVerticalStrut(10));

        sliderMarrSigma = makeSlider(5, 30, 14);
        valMarrSigma    = makeValLabel("1.4");
        sliderMarrSigma.addChangeListener(e -> {
            valMarrSigma.setText(String.format("%.1f", sliderMarrSigma.getValue() / 10.0));
            if ("MARR_HILDRETH".equals(activeFilter)) applyFilterAsync("MARR_HILDRETH", true);
        });
        btnMarrHildreth = makeFilterButton("◉  Marr-Hildreth", "MARR_HILDRETH");
        addSliderBlockLabeled(inner, "sigma", sliderMarrSigma, valMarrSigma, btnMarrHildreth);
        inner.add(Box.createVerticalStrut(10));

        sliderCannyLow  = makeSlider(0, 254, 50);
        valCannyLow     = makeValLabel("50");
        sliderCannyHigh = makeSlider(1, 255, 150);
        valCannyHigh    = makeValLabel("150");

        sliderCannyLow.addChangeListener(e -> {
            int lo = sliderCannyLow.getValue();
            if (lo >= sliderCannyHigh.getValue()) sliderCannyHigh.setValue(lo + 1);
            valCannyLow.setText(String.valueOf(lo));
            if ("CANNY".equals(activeFilter)) applyFilterAsync("CANNY", true);
        });
        sliderCannyHigh.addChangeListener(e -> {
            int hi = sliderCannyHigh.getValue();
            if (hi <= sliderCannyLow.getValue()) sliderCannyLow.setValue(hi - 1);
            valCannyHigh.setText(String.valueOf(hi));
            if ("CANNY".equals(activeFilter)) applyFilterAsync("CANNY", true);
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

        // ── AFINAMENTO ────────────────────────────────────────────────────────
        addSection(inner, "AFINAMENTO");
        inner.add(Box.createVerticalStrut(4));

        btnStentiford = makeFilterButton("✂  Stentiford", "STENTIFORD");
        btnZhangSuen  = makeFilterButton("✂  Zhang-Suen", "ZHANG_SUEN");
        btnHolt       = makeFilterButton("✂  Holt", "HOLT");

        for (JButton b : new JButton[]{btnStentiford, btnZhangSuen, btnHolt}) {
            addSideBtn(inner, b);
            inner.add(Box.createVerticalStrut(3));
        }
        inner.add(Box.createVerticalStrut(13));

        addSection(inner, "TRANSFORMÇÕES PONTUAIS");
        inner.add(Box.createVerticalStrut(6));

        // ── BRILHO ───────────────────────────────────────────────────────────
        addSection(inner, "BRILHO");
        inner.add(Box.createVerticalStrut(4));

        sliderBrilho = makeSlider(-100, 100, 0);
        valBrilho    = makeValLabel("0");
        sliderBrilho.addChangeListener(e -> {
            valBrilho.setText(String.valueOf(sliderBrilho.getValue()));
            if ("BRILHO".equals(activeFilter)) applyFilterAsync("BRILHO", true);
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
            if ("CONTRASTE".equals(activeFilter)) applyFilterAsync("CONTRASTE", true);
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
                if (!"THRESHOLD".equals(activeFilter)) {
                    preSliderImage = (filteredImage != null) ? filteredImage : originalImage;
                    activeFilter = "THRESHOLD";
                    repaintButtons();
                }
                applyFilterAsync("THRESHOLD", true);
            }
        });
        addSliderRow(inner, sliderThreshold, valThreshold);
        JLabel hint = new JLabel("arraste para aplicar ao vivo");
        hint.setFont(new Font("Monospaced", Font.PLAIN, 9));
        hint.setForeground(TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        inner.add(Box.createVerticalStrut(3));
        inner.add(hint);
        inner.add(Box.createVerticalStrut(16));

        // ── MORFOLOGIA ───────────────────────────────────────────────────────
        buildMorphologySection(inner);

        inner.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(inner,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setPreferredSize(new Dimension(400, 0));
        scroll.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_COLOR));
        scroll.getViewport().setBackground(BG_PANEL);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 0));
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

    private void addSubLabel(JPanel parent, String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
        lbl.setForeground(TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        parent.add(lbl);
        parent.add(Box.createVerticalStrut(1));
    }

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

    private void addSliderBlockLabeled(JPanel parent, String label,
                                       JSlider slider, JLabel val, JButton btn) {
        addSubLabel(parent, label);
        addSliderRow(parent, slider, val);
        parent.add(Box.createVerticalStrut(3));
        addSideBtn(parent, btn);
    }

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
                preSliderImage = null;
                activeFilter  = null;
                morphBaseImage = null;
                selectedStructElement = null;
                if (seButtons != null) for (JButton b : seButtons) b.repaint();
                displayImage(originalImage, true);
                clearOutput();
                activeFilters.clear();
                updateHistoryUI();
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
    
    // ── Resetar Imagem ───────────────────────────────────────────────────────────
    private void resetImage() {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        filteredImage = null;
        preSliderImage = null;
        activeFilter = null;
        morphBaseImage = null;
        selectedStructElement = null;
        if (seButtons != null) for (JButton b : seButtons) b.repaint();
        
        // Reset dos sliders de cor
        sliderBrilho.setValue(0);
        sliderContraste.setValue(0);
        sliderThreshold.setValue(128);

        clearOutput();
        activeFilters.clear();
        updateHistoryUI();
        updateStatus("Imagem resetada.");
        repaintButtons();
    }
    
    // ── Histórico Lógica ─────────────────────────────────────────────────────────

    private FilterConfig createCurrentConfig(String key, String display) {
        FilterConfig c = new FilterConfig();
        c.key = key;
        c.display = display;
        c.vBrilho = sliderBrilho.getValue();
        c.vContraste = sliderContraste.getValue();
        c.vThreshold = sliderThreshold.getValue();
        c.vRobertsThresh = sliderRobertsThresh.getValue();
        c.vSobelThresh = sliderSobelThresh.getValue();
        c.vRobinsonThresh = sliderRobinsonThresh.getValue();
        c.vFreiChenThresh = sliderFreiChenThresh.getValue() / 100.0;
        c.vMarrSigma = sliderMarrSigma.getValue() / 10.0;
        c.vCannyLow = sliderCannyLow.getValue();
        c.vCannyHigh = sliderCannyHigh.getValue();
        c.morphOp = key;
        c.selectedStructElement = selectedStructElement;
        c.morphIter = sliderMorphIter.getValue();
        return c;
    }
    
    private String getFilterDisplayName(String filterKey) {
        return switch (filterKey) {
            case "ROBERTS"       -> "ROBERTS (" + sliderRobertsThresh.getValue() + ")";
            case "SOBEL"         -> "SOBEL (" + sliderSobelThresh.getValue() + ")";
            case "ROBINSON"      -> "ROBINSON (" + sliderRobinsonThresh.getValue() + ")";
            case "FREI_CHEN"     -> String.format("FREI_CHEN (%.0f%%)", sliderFreiChenThresh.getValue() / 100.0 * 100);
            case "MARR_HILDRETH" -> String.format("MARR_HILDRETH (%.1f)", sliderMarrSigma.getValue() / 10.0);
            case "CANNY"         -> String.format("CANNY (%.0f/%.0f)", (double)sliderCannyLow.getValue(), (double)sliderCannyHigh.getValue());
            case "BRILHO"        -> "BRILHO (" + sliderBrilho.getValue() + ")";
            case "CONTRASTE"     -> "CONTRASTE (" + sliderContraste.getValue() + ")";
            case "THRESHOLD"     -> "THRESHOLD (" + sliderThreshold.getValue() + ")";
            case "EROSAO", "DILATACAO", "ABERTURA", "FECHAMENTO" -> "Morfologia: " + filterKey + " (iters: " + sliderMorphIter.getValue() + ")";
            default              -> filterKey;
        };
    }

    private BufferedImage applyConfig(BufferedImage input, FilterConfig cfg) {
        return switch (cfg.key) {
            case "TRANSLADAR"    -> GeometricFilters.transladar(input, 15, 10);
            case "AMPLIAR"       -> GeometricFilters.escalar(input, 1.5, 1.5);
            case "REDUZIR"       -> GeometricFilters.escalar(input, 0.5, 0.5);
            case "ROTACIONAR"    -> GeometricFilters.rotacionar(input, 45);
            case "ESPELHAR_H"    -> GeometricFilters.espelharHorizontal(input);
            case "ESPELHAR_V"    -> GeometricFilters.espelharVertical(input);
            case "CONVOLUCAO"    -> LowPassFilters.convolucao(input, new float[][]{
                    {1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f},{1/9f,1/9f,1/9f}
            });
            case "MEDIANA"       -> LowPassFilters.mediana(input, 3);
            case "MODA"          -> LowPassFilters.moda(input, 3);
            case "GAUSS"         -> LowPassFilters.gaussiano(input, 3, 1.0);
            case "ROBERTS"       -> EdgeDetectionFilters.robertsCross(input, cfg.vRobertsThresh);
            case "SOBEL"         -> EdgeDetectionFilters.sobel(input, cfg.vSobelThresh);
            case "ROBINSON"      -> EdgeDetectionFilters.robinsonCompass(input, cfg.vRobinsonThresh);
            case "FREI_CHEN"     -> EdgeDetectionFilters.freiChen(input, cfg.vFreiChenThresh);
            case "MARR_HILDRETH" -> EdgeDetectionFilters.marrHildreth(input, 5, cfg.vMarrSigma);
            case "CANNY"         -> EdgeDetectionFilters.canny(input, cfg.vCannyLow, cfg.vCannyHigh);
            case "STENTIFORD"    -> ThinningFilters.stentiford(input);
            case "ZHANG_SUEN"    -> ThinningFilters.zhangSuen(input);
            case "HOLT"          -> ThinningFilters.holt(input);
            case "BRILHO"        -> ColorFilters.brilho(input, cfg.vBrilho);
            case "CONTRASTE"     -> ColorFilters.contraste(input, cfg.vContraste);
            case "THRESHOLD"     -> ColorFilters.threshold(input, cfg.vThreshold);
            case "EROSAO"        -> MorphologyFilters.aplicarNVezes(input, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, true);
            case "DILATACAO"     -> MorphologyFilters.aplicarNVezes(input, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, false);
            case "ABERTURA"      -> {
                BufferedImage e = MorphologyFilters.aplicarNVezes(input, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, true);
                yield MorphologyFilters.aplicarNVezes(e, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, false);
            }
            case "FECHAMENTO"    -> {
                BufferedImage d = MorphologyFilters.aplicarNVezes(input, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, false);
                yield MorphologyFilters.aplicarNVezes(d, MorphologyFilters.getStructuringElement(cfg.selectedStructElement), cfg.morphIter, true);
            }
            default              -> input;
        };
    }

    // ── Aplicar filtro ────────────────────────────────────────────────────────────
    private void applyFilter(String filterKey) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        preSliderImage = (filteredImage != null) ? filteredImage : originalImage;
        activeFilter = filterKey;
        repaintButtons();
        
        applyFilterAsync(filterKey, false);
    }

    // ── Executa o filtro em background ───────────────────────────────────────────
    private void applyFilterAsync(String filterKey, boolean fromSlider) {
        if (originalImage == null) return;
        
        String displayStr = getFilterDisplayName(filterKey);
        
        if (fromSlider && !activeFilters.isEmpty()) {
            FilterConfig last = activeFilters.get(activeFilters.size() - 1);
            if (last.key.equals(filterKey)) {
                activeFilters.set(activeFilters.size() - 1, createCurrentConfig(filterKey, displayStr));
            } else {
                activeFilters.add(createCurrentConfig(filterKey, displayStr));
            }
        } else {
            activeFilters.add(createCurrentConfig(filterKey, displayStr));
        }
        
        updateHistoryUI();
        
        final BufferedImage input;
        if (fromSlider) {
            input = (preSliderImage != null) ? preSliderImage : originalImage;
        } else {
            input = (filteredImage != null) ? filteredImage : originalImage;
        }

        final FilterConfig cfg = activeFilters.get(activeFilters.size() - 1);

        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                return applyConfig(input, cfg);
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
    // MORFOLOGIA — SEÇÃO DO PAINEL
    // ══════════════════════════════════════════════════════════════════════════

    private void buildMorphologySection(JPanel inner) {
        addSection(inner, "MORFOLOGIA");
        inner.add(Box.createVerticalStrut(6));

        // ── Elementos estruturantes (grade 2×3) ──────────────────────────
        addSubLabel(inner, "elemento estruturante");
        inner.add(Box.createVerticalStrut(3));

        String[] seNames = {"Disco", "Cruz", "Quadrado", "Hexágono", "Linha", "Par Pts"};
        String[] seKeys  = {"DISCO", "CRUZ", "QUADRADO", "HEXAGONO", "LINHA", "PONTOS"};

        JPanel seGrid = new JPanel(new java.awt.GridLayout(2, 3, 4, 4));
        seGrid.setOpaque(false);
        seGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        seGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        seButtons = new JButton[seKeys.length];
        for (int i = 0; i < seKeys.length; i++) {
            final String key = seKeys[i];
            JButton btn = makeSEButton(seNames[i], key);
            seButtons[i] = btn;
            seGrid.add(btn);
        }
        inner.add(seGrid);
        inner.add(Box.createVerticalStrut(6));

        // ── Toggle threshold ─────────────────────────────────────────────
        JPanel toggleRow = new JPanel(new BorderLayout(6, 0));
        toggleRow.setOpaque(false);
        toggleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        toggleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel toggleLbl = new JLabel("aplicar threshold ao selecionar");
        toggleLbl.setFont(new Font("Monospaced", Font.PLAIN, 9));
        toggleLbl.setForeground(TEXT_MUTED);

        JToggleButton toggleThresh = new JToggleButton("ON") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = isSelected() ? new Color(45, 170, 110) : new Color(100, 40, 40);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        toggleThresh.setSelected(true);
        toggleThresh.setForeground(Color.WHITE);
        toggleThresh.setFont(new Font("Monospaced", Font.BOLD, 9));
        toggleThresh.setFocusPainted(false);
        toggleThresh.setBorderPainted(false);
        toggleThresh.setContentAreaFilled(false);
        toggleThresh.setPreferredSize(new Dimension(34, 22));
        toggleThresh.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleThresh.addActionListener(e -> {
            morphThreshEnabled = toggleThresh.isSelected();
            toggleThresh.setText(morphThreshEnabled ? "ON" : "OFF");
            toggleThresh.repaint();
        });
        toggleThresh.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { toggleThresh.repaint(); }
            @Override public void mouseExited (MouseEvent e) { toggleThresh.repaint(); }
        });

        toggleRow.add(toggleLbl,    BorderLayout.CENTER);
        toggleRow.add(toggleThresh, BorderLayout.EAST);
        inner.add(toggleRow);
        inner.add(Box.createVerticalStrut(6));

        // ── Slider de iterações ──────────────────────────────────────────
        addSubLabel(inner, "iterações  (1 = sem repetição)");
        sliderMorphIter = makeSlider(1, 10, 1);
        valMorphIter    = makeValLabel("1");
        sliderMorphIter.addChangeListener(e ->
                valMorphIter.setText(String.valueOf(sliderMorphIter.getValue())));
        addSliderRow(inner, sliderMorphIter, valMorphIter);
        inner.add(Box.createVerticalStrut(6));

        // ── Operações morfológicas ───────────────────────────────────────
        btnErosao     = makeMorphButton("⊖  Erosão",     "EROSAO");
        btnDilatacao  = makeMorphButton("⊕  Dilatação",  "DILATACAO");
        btnAbertura   = makeMorphButton("◁  Abertura",   "ABERTURA");
        btnFechamento = makeMorphButton("▷  Fechamento", "FECHAMENTO");

        for (JButton b : new JButton[]{btnErosao, btnDilatacao, btnAbertura, btnFechamento}) {
            addSideBtn(inner, b);
            inner.add(Box.createVerticalStrut(3));
        }
        inner.add(Box.createVerticalStrut(13));
    }

    // ── Botão de elemento estruturante (grade compacta) ──────────────────────────
    private JButton makeSEButton(String label, String key) {
        JButton btn = new JButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = key.equals(selectedStructElement);
                Color bg = active ? ACCENT2
                        : getModel().isRollover() ? BTN_HOVER : BTN_NORMAL;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                if (active) {
                    g2.setColor(new Color(180, 230, 255));
                    g2.fillRoundRect(0, 3, 3, getHeight()-6, 2, 2);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setForeground(TEXT_LABEL);
        btn.setFont(new Font("Monospaced", Font.PLAIN, 9));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setHorizontalAlignment(SwingConstants.CENTER);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> selectStructElement(key));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited (MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    // ── Botão de operação morfológica ────────────────────────────────────────────
    private JButton makeMorphButton(String text, String op) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getModel().isRollover() ? BTN_HOVER : BTN_NORMAL;
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
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
        btn.addActionListener(e -> applyMorphOp(op));
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override public void mouseExited (MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MORFOLOGIA — LÓGICA
    // ══════════════════════════════════════════════════════════════════════════

    /** Seleciona SE e aplica threshold (limiar 128) para criar a base morfológica */
    private void selectStructElement(String key) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectedStructElement = key;
        if (seButtons != null) for (JButton b : seButtons) b.repaint();
        
        if (morphThreshEnabled) {
            // Se ativado, binariza a imagem automaticamente no histórico
            applyFilter("THRESHOLD");
        }
    }

    /** Aplica operação morfológica SOBRE a imagem filtrada atual (composição possível) */
    private void applyMorphOp(String op) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this, "Carregue uma imagem primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedStructElement == null) {
            JOptionPane.showMessageDialog(this,
                    "Selecione um elemento estruturante primeiro.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        preSliderImage = (filteredImage != null) ? filteredImage : originalImage;
        activeFilter = op;
        repaintButtons();
        
        applyFilterAsync(op, false);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════════

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
                    btnStentiford, btnZhangSuen, btnHolt,
                    btnBrilho, btnContraste
            }) if (b != null) b.repaint();
        });
    }
}