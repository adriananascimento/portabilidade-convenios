package br.com.central.convenios.controller;

import br.com.central.convenios.dto.*;
import br.com.central.convenios.exception.RequisicaoInvalidaException;
import br.com.central.convenios.service.OperacaoService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/operacoes-portabilidade")
public class OperacaoController {
    private final OperacaoService service;

    public OperacaoController(OperacaoService service) { this.service = service; }

    @GetMapping("/{protocolo}")
    public OperacaoConsultaRespostaDTO consultar(@PathVariable UUID protocolo) {
        return service.consultar(protocolo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperacaoRespostaDTO registrar(@RequestHeader("Idempotency-Key") String chave,
                                         @Valid @RequestBody OperacaoRequisicaoDTO requisicao) {
        if (!chave.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new RequisicaoInvalidaException("Idempotency-Key deve ser um UUID");
        }
        return service.registrar(UUID.fromString(chave), requisicao);
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
}
