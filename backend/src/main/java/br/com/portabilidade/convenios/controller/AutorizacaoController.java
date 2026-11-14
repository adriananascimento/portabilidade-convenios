package br.com.portabilidade.convenios.controller;

import br.com.portabilidade.convenios.dto.AutorizacaoRespostaDTO;
import br.com.portabilidade.convenios.dto.CriarAutorizacaoRequestDTO;
import br.com.portabilidade.convenios.service.AutorizacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/autorizacoes")
public class AutorizacaoController {

    private final AutorizacaoService service;

    public AutorizacaoController(AutorizacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AutorizacaoRespostaDTO> criarAutorizacao(@RequestBody CriarAutorizacaoRequestDTO request) {
        return service.criarAutorizacao(request);
    }
}
