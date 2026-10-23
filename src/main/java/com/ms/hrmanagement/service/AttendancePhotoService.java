package com.ms.hrmanagement.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttendancePhotoService {
    public byte[] compress(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Choose a JPEG or PNG photo up to 5 MB");
        }
        try (var input = file.getInputStream(); ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new IllegalArgumentException("The file is not a valid photo");
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) {
                    throw new IllegalArgumentException("Only JPEG and PNG photos are supported");
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 50_000_000) {
                    throw new IllegalArgumentException("Photo dimensions are too large");
                }
                ImageReadParam params = reader.getDefaultReadParam();
                int sample = Math.max(1, (int) Math.ceil(Math.max(width, height) / 1024.0));
                params.setSourceSubsampling(sample, sample, 0, 0);
                BufferedImage source = reader.read(0, params);
                BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                graphics.drawImage(source, 0, 0, null);
                graphics.dispose();
                source.flush();
                try {
                    for (float quality : new float[]{0.8f, 0.6f, 0.4f}) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
                        try (var output = new MemoryCacheImageOutputStream(bytes)) {
                            writer.setOutput(output);
                            ImageWriteParam options = writer.getDefaultWriteParam();
                            options.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                            options.setCompressionQuality(quality);
                            writer.write(null, new javax.imageio.IIOImage(rgb, null, null), options);
                        } finally {
                            writer.dispose();
                        }
                        if (bytes.size() <= 200 * 1024) return bytes.toByteArray();
                    }
                } finally {
                    rgb.flush();
                }
                throw new IllegalArgumentException("Photo is too detailed. Choose a smaller photo");
            } finally {
                reader.dispose();
            }
        }
    }
}
