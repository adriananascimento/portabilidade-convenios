package br.com.central.convenios.controller;

import br.com.central.convenios.dto.AutorizacaoRequisicaoDTO;
import br.com.central.convenios.dto.AutorizacaoRespostaDTO;
import br.com.central.convenios.service.AutorizacaoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/autorizacoes")
public class AutorizacaoController {
    private final AutorizacaoService service;

    public AutorizacaoController(AutorizacaoService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AutorizacaoRespostaDTO> registrar(@Valid @RequestBody AutorizacaoRequisicaoDTO requisicao) {
        var resultado = service.registrar(requisicao);
        return ResponseEntity.status(resultado.criada() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(resultado.autorizacao());
    }
}
