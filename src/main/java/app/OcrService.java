package app;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.leptonica.PIX;
import org.bytedeco.tesseract.TessBaseAPI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.bytedeco.leptonica.global.leptonica.pixDestroy;
import static org.bytedeco.leptonica.global.leptonica.pixReadMem;

public class OcrService {
    private final TessBaseAPI api = new TessBaseAPI();

    public OcrService() throws IOException {
        Path dir = asciiDir().resolve("scan-ocr-tessdata");
        Files.createDirectories(dir);
        for (String lang : new String[]{"rus", "eng"}) {
            try (InputStream in = getClass().getResourceAsStream("/tessdata/" + lang + ".traineddata")) {
                if (in == null) throw new IOException("Нет ресурса tessdata/" + lang + ".traineddata");
                Files.copy(in, dir.resolve(lang + ".traineddata"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (api.Init(dir.toString(), "rus") != 0) {
            throw new IOException("Tesseract не смог загрузить язык rus из " + dir);
        }
    }

    public synchronized String recognize(BufferedImage img) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        BytePointer data = new BytePointer(bos.toByteArray());
        PIX pix = pixReadMem(data, bos.size());
        if (pix == null) throw new IOException("Leptonica не смог прочитать изображение");
        BytePointer out = null;
        try {
            api.SetImage(pix);
            out = api.GetUTF8Text();
            return out.getString("UTF-8");
        } finally {
            if (out != null) out.deallocate();
            pixDestroy(pix);
            data.deallocate();
        }
    }

    private static Path asciiDir() throws IOException {
        String[] candidates = {System.getenv("ProgramData"), System.getenv("PUBLIC"),
                System.getProperty("java.io.tmpdir"), System.getProperty("user.dir")};
        for (String c : candidates) {
            if (c == null) continue;
            File f = new File(c);
            if (StandardCharsets.US_ASCII.newEncoder().canEncode(f.getAbsolutePath()) && f.isDirectory() && f.canWrite()) {
                return f.toPath();
            }
        }
        throw new IOException("Не найден ASCII-путь для tessdata");
    }
}