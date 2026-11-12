package com.yachiqo.spotgo.parking.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yachiqo.spotgo.iam.domain.model.valueobjects.UserRole;
import com.yachiqo.spotgo.iam.infrastructure.security.SpotgoUserPrincipal;
import com.yachiqo.spotgo.parking.domain.model.aggregates.Reservation;
import com.yachiqo.spotgo.parking.infrastructure.persistence.jpa.repositories.ReservationRepository;
import com.yachiqo.spotgo.billing.domain.model.valueobjects.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Duration;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ReservationReceiptService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private final ReservationRepository reservations;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final String apiKey;
    private final String fromEmail;
    private final String resendUrl;
    private final boolean emailEnabled;

    public ReservationReceiptService(ReservationRepository reservations, Clock clock,
            @Value("${app.password-reset.resend-api-key:${RESEND_API_KEY:}}") String apiKey,
            @Value("${app.password-reset.from-email:${APP_PASSWORD_RESET_FROM_EMAIL:}}") String fromEmail,
            @Value("${app.password-reset.resend-url:https://api.resend.com/emails}") String resendUrl,
            @Value("${app.receipt-email.enabled:${RECEIPT_EMAIL_ENABLED:false}}") boolean emailEnabled) {
        this.reservations = reservations;
        this.clock = clock;
        this.http = HttpClient.newHttpClient();
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.resendUrl = resendUrl;
        this.emailEnabled = emailEnabled;
    }

    public byte[] pdf(SpotgoUserPrincipal actor, UUID reservationId) {
        return render(authorized(actor, reservationId));
    }

    public void email(SpotgoUserPrincipal actor, UUID reservationId) {
        if (actor == null || actor.getRole() != UserRole.DRIVER)
            throw new AccessDeniedException("Only the reservation's driver can email the receipt.");
        var reservation = authorized(actor, reservationId);
        if (!emailEnabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Receipt email is available only when enabled for deployment.");
        String recipient = reservation.getProfile().getAccount().getEmail();
        if (apiKey == null || apiKey.isBlank() || fromEmail == null || fromEmail.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Receipt email is not configured.");
        var payload = new ResendEmailRequest(fromEmail, List.of(recipient),
                "SpotGo - informational parking receipt",
                "Attached is the informational receipt for your parking reservation. It is not a tax receipt or an invoice.",
                List.of(new Attachment("spotgo-reservation-" + reservation.getId() + ".pdf",
                        Base64.getEncoder().encodeToString(render(reservation)))));
        HttpRequest request = HttpRequest.newBuilder(URI.create(resendUrl))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Receipt email provider rejected the request.");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not send the receipt email.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Receipt email was interrupted.", e);
        }
    }

    private Reservation authorized(SpotgoUserPrincipal actor, UUID id) {
        var reservation = reservations.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found."));
        if (actor == null) throw new AccessDeniedException("Authentication is required.");
        if (actor.getRole() == UserRole.DRIVER) {
            if (!actor.getAccountId().equals(reservation.getProfile().getAccountId()))
                throw new AccessDeniedException("The reservation belongs to another driver.");
        } else if (actor.getRole() == UserRole.PARKING_ADMIN) {
            if (!reservation.getTenantId().equals(actor.getTenantId()))
                throw new AccessDeniedException("The reservation is outside your tenant.");
        } else if (actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("This role cannot access reservation receipts.");
        }
        if (reservation.getPaymentId() == null || reservation.getPayment() == null ||
                reservation.getPayment().getStatus() != PaymentStatus.APPROVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A receipt is available only after payment approval.");
        return reservation;
    }

    private byte[] render(Reservation r) {
        var p = r.getPayment();
        List<String> lines = List.of(
                "INFORMATIONAL RECEIPT - NOT A TAX DOCUMENT",
                "Reservation: " + r.getId(),
                "Parking facility: " + r.getTenant().getName(),
                "Zone / space: " + r.getZone().getName() + " / " + r.getSpot().getCode(),
                "Vehicle: " + r.getVehicle().getPlate() + " - " + r.getVehicle().getMake() + " " + r.getVehicle().getModel(),
                "Driver: " + r.getProfile().getFirstName() + " " + r.getProfile().getLastName(),
                "Start: " + r.getStartAt(),
                "End: " + r.getEndAt(),
                "Amount paid: PEN " + r.getAmount().setScale(2, RoundingMode.HALF_UP),
                "Payment: " + p.getId() + " - " + p.getStatus(),
                "Issued: " + clock.instant(),
                "This document is for information only; it is not an invoice or tax receipt."
        );
        StringBuilder stream = new StringBuilder("BT\n/F1 15 Tf\n50 790 Td\n");
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) stream.append("0 -30 Td\n/F1 ").append(i == 0 ? 15 : 10).append(" Tf\n");
            stream.append('(').append(escapePdf(ascii(lines.get(i)))).append(") Tj\n");
        }
        stream.append("ET\n");
        byte[] content = stream.toString().getBytes(StandardCharsets.US_ASCII);
        List<byte[]> objects = List.of(
                bytes("<< /Type /Catalog /Pages 2 0 R >>"),
                bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"),
                bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"),
                concat(bytes("<< /Length " + content.length + " >>\nstream\n"), content, bytes("endstream"))
        );
        return pdf(objects);
    }

    private byte[] pdf(List<byte[]> objects) {
        try {
            var out = new ByteArrayOutputStream();
            out.write(bytes("%PDF-1.4\n"));
            List<Integer> offsets = new ArrayList<>();
            for (int i = 0; i < objects.size(); i++) {
                offsets.add(out.size());
                out.write(bytes((i + 1) + " 0 obj\n"));out.write(objects.get(i));out.write(bytes("\nendobj\n"));
            }
            int xref = out.size();out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n"));
            for (int offset : offsets) out.write(bytes(String.format(Locale.ROOT, "%010d 00000 n \n", offset)));
            out.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
            return out.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("Could not create reservation PDF.", e); }
    }

    private String ascii(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[^\\x20-\\x7E]", "?");
    }
    private String escapePdf(String value) { return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); }
    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.US_ASCII); }
    private byte[] concat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        try { for (byte[] part : parts) out.write(part); } catch (IOException e) { throw new IllegalStateException(e); }
        return out.toByteArray();
    }
    private String toJson(ResendEmailRequest request) {
        try { return mapper.writeValueAsString(request); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Could not serialize receipt email.", e); }
    }

    private record Attachment(String filename, String content) {}
    private record ResendEmailRequest(String from, List<String> to, String subject, String text, List<Attachment> attachments) {}
}
