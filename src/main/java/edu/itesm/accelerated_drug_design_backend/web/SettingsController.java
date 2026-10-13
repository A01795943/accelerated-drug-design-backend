package edu.itesm.accelerated_drug_design_backend.web;

import edu.itesm.accelerated_drug_design_backend.dto.SettingView;
import edu.itesm.accelerated_drug_design_backend.dto.UpdateSettingRequest;
import edu.itesm.accelerated_drug_design_backend.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Settings", description = "Admin-editable application settings")
public class SettingsController {

	private final SettingsService settingsService;

	public SettingsController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping
	@Operation(summary = "List defined settings with their current value")
	public List<SettingView> list() {
		return settingsService.listDefined();
	}

	@PutMapping("/{key:.+}")
	@Operation(summary = "Validate and store one setting")
	public SettingView update(
			@PathVariable("key") String key,
			@RequestBody UpdateSettingRequest request,
			@AuthenticationPrincipal UserDetails user) {
		String updatedBy = user == null || user.getUsername() == null || user.getUsername().isBlank()
				? "unknown"
				: user.getUsername();
		String value = request == null ? null : request.value();
		return settingsService.updateDefined(key, value, updatedBy);
	}
}
