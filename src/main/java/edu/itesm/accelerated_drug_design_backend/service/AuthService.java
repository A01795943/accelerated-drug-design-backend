package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.ChangePasswordRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginResponse;
import edu.itesm.accelerated_drug_design_backend.entity.AppUser;
import edu.itesm.accelerated_drug_design_backend.repository.UserRepository;
import edu.itesm.accelerated_drug_design_backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuthenticationManager authenticationManager;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			JwtService jwtService, AuthenticationManager authenticationManager) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.authenticationManager = authenticationManager;
	}

	public LoginResponse login(LoginRequest request) {
		AppUser user = userRepository.findByUsername(request.username())
				.orElseThrow(() -> new IllegalArgumentException("Usuario o contraseña incorrectos"));
		authenticationManager.authenticate(
				new UsernamePasswordAuthenticationToken(user.getUsername(), request.password())
		);
		String token = jwtService.generateToken(user);
		return buildResponse(user, token);
	}

	public LoginResponse getCurrentUser(String username) {
		AppUser user = userRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		return buildResponse(user, null);
	}

	private static LoginResponse buildResponse(AppUser user, String token) {
		String role = user.getRoles().stream()
				.map(r -> r.getName())
				.findFirst()
				.orElse("USER");
		return new LoginResponse(token, user.getId(), user.getUsername(), user.getEmail(), role);
	}

	@Transactional
	public void changePassword(String username, ChangePasswordRequest request) {
		AppUser user = userRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User not found"));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new IllegalArgumentException("Current password is incorrect");
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}
}
