package edu.itesm.accelerated_drug_design_backend.security;

import edu.itesm.accelerated_drug_design_backend.entity.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.stream.Collectors;

@Service
public class JwtService {

	@Value("${app.jwt.secret}")
	private String secret;

	@Value("${app.jwt.expiration-ms:86400000}")
	private long expirationMs;

	public String generateToken(AppUser user) {
		String roles = user.getRoles().stream()
				.map(r -> "ROLE_" + r.getName())
				.collect(Collectors.joining(","));
		return Jwts.builder()
				.subject(user.getUsername())
				.claim("userId", user.getId())
				.claim("email", user.getEmail())
				.claim("roles", roles)
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + expirationMs))
				.signWith(getSigningKey(), Jwts.SIG.HS512)
				.compact();
	}

	public String extractUsername(String token) {
		return getClaims(token).getSubject();
	}

	public Long extractUserId(String token) {
		return getClaims(token).get("userId", Long.class);
	}

	public boolean isTokenValid(String token, String username) {
		String sub = extractUsername(token);
		return sub != null && sub.equals(username) && !isTokenExpired(token);
	}

	private boolean isTokenExpired(String token) {
		return getClaims(token).getExpiration().before(new Date());
	}

	private Claims getClaims(String token) {
		return Jwts.parser()
				.verifyWith(getSigningKey())
				.build()
				.parseSignedClaims(token)
				.getPayload();
	}

	/**
	 * Clave HMAC-SHA256 estable: mismo secret siempre produce la misma clave.
	 * HS256 permite 256 bits (32 bytes); así evitamos WeakKeyException y firma consistente.
	 */
	private SecretKey getSigningKey() {
		byte[] keyBytes = Decoders.BASE64.decode(secret);
		return Keys.hmacShaKeyFor(keyBytes);
	}
}
