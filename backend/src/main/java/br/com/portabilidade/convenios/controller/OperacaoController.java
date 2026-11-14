package br.com.portabilidade.convenios.controller;

import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.exception.RequisicaoInvalidaException;
import br.com.portabilidade.convenios.service.AutorizacaoService;
import br.com.portabilidade.convenios.service.OperacaoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/operacoes")
public class OperacaoController {

    private final OperacaoService service;

    public OperacaoController(OperacaoService service) {
        this.service = service;
    }

    @GetMapping
    public OperacaoPaginaDTO listar(
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "10") int tamanho) {
        if (pagina < 0 || tamanho < 1 || tamanho > 100) {
            throw new RequisicaoInvalidaException("Página deve ser não negativa e tamanho deve estar entre 1 e 100.");
        }
        return service.listar(pagina, tamanho);
    }

    @PostMapping
    public ResponseEntity<OperacaoRespostaDTO> criarOperacao(@Valid @RequestBody CriarOperacaoRequestDTO request,
                                                             @RequestHeader("Idempotency-Key") UUID chaveIdempotencia) {
        return service.criarOperacao(request, chaveIdempotencia);
    }
}
