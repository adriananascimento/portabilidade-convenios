package br.com.portabilidade.convenios.controller;

import br.com.portabilidade.convenios.dto.OperacaoListagemDTO;
import br.com.portabilidade.convenios.service.SincronizacaoOperacaoService;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operacoes")
public class SincronizacaoOperacaoController {

    private final SincronizacaoOperacaoService service;

    public SincronizacaoOperacaoController(SincronizacaoOperacaoService service) {
        this.service = service;
    }

    @PostMapping("/{id}/sincronizar-status")
    public OperacaoListagemDTO sincronizar(@PathVariable UUID id) {
        return service.sincronizar(id);
    }
}
