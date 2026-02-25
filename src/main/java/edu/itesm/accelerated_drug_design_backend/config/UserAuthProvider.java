package edu.itesm.accelerated_drug_design_backend.config;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

@Component
public class UserAuthProvider {

	@Value("${app.jwt.secret}")
	private String secret;

	public Authentication validateToken(String token) {
		// app.jwt.secret es Base64 (openssl rand -base64 64)
		byte[] secretBytes = Base64.getDecoder().decode(secret);

		// Tu JwtService firma con HS512
		Algorithm algorithm = Algorithm.HMAC512(secretBytes);

		JWTVerifier verifier = JWT.require(algorithm).build();
		DecodedJWT decoded = verifier.verify(token);

		String username = decoded.getSubject(); // "sub"
		String rolesCsv = decoded.getClaim("roles").asString();
		if (rolesCsv == null) rolesCsv = "";

		var authorities = Arrays.stream(rolesCsv.split(","))
				.map(String::trim)
				.filter(s -> !s.isBlank())
				.map(SimpleGrantedAuthority::new)
				.collect(Collectors.toList());

		// Principal como UserDetails (Spring Security)
		UserDetails userDetails = User.builder()
				.username(username)
				// Password no se necesita aquí porque ya vienes autenticado por JWT
				.password("")
				.authorities(authorities)
				.build();

		return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
	}
}