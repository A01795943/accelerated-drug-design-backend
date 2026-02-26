package edu.itesm.accelerated_drug_design_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {

	private final UserAuthProvider userAuthProvider;

	public JwtAuthFilter(UserAuthProvider userAuthProvider) {
		this.userAuthProvider = userAuthProvider;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getServletPath();
		return path.startsWith("/v3/api-docs")
				|| path.startsWith("/swagger-ui")
				|| path.equals("/swagger-ui.html")
				|| path.startsWith("/swagger-resources")
				|| path.startsWith("/webjars");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);

		if (header != null) {
			String[] authElements = header.split(" ");
			if (authElements.length == 2 && "Bearer".equals(authElements[0])) {
				try {
					SecurityContextHolder.getContext()
							.setAuthentication(userAuthProvider.validateToken(authElements[1]));
				} catch (RuntimeException ex) {
					SecurityContextHolder.clearContext();
					throw ex;
				}
			}
		}
		filterChain.doFilter(request, response);
	}
}
