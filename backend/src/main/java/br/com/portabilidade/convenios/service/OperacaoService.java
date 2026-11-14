package br.com.portabilidade.convenios.service;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OperacaoService {

    private final CentralConveniosClient centralClient;

    public OperacaoService(CentralConveniosClient centralClient) {
        this.centralClient = centralClient;
    }

    public OperacaoPaginaDTO listar(int pagina, int tamanho) {
        return centralClient.listarOperacoes(pagina, tamanho);
    }

    public ResponseEntity<OperacaoRespostaDTO> criarOperacao(CriarOperacaoRequestDTO request, UUID chaveIdempotencia) {
        ResponseEntity<OperacaoRespostaDTO> resposta = centralClient.criarOperacao(request, chaveIdempotencia);

        return ResponseEntity
                .status(resposta.getStatusCode())
                .body(resposta.getBody());
    }
}
