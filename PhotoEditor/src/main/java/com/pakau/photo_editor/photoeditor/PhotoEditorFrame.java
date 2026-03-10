/*
 * Photo Editor - UI com dois painéis de imagem e filtros
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

    // Botões de filtro
    private JButton btnTranslate;
    
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

        // ── Root panel ──────────────────────────────────────────────────────
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        setContentPane(root);

        // ── Header ──────────────────────────────────────────────────────────
        root.add(buildHeader(), BorderLayout.NORTH);

        // ── Centro: dois painéis de imagem ───────────────────────────────────
        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.setBackground(BG_DARK);
        center.setBorder(BorderFactory.createEmptyBorder(0, 20, 12, 20));

        inputPanel  = buildImagePanel("ORIGINAL", true);
        outputPanel = buildImagePanel("RESULTADO", false);
        center.add(inputPanel);
        center.add(outputPanel);
        root.add(center, BorderLayout.CENTER);

        // ── Barra inferior: controles ────────────────────────────────────────
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

        // Label do painel
        JPanel panelHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10));
        panelHeader.setBackground(BG_PANEL);
        panelHeader.setBorder(new MatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JLabel lbl = new JLabel(labelText);
        lbl.setFont(loadFont("Monospaced", Font.BOLD, 11));
        lbl.setForeground(isInput ? ACCENT : new Color(82, 200, 255));
        panelHeader.add(lbl);
        card.add(panelHeader, BorderLayout.NORTH);

        // Área da imagem
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

            // Clique para abrir arquivo
            imageArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            imageArea.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) { loadImage(); }
                @Override public void mouseEntered(MouseEvent e) {
                    imageArea.setBackground(new Color(42, 42, 52));
                }
                @Override public void mouseExited(MouseEvent e) {
                    imageArea.setBackground(BG_CARD);
                }
            });
        } else {
            outputImageLabel = new JLabel();
            outputImageLabel.setHorizontalAlignment(SwingConstants.CENTER);
            outputImageLabel.setVerticalAlignment(SwingConstants.CENTER);

            outputPlaceholderLabel = new JLabel(
                "<html><center><span style='font-size:28px'>🖼️</span><br/>" +
                "<span style='color:#787890;font-size:11px;font-family:monospace'>" +
                "RESULTADO APARECE AQUI<br/>SELECIONE UM FILTRO</span></center></html>"
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

        // Botões de filtro
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filters.setOpaque(false);

        JLabel filterLabel = new JLabel("FILTROS:");
        filterLabel.setFont(loadFont("Monospaced", Font.BOLD, 11));
        filterLabel.setForeground(TEXT_MUTED);
        filters.add(filterLabel);

        btnTranslate = makeFilterButton("Transladar imagem", "TRANSLADAR");

        filters.add(btnTranslate);

        bar.add(filters, BorderLayout.CENTER);

        // Botões de ação (carregar / salvar)
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

    // ── Fábrica de botões de filtro ─────────────────────────────────────────────
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
        btn.setPreferredSize(new Dimension(200, 36));

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
                Color bg = getModel().isRollover()
                    ? color.brighter()
                    : color;
                g2.setColor(bg);
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

    // ── Lógica: carregar imagem ─────────────────────────────────────────────────
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

    // ── Lógica: salvar imagem ───────────────────────────────────────────────────
    private void saveImage() {
        if (filteredImage == null) {
            JOptionPane.showMessageDialog(this,
                "Aplique um filtro antes de salvar.", "Aviso",
                JOptionPane.WARNING_MESSAGE);
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

    // ── Lógica: aplicar filtro ──────────────────────────────────────────────────
    private void applyFilter(String filterKey) {
        if (originalImage == null) {
            JOptionPane.showMessageDialog(this,
                "Carregue uma imagem primeiro.", "Aviso",
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        activeFilter = filterKey;
        repaintButtons();

        // Roda em thread separada para não travar a UI
        SwingWorker<BufferedImage, Void> worker = new SwingWorker<>() {
            @Override protected BufferedImage doInBackground() {
                return switch (filterKey) {
                    case "TRANSLADAR" -> translateImage(originalImage);
                    default          -> originalImage;
                };
            }
            @Override protected void done() {
                try {
                    filteredImage = get();
                    displayImage(filteredImage, false);
                    updateStatus("Filtro aplicado: " + filterKey);
                } catch (Exception ex) {
                    logger.log(java.util.logging.Level.SEVERE, "Erro no filtro", ex);
                }
            }
        };
        worker.execute();
    }

    // ── Filtros ────────────────────────────────────────────────────────────────    
    private BufferedImage translateImage(BufferedImage src) {
        int add = 150;

        double[][] matrizTranslacao = {
            {add, 0, 0},
            {0, add, 0},
            {0, 0, add}
        };
        
        BufferedImage out = deepCopy(src);
        
        for (int y = 0; y < src.getHeight(); y++) {
           for (int x = 0; x < src.getWidth(); x++) {

               // Vetor homogêneo do pixel original
               double[] pixel = {x, y, 1};

               // Multiplica matriz * pixel
               double[] resultado = new double[3];
               for (int i = 0; i < 3; i++) {
                   for (int j = 0; j < 3; j++) {
                       resultado[i] += matrizTranslacao[i][j] * pixel[j];
                   }
               }

               int novoX = (int) resultado[0];
               int novoY = (int) resultado[1];

               if (novoX < src.getWidth() && novoY < src.getHeight()) {
                   out.setRGB(novoX, novoY, src.getRGB(x, y));
               }
           }
   }

        return out;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────
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

        // Escala mantendo proporção
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
        // O centro do card é o imageArea
        for (Component c : card.getComponents()) {
            if (c instanceof JPanel && ((JPanel) c).getLayout() instanceof GridBagLayout)
                return (JPanel) c;
        }
        return null;
    }

    private void updateStatus(String text) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(text));
    }

    private void repaintButtons() {
        SwingUtilities.invokeLater(() -> {
            btnTranslate.repaint();
        });
    }

    private Font loadFont(String name, int style, int size) {
        return new Font(name, style, size);
    }
}