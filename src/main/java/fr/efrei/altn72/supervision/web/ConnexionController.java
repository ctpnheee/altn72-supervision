package fr.efrei.altn72.supervision.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ConnexionController {

	@GetMapping("/login")
	public String connexion() {
		return "login";
	}
}
