package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.ChangePasswordRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginRequest;
import edu.itesm.accelerated_drug_design_backend.dto.LoginResponse;
import edu.itesm.accelerated_drug_design_backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth", description = "Authentication and current user")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	@Operation(summary = "Login", description = "Authenticate with username and password. Returns a JWT for Authorization header.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
			@ApiResponse(responseCode = "400", description = "Invalid request"),
			@ApiResponse(responseCode = "401", description = "Invalid credentials"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
	@SecurityRequirements
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResponse response = authService.login(request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/me")
	@Operation(summary = "Current user", description = "Returns the currently authenticated user (requires JWT).")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Success", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
			@ApiResponse(responseCode = "401", description = "Unauthorized"),
			@ApiResponse(responseCode = "500", description = "Server error")
	})
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
