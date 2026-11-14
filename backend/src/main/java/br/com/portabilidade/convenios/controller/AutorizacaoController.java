package br.com.portabilidade.convenios.controller;

import br.com.portabilidade.convenios.dto.AutorizacaoRespostaDTO;
import br.com.portabilidade.convenios.dto.AutorizacoesPaginaDTO;
import br.com.portabilidade.convenios.dto.CriarAutorizacaoRequestDTO;
import br.com.portabilidade.convenios.exception.RequisicaoInvalidaException;
import br.com.portabilidade.convenios.service.AutorizacaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/autorizacoes")
public class AutorizacaoController {

    private final AutorizacaoService service;

    public AutorizacaoController(AutorizacaoService service) {
        this.service = service;
    }

    @GetMapping
    public AutorizacoesPaginaDTO listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new RequisicaoInvalidaException("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
        }
        return service.listar(pagina, tamanho);
    }

    @PostMapping
    public ResponseEntity<AutorizacaoRespostaDTO> criarAutorizacao(@RequestBody CriarAutorizacaoRequestDTO request) {
        return service.criarAutorizacao(request);
    }
}
