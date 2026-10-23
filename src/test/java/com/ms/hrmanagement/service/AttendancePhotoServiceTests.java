package com.ms.hrmanagement.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class AttendancePhotoServiceTests {
    private final AttendancePhotoService service = new AttendancePhotoService();

    @Test
    void compressesAndReencodesWithoutTrustingFilenameOrMimeType() throws Exception {
        var image = new BufferedImage(1800, 1200, BufferedImage.TYPE_INT_RGB);
        var bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); image.flush();
        byte[] compressed = service.compress(new MockMultipartFile("photo", "photo.png", "image/png", bytes.toByteArray()));
        var result = ImageIO.read(new ByteArrayInputStream(compressed));
        assertNotNull(result); assertTrue(result.getWidth() <= 1024); assertTrue(compressed.length <= 200 * 1024);
        assertEquals(0xff, compressed[0] & 0xff); assertEquals(0xd8, compressed[1] & 0xff);
    }

    @Test
    void rejectsEmptyFakeAndOversizedPhotos() {
        assertThrows(IllegalArgumentException.class, () -> service.compress(new MockMultipartFile("photo", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> service.compress(new MockMultipartFile("photo", "fake.jpg", "image/jpeg", "not-an-image".getBytes())));
        assertThrows(IllegalArgumentException.class, () -> service.compress(new MockMultipartFile("photo", new byte[5 * 1024 * 1024 + 1])));
    }
}
