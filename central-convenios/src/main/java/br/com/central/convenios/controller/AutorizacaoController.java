package br.com.central.convenios.controller;

import br.com.central.convenios.dto.AutorizacaoRequisicaoDTO;
import br.com.central.convenios.dto.AutorizacaoRespostaDTO;
import br.com.central.convenios.dto.AutorizacoesPaginaDTO;
import br.com.central.convenios.exception.RequisicaoInvalidaException;
import br.com.central.convenios.service.AutorizacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/autorizacoes")
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
    public ResponseEntity<AutorizacaoRespostaDTO> registrar(@Valid @RequestBody AutorizacaoRequisicaoDTO requisicao) {
        var resultado = service.registrar(requisicao);
        return ResponseEntity.status(resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.autorizacao());
    }
}
