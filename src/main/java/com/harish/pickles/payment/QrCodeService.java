package com.harish.pickles.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * Turns an order's payment details into a scannable UPI QR code, with the order
 * number pre-filled as the transaction note so a customer's UPI app carries it
 * straight through to the payment, instead of the customer typing it in by hand.
 */
@Service
public class QrCodeService {

    private static final int SIZE_PX = 480;

    /** Builds the {@code upi://pay} deep link a UPI app resolves into a payment screen. */
    public String buildUpiUri(PaymentProperties paymentProperties, String orderNumber, BigDecimal amountRupees) {
        return "upi://pay"
                + "?pa=" + encode(paymentProperties.getUpiId())
                + "&pn=" + encode(paymentProperties.getPayeeName())
                + "&am=" + encode(amountRupees.toPlainString())
                + "&cu=INR"
                + "&tn=" + encode(orderNumber)
                + "&tr=" + encode(orderNumber);
    }

    /** Renders the given text as a PNG QR code. */
    public byte[] generatePng(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, SIZE_PX, SIZE_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Failed to generate QR code", ex);
        }
    }

    private static String encode(String value) {
        return UriUtils.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
