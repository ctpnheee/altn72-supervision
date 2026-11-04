
package fr.efrei.altn72.supervision.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import fr.efrei.altn72.supervision.service.PrestataireService;
import fr.efrei.altn72.supervision.service.PrestataireService.PrestataireResume;

@RestController
@RequestMapping("/api/prestataires")
public class PrestataireApiController {

    private final PrestataireService prestataireService;

    public PrestataireApiController(
            PrestataireService prestataireService) {
        this.prestataireService = prestataireService;
    }

    @GetMapping
    @PreAuthorize("hasRole('RESPONSABLE')")
    public List<PrestataireResume> lister() {
        return prestataireService.listerPrestataires();
    }
}
