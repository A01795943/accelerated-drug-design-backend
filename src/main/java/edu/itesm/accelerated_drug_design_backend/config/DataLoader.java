package edu.itesm.accelerated_drug_design_backend.config;

import edu.itesm.accelerated_drug_design_backend.entity.AppUser;
import edu.itesm.accelerated_drug_design_backend.entity.Role;
import edu.itesm.accelerated_drug_design_backend.repository.UserRepository;
import edu.itesm.accelerated_drug_design_backend.repository.RoleRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements ApplicationRunner {

	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;

	public DataLoader(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (userRepository.findByUsername("admin").isPresent()) {
			return;
		}
		Role adminRole = roleRepository.findByName("ADMIN")
				.orElseGet(() -> roleRepository.save(createRole("ADMIN")));
		roleRepository.findByName("USER").orElseGet(() -> roleRepository.save(createRole("USER")));
		AppUser admin = new AppUser();
		admin.setUsername("admin");
		admin.setEmail("admin@admin.com");
		admin.setPasswordHash(passwordEncoder.encode("admin"));
		admin.setEnabled(true);
		admin.getRoles().add(adminRole);
		userRepository.save(admin);
	}

	private static Role createRole(String name) {
		Role r = new Role();
		r.setName(name);
		return r;
	}
}
