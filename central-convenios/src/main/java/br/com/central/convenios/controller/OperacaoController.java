package br.com.central.convenios.controller;

import br.com.central.convenios.dto.*;
import br.com.central.convenios.exception.AssinaturaException;
import br.com.central.convenios.exception.RequisicaoInvalidaException;
import br.com.central.convenios.seguranca.AssinaturaHmac;
import br.com.central.convenios.service.OperacaoService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/operacoes-portabilidade")
public class OperacaoController {
    private final OperacaoService service;
    private final String chaveHmac;
    private final JsonMapper jsonMapper;
    private final Validator validator;

    public OperacaoController(
            OperacaoService service,
            @Value("${central.integracao.chave-hmac}")
            String chaveHmac,
            JsonMapper jsonMapper,
            Validator validator
    ) {
        this.service = service;
        this.chaveHmac = chaveHmac;
        this.jsonMapper = jsonMapper;
        this.validator = validator;
    }

    @GetMapping("/{protocolo}")
    public OperacaoConsultaRespostaDTO consultar(@PathVariable UUID protocolo) {
        return service.consultar(protocolo);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OperacaoRespostaDTO registrar(@RequestHeader("Idempotency-Key") String chave,
                                         @RequestHeader(value = "X-Assinatura-HMAC", required = false) String assinaturaRecebida,
                                         @RequestBody String corpoJson) {

        try {
            if (!AssinaturaHmac.verificar(corpoJson, chaveHmac, assinaturaRecebida)) {
                throw new AssinaturaException("Assinatura HMAC inválida");
            }
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new AssinaturaException("Erro ao verificar assinatura HMAC");
        }

        if (!chave.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new RequisicaoInvalidaException("Idempotency-Key deve ser um UUID");
        }
        OperacaoRequisicaoDTO requisicao = jsonMapper.readValue(corpoJson, OperacaoRequisicaoDTO.class);
        Set<ConstraintViolation<OperacaoRequisicaoDTO>> violacoes = validator.validate(requisicao);
        if (!violacoes.isEmpty()) {
            throw new ConstraintViolationException(violacoes);
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
