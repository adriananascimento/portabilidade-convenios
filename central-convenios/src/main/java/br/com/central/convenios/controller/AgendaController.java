package br.com.central.convenios.controller;

import br.com.central.convenios.dto.AgendaRespostaDTO;
import br.com.central.convenios.service.AgendaService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/beneficiarios")
public class AgendaController {
    private final AgendaService service;

    public AgendaController(AgendaService service) {
        this.service = service;
    }

    @GetMapping("/{beneficiarioId}/agenda")
    public AgendaRespostaDTO consultar(@PathVariable String beneficiarioId) {
        return service.consultar(beneficiarioId);
    }
}
