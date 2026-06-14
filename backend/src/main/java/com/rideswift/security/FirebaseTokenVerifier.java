package com.rideswift.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Locator;
import io.jsonwebtoken.Header;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Verifies a Firebase ID token without the (heavyweight) Firebase Admin SDK or a
 * service-account key. A Firebase ID token is just an RS256 JWT signed by Google;
 * we validate its signature against Google's published x509 certs and check the
 * issuer/audience match this project. That's all token verification needs.
 */
@Component
public class FirebaseTokenVerifier {

    private static final String CERTS_URL =
            "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";

    /** What we trust out of a verified token. */
    public record FirebaseUser(String uid, String phoneNumber, String name) {
    }

    private final boolean enabled;
    private final String projectId;
    private final RestClient http = RestClient.create();

    private volatile Map<String, PublicKey> certs = Map.of();
    private volatile Instant certsExpireAt = Instant.EPOCH;

    public FirebaseTokenVerifier(@Value("${rideswift.firebase.enabled:false}") boolean enabled,
                                 @Value("${rideswift.firebase.project-id:}") String projectId) {
        this.enabled = enabled;
        this.projectId = projectId;
    }

    public boolean isEnabled() {
        return enabled && !projectId.isBlank();
    }

    public FirebaseUser verify(String idToken) {
        if (!isEnabled()) {
            throw new BadCredentialsException("Firebase login is not configured");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(keyLocator())
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (BadCredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new BadCredentialsException("Invalid Firebase token: " + e.getMessage());
        }

        String expectedIssuer = "https://securetoken.google.com/" + projectId;
        if (!expectedIssuer.equals(claims.getIssuer())) {
            throw new BadCredentialsException("Firebase token issuer mismatch");
        }
        Set<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(projectId)) {
            throw new BadCredentialsException("Firebase token audience mismatch");
        }
        String uid = claims.getSubject();
        if (uid == null || uid.isBlank()) {
            throw new BadCredentialsException("Firebase token has no subject");
        }
        return new FirebaseUser(uid,
                claims.get("phone_number", String.class),
                claims.get("name", String.class));
    }

    /** Resolves the signing key for a token by its {@code kid} header, refreshing certs on a miss. */
    private Locator<Key> keyLocator() {
        return new Locator<>() {
            @Override
            public Key locate(Header header) {
                String kid = (String) header.get("kid");
                if (kid == null) {
                    throw new BadCredentialsException("Firebase token missing key id");
                }
                PublicKey key = lookup(kid);
                if (key == null) {
                    refreshCerts();
                    key = lookup(kid);
                }
                if (key == null) {
                    throw new BadCredentialsException("Unknown Firebase signing key");
                }
                return key;
            }
        };
    }

    private PublicKey lookup(String kid) {
        if (Instant.now().isAfter(certsExpireAt)) {
            refreshCerts();
        }
        return certs.get(kid);
    }

    @SuppressWarnings("unchecked")
    private synchronized void refreshCerts() {
        try {
            Map<String, String> pemByKid = http.get().uri(CERTS_URL).retrieve().body(Map.class);
            if (pemByKid == null || pemByKid.isEmpty()) {
                return;
            }
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Map<String, PublicKey> fresh = new HashMap<>();
            for (Map.Entry<String, String> e : pemByKid.entrySet()) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(
                        new ByteArrayInputStream(e.getValue().getBytes(StandardCharsets.UTF_8)));
                fresh.put(e.getKey(), cert.getPublicKey());
            }
            this.certs = Map.copyOf(fresh);
            this.certsExpireAt = Instant.now().plus(Duration.ofHours(1));
        } catch (Exception ex) {
            throw new BadCredentialsException("Could not fetch Firebase signing certs: " + ex.getMessage());
        }
    }
}
