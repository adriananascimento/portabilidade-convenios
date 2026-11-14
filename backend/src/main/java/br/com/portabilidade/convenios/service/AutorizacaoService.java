package br.com.portabilidade.convenios.service;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.AutorizacaoRespostaDTO;
import br.com.portabilidade.convenios.dto.AutorizacoesPaginaDTO;
import br.com.portabilidade.convenios.dto.CriarAutorizacaoRequestDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class AutorizacaoService {

    private final CentralConveniosClient centralClient;

    public AutorizacaoService(CentralConveniosClient centralClient) {
        this.centralClient = centralClient;
    }

    public AutorizacoesPaginaDTO listar(int pagina, int tamanho) {
        return centralClient.listarAutorizacoes(pagina, tamanho);
    }

    public ResponseEntity<AutorizacaoRespostaDTO> criarAutorizacao(CriarAutorizacaoRequestDTO request) {
        ResponseEntity<AutorizacaoRespostaDTO> resposta = centralClient.criarAutorizacao(request);

        return ResponseEntity
                .status(resposta.getStatusCode())
                .body(resposta.getBody());
    }
}
