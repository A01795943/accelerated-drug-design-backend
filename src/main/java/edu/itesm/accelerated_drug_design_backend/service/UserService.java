package edu.itesm.accelerated_drug_design_backend.service;

import edu.itesm.accelerated_drug_design_backend.dto.CreateUserRequest;
import edu.itesm.accelerated_drug_design_backend.dto.UserResponse;
import edu.itesm.accelerated_drug_design_backend.entity.AppUser;
import edu.itesm.accelerated_drug_design_backend.entity.Role;
import edu.itesm.accelerated_drug_design_backend.repository.UserRepository;
import edu.itesm.accelerated_drug_design_backend.repository.RoleRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public List<UserResponse> findAll() {
		return userRepository.findAll().stream()
				.map(this::toResponse)
				.collect(Collectors.toList());
	}

	public UserResponse create(CreateUserRequest request) {
		if (userRepository.existsByUsername(request.username())) {
			throw new IllegalArgumentException("Username already exists");
		}
		if (userRepository.existsByEmail(request.email())) {
			throw new IllegalArgumentException("Email already exists");
		}
		Role role = roleRepository.findByName(request.role())
				.orElseThrow(() -> new IllegalArgumentException("Invalid role: " + request.role()));
		AppUser user = new AppUser();
		user.setUsername(request.username());
		user.setEmail(request.email());
		user.setPasswordHash(passwordEncoder.encode(request.password()));
		user.setEnabled(true);
		user.getRoles().add(role);
		user = userRepository.save(user);
		return toResponse(user);
	}

	private UserResponse toResponse(AppUser u) {
		String role = u.getRoles().stream()
				.map(Role::getName)
				.findFirst()
				.orElse("USER");
		return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), role, u.isEnabled());
	}
}
