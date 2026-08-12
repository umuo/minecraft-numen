package com.dwinovo.numen.client.screen.chat;

import com.dwinovo.numen.agent.llm.InputImage;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClipboardImagesTest {

    @Test
    void smallClipboardImageStaysPngAndDecodes() throws Exception {
        BufferedImage source = new BufferedImage(320, 180, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, source.getWidth(), source.getHeight());
        g.dispose();

        InputImage encoded = ClipboardImages.encode(source);
        assertEquals("image/png", encoded.mediaType());
        assertTrue(encoded.bytes().length <= ClipboardImages.MAX_BYTES);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded.bytes()));
        assertNotNull(decoded);
        assertEquals(320, decoded.getWidth());
        assertEquals(180, decoded.getHeight());
    }

    @Test
    void oversizedClipboardImageIsScaledToRequestLimit() throws Exception {
        BufferedImage source = new BufferedImage(2400, 1200, BufferedImage.TYPE_INT_RGB);
        InputImage encoded = ClipboardImages.encode(source);
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(encoded.bytes()));
        assertNotNull(decoded);
        assertTrue(Math.max(decoded.getWidth(), decoded.getHeight()) <= ClipboardImages.MAX_EDGE);
        assertTrue(encoded.bytes().length <= ClipboardImages.MAX_BYTES);
    }
}
