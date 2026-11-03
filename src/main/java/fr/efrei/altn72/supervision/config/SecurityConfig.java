package fr.efrei.altn72.supervision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

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
				// Deux correspondances : une seule serait appliquée à toutes les requêtes.
				// L'API est la première, donc elle répond 401 ; les pages retombent sur /login.
				.exceptionHandling(exceptions -> exceptions
						.defaultAuthenticationEntryPointFor(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
								PathPatternRequestMatcher.withDefaults().matcher("/api/**"))
						.defaultAuthenticationEntryPointFor(new LoginUrlAuthenticationEntryPoint("/login"),
								PathPatternRequestMatcher.withDefaults().matcher("/**")))
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
