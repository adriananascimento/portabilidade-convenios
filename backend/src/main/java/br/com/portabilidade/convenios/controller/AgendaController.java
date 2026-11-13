package br.com.portabilidade.convenios.controller;

import br.com.portabilidade.convenios.dto.AgendaRespostaDTO;
import br.com.portabilidade.convenios.service.AgendaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beneficiarios")
public class AgendaController {

    private final AgendaService agendaService;

    public AgendaController(AgendaService agendaService) {
        this.agendaService = agendaService;
    }

    @GetMapping("/{beneficiarioId}/agenda")
    public ResponseEntity<AgendaRespostaDTO> consultarAgenda(@PathVariable String beneficiarioId) {
        AgendaRespostaDTO resposta = agendaService.consultarAgenda(beneficiarioId);
        return ResponseEntity.ok(resposta);
    }
}
