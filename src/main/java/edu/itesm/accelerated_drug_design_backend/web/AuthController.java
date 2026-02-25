package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.ChangePasswordRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginResponse;
import edu.itesm.accelerated_drug_design_backend.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/me")
	public ResponseEntity<LoginResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
		LoginResponse response = authService.getCurrentUser(userDetails.getUsername());
		return ResponseEntity.ok(response);
	}

	@PostMapping("/change-password")
	public ResponseEntity<Void> changePassword(
			@AuthenticationPrincipal UserDetails userDetails,
			@Valid @RequestBody ChangePasswordRequest request) {
		authService.changePassword(userDetails.getUsername(), request);
		return ResponseEntity.ok().build();
	}
}
