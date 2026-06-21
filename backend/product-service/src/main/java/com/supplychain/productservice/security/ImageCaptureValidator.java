package com.supplychain.productservice.security;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

public final class ImageCaptureValidator {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_PIXELS = 16_000_000;
    private ImageCaptureValidator() { }
    public static void validate(byte[] image) {
        if (image == null || image.length == 0 || image.length > MAX_BYTES) throw new IllegalArgumentException("Image must be between 1 byte and 10 MB");
        boolean jpeg = image.length >= 3 && (image[0] & 0xff) == 0xff && (image[1] & 0xff) == 0xd8 && (image[2] & 0xff) == 0xff;
        boolean png = image.length >= 8 && image[0] == (byte)137 && image[1] == 80 && image[2] == 78 && image[3] == 71 && image[4] == 13 && image[5] == 10 && image[6] == 26 && image[7] == 10;
        if (!jpeg && !png) throw new IllegalArgumentException("Only binary JPEG and PNG captures are accepted");
        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image));
            if (decoded == null || decoded.getWidth() < 16 || decoded.getHeight() < 16 || (long) decoded.getWidth() * decoded.getHeight() > MAX_PIXELS) throw new IllegalArgumentException("Unsupported image dimensions");
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Unable to decode image capture", e); }
    }
}
