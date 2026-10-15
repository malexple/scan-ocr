package app;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PdfImageExtractor {

    public List<File> extract(File pdf) throws IOException {
        String base = pdf.getName().replaceFirst("(?i)\\.pdf$", "");
        List<File> result = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            int n = 0;
            for (PDPage page : doc.getPages()) {
                PDResources res = page.getResources();
                if (res == null) continue;
                for (COSName name : res.getXObjectNames()) {
                    PDXObject xo = res.getXObject(name);
                    if (xo instanceof PDImageXObject img) {
                        File out = new File(pdf.getParentFile(), String.format("%s_%02d.jpg", base, ++n));
                        writeJpeg(toRgb(img.getImage()), out);
                        result.add(out);
                    }
                }
            }
        }
        return result;
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = rgb.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage img, File out) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = writer.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(0.95f);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(img, null, null), p);
        } finally {
            writer.dispose();
        }
    }
}