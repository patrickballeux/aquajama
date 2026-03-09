package com.pb.aquajama.ollama;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class ImageEncoder {

    public static List<String> encode(List<BufferedImage> images) {

        List<String> result = new ArrayList<>();

        if (images == null) return result;

        for (BufferedImage img : images) {

            if (img == null) continue;

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                ImageIO.write(img, "png", baos);

                byte[] bytes = baos.toByteArray();

                result.add(Base64.getEncoder().encodeToString(bytes));

            } catch (Exception ignored) {
            }
        }

        return result;
    }
}