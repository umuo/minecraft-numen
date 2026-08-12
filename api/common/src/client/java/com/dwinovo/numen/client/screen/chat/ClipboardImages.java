package com.dwinovo.numen.client.screen.chat;

import com.dwinovo.numen.agent.llm.InputImage;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Desktop clipboard image detection and bounded in-memory encoding. */
final class ClipboardImages {

    static final int MAX_EDGE = 1536;
    static final int MAX_BYTES = 2 * 1024 * 1024;

    private ClipboardImages() {}

    /** Cheap probe used before the normal text-paste path gets a chance to run. */
    static boolean hasImage() {
        try {
            Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (t == null) return false;
            if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) return true;
            if (!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false;
            return firstImagePath(fileList(t)) != null;
        } catch (Exception | LinkageError unavailable) {
            // AWT can be absent on some alternative launchers. Text paste must
            // continue working there, so an unavailable image clipboard is "no image".
            return false;
        }
    }

    /** Read the current clipboard and encode it for an LLM request. */
    static InputImage read() throws Exception {
        Transferable t = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if (t == null) throw new IOException("剪贴板为空");
        BufferedImage image = null;
        if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            Object value = t.getTransferData(DataFlavor.imageFlavor);
            if (value instanceof Image awt) image = buffered(awt);
        }
        if (image == null && t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            Path path = firstImagePath(fileList(t));
            if (path != null) image = ImageIO.read(path.toFile());
        }
        if (image == null) throw new IOException("剪贴板中没有可读取的图片");
        return encode(image);
    }

    /** Package-private for headless codec tests. */
    static InputImage encode(BufferedImage source) throws IOException {
        if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
            throw new IOException("图片尺寸无效");
        }
        BufferedImage scaled = scale(source, MAX_EDGE);
        byte[] png = writePng(scaled);
        if (png.length <= MAX_BYTES) return new InputImage("image/png", png);

        // Screenshots often compress poorly as PNG. JPEG keeps the request
        // bounded while preserving enough detail for scene understanding.
        BufferedImage rgb = rgb(scaled);
        float[] qualities = {0.88f, 0.76f, 0.64f, 0.52f};
        byte[] last = null;
        for (float quality : qualities) {
            last = writeJpeg(rgb, quality);
            if (last.length <= MAX_BYTES) return new InputImage("image/jpeg", last);
        }

        // Extremely noisy images can still exceed the cap. Reduce dimensions
        // rather than silently sending an unbounded payload.
        int edge = MAX_EDGE;
        while (last != null && last.length > MAX_BYTES && edge > 512) {
            edge = Math.max(512, edge * 3 / 4);
            rgb = rgb(scale(source, edge));
            last = writeJpeg(rgb, 0.64f);
        }
        if (last == null || last.length > MAX_BYTES) {
            throw new IOException("图片压缩后仍超过 2 MB");
        }
        return new InputImage("image/jpeg", last);
    }

    private static BufferedImage scale(BufferedImage source, int maxEdge) {
        int w = source.getWidth();
        int h = source.getHeight();
        double ratio = Math.min(1.0, (double) maxEdge / Math.max(w, h));
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        if (nw == w && nh == h) return source;
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage buffered(Image source) throws IOException {
        int w = source.getWidth(null);
        int h = source.getHeight(null);
        if (w <= 0 || h <= 0) throw new IOException("剪贴板图片尚未加载完成");
        if (source instanceof BufferedImage b) return b;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static BufferedImage rgb(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.drawImage(source, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] writePng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) throw new IOException("PNG 编码器不可用");
        return out.toByteArray();
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG 编码器不可用");
        ImageWriter writer = writers.next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static List<java.io.File> fileList(Transferable t) throws Exception {
        Object value = t.getTransferData(DataFlavor.javaFileListFlavor);
        return value instanceof List<?> list ? (List<java.io.File>) list : List.of();
    }

    private static Path firstImagePath(List<java.io.File> files) {
        for (java.io.File file : files) {
            Path path = file.toPath();
            if (!Files.isRegularFile(path)) continue;
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".gif") || name.endsWith(".webp")) {
                return path;
            }
        }
        return null;
    }
}
