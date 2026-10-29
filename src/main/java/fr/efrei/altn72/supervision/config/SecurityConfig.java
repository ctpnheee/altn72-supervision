package fr.efrei.altn72.supervision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import fr.efrei.altn72.supervision.model.enums.Profil;

/**
 * Authentification par formulaire et restrictions par profil (RG11).
 * Les règles fines (qui peut résoudre, évaluer…) sont posées avec {@code @PreAuthorize} sur les services.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain filtresDeSecurite(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(requetes -> requetes
						.requestMatchers("/login", "/error", "/css/**", "/img/**").permitAll()
						.requestMatchers("/tableau-de-bord/**").hasRole(Profil.RESPONSABLE.name())
						.anyRequest().authenticated())
				.formLogin(formulaire -> formulaire
						.loginPage("/login")
						.defaultSuccessUrl("/", true))
				.logout(deconnexion -> deconnexion
						.logoutUrl("/logout")
						.logoutSuccessUrl("/login?logout")
						.invalidateHttpSession(true));
		return http.build();
	}

	/** Haché BCrypt */
	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
