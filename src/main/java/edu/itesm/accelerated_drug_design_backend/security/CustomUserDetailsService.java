package edu.itesm.accelerated_drug_design_backend.security;

import edu.itesm.accelerated_drug_design_backend.entity.AppUser;
import edu.itesm.accelerated_drug_design_backend.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public CustomUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		AppUser appUser = userRepository.findByUsername(username)
				.orElseGet(() -> userRepository.findByEmail(username)
						.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)));
		var authorities = appUser.getRoles().stream()
				.map(r -> new SimpleGrantedAuthority("ROLE_" + r.getName()))
				.collect(Collectors.toList());
		UserDetails ud = User.builder()
				.username(appUser.getUsername())
				.password(appUser.getPasswordHash())
				.disabled(!appUser.isEnabled())
				.authorities(authorities)
				.build();
		return ud;
	}
}
