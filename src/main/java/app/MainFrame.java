package app;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame {
    private final JTextField pathField = new JTextField();
    private final JButton browseBtn = new JButton("Выбрать…");
    private final JButton runBtn = new JButton("Распознать");
    private final DefaultListModel<Scan> model = new DefaultListModel<>();
    private final JList<Scan> list = new JList<>(model);
    private final JLabel preview = new JLabel();
    private final JTextArea textArea = new JTextArea();
    private final JProgressBar progress = new JProgressBar();
    private final JLabel status = new JLabel(" ");
    private final File baseDir;
    private OcrService ocr;

    public MainFrame(File baseDir) {
        super("Распознавание сканов");
        this.baseDir = baseDir;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);

        File images = new File(baseDir, "images");
        pathField.setText((images.isDirectory() ? images : baseDir).getAbsolutePath());

        JPanel top = new JPanel(new BorderLayout(6, 6));
        top.add(pathField, BorderLayout.CENTER);
        JPanel topBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        topBtns.add(browseBtn);
        topBtns.add(runBtn);
        top.add(topBtns, BorderLayout.EAST);
        top.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        JSplitPane right = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(preview), new JScrollPane(textArea));
        right.setResizeWeight(0.5);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(list), right);
        main.setDividerLocation(200);

        JButton copyBtn = new JButton("Копировать");
        JButton txtBtn = new JButton("Сохранить TXT");
        JButton docxBtn = new JButton("Сохранить DOCX");
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        bottom.add(copyBtn);
        bottom.add(txtBtn);
        bottom.add(docxBtn);
        bottom.add(progress);
        bottom.add(status);

        add(top, BorderLayout.NORTH);
        add(main, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        browseBtn.addActionListener(e -> browse());
        runBtn.addActionListener(e -> run());
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) showSelected();
        });
        copyBtn.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(allText()), null));
        txtBtn.addActionListener(e -> saveTxt());
        docxBtn.addActionListener(e -> saveDocx());
    }

    private void browse() {
        JFileChooser fc = new JFileChooser(pathField.getText());
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private List<File> collectInputs(File p) {
        if (p.isFile()) return List.of(p);
        File[] files = p.listFiles(f -> {
            String n = f.getName().toLowerCase();
            return f.isFile() && (n.endsWith(".pdf") || n.endsWith(".jpg") || n.endsWith(".jpeg"));
        });
        if (files == null) return List.of();
        Arrays.sort(files);
        return Arrays.asList(files);
    }

    private void run() {
        File input = new File(pathField.getText());
        if (!input.exists()) {
            JOptionPane.showMessageDialog(this, "Путь не найден: " + input);
            return;
        }
        model.clear();
        textArea.setText("");
        preview.setIcon(null);
        setBusy(true);

        SwingWorker<Void, Scan> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (ocr == null) ocr = new OcrService();
                PdfImageExtractor extractor = new PdfImageExtractor();
                Set<File> images = new LinkedHashSet<>();
                for (File f : collectInputs(input)) {
                    if (f.getName().toLowerCase().endsWith(".pdf")) images.addAll(extractor.extract(f));
                    else images.add(f);
                }
                int i = 0;
                for (File img : images) {
                    BufferedImage bi = ImageIO.read(img);
                    publish(new Scan(img, bi == null ? "" : ocr.recognize(bi)));
                    setProgress(++i * 100 / images.size());
                }
                return null;
            }

            @Override
            protected void process(List<Scan> chunks) {
                chunks.forEach(model::addElement);
                if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    get();
                    status.setText("Готово: " + model.size());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(MainFrame.this,
                            String.valueOf(ex.getCause() != null ? ex.getCause() : ex),
                            "Ошибка", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        w.addPropertyChangeListener(ev -> {
            if ("progress".equals(ev.getPropertyName())) {
                progress.setIndeterminate(false);
                progress.setValue((Integer) ev.getNewValue());
            }
        });
        w.execute();
    }

    private void setBusy(boolean busy) {
        runBtn.setEnabled(!busy);
        browseBtn.setEnabled(!busy);
        progress.setIndeterminate(busy);
        progress.setValue(0);
        status.setText(busy ? "Распознавание…" : status.getText());
    }

    private void showSelected() {
        Scan s = list.getSelectedValue();
        if (s == null) return;
        textArea.setText(s.text());
        textArea.setCaretPosition(0);
        try {
            BufferedImage bi = ImageIO.read(s.file());
            int w = Math.max(300, preview.getParent() != null ? preview.getParent().getWidth() - 20 : 500);
            int h = (int) Math.round(w * (double) bi.getHeight() / bi.getWidth());
            preview.setIcon(new ImageIcon(bi.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
        } catch (Exception ex) {
            preview.setIcon(null);
        }
    }

    private List<Scan> scans() {
        return Collections.list(model.elements());
    }

    private String allText() {
        StringBuilder sb = new StringBuilder();
        for (Scan s : scans()) sb.append("=== ").append(s.file().getName()).append(" ===\n").append(s.text()).append("\n");
        return sb.toString();
    }

    private File chooseSave(String ext) {
        File in = new File(pathField.getText());
        String base = in.getName().replaceFirst("(?i)\\.(pdf|jpe?g)$", "");
        File dir = in.isDirectory() ? in : in.getParentFile();
        JFileChooser fc = new JFileChooser(dir);
        fc.setSelectedFile(new File(dir, base + "." + ext));
        return fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION ? fc.getSelectedFile() : null;
    }

    private void saveTxt() {
        File f = chooseSave("txt");
        if (f == null) return;
        try {
            Files.writeString(f.toPath(), allText(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.toString());
        }
    }

    private void saveDocx() {
        File f = chooseSave("docx");
        if (f == null) return;
        try {
            new DocxExporter().export(scans(), f);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.toString());
        }
    }
}