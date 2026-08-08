package com.example.demo.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Image;

import java.awt.image.BufferedImage;
import java.util.Map;

public final class QrCodeImageWriter {

    private QrCodeImageWriter() {
    }

    public static Image createPdfImage(String content, int sizePx) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(
                    content,
                    BarcodeFormat.QR_CODE,
                    sizePx,
                    sizePx,
                    Map.of(EncodeHintType.MARGIN, 1)
            );
            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            return Image.getInstance(bufferedImage, null);
        } catch (Exception ex) {
            throw new IllegalStateException("QR 코드 생성에 실패했습니다.", ex);
        }
    }
}
