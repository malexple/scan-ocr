package app;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class DocxExporter {

    public void export(List<Scan> scans, File out) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (int i = 0; i < scans.size(); i++) {
                Scan s = scans.get(i);
                XWPFRun title = doc.createParagraph().createRun();
                title.setBold(true);
                title.setText(s.file().getName());

                BufferedImage bi = ImageIO.read(s.file());
                int w = 450;
                int h = bi == null ? w : (int) Math.round(w * (double) bi.getHeight() / bi.getWidth());
                XWPFRun pic = doc.createParagraph().createRun();
                try (FileInputStream in = new FileInputStream(s.file())) {
                    pic.addPicture(in, XWPFDocument.PICTURE_TYPE_JPEG, s.file().getName(),
                            Units.toEMU(w), Units.toEMU(h));
                }

                for (String line : s.text().split("\\R")) {
                    doc.createParagraph().createRun().setText(line);
                }
                if (i < scans.size() - 1) {
                    doc.createParagraph().createRun().addBreak(BreakType.PAGE);
                }
            }
            try (FileOutputStream fos = new FileOutputStream(out)) {
                doc.write(fos);
            }
        }
    }
}