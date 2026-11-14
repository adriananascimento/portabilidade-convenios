package br.com.portabilidade.convenios.service;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.exception.*;
import br.com.portabilidade.convenios.repository.OperacaoPortabilidadeRepository;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class OperacaoService {
    private final CentralConveniosClient centralClient;
    private final OperacaoPortabilidadeRepository operacoes;
    private final TransactionTemplate transacao;

    public OperacaoService(CentralConveniosClient centralClient, OperacaoPortabilidadeRepository operacoes,
                           PlatformTransactionManager transactionManager) {
        this.centralClient = centralClient;
        this.operacoes = operacoes;
        transacao = new TransactionTemplate(transactionManager);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public OperacaoPaginaDTO listar(int pagina, int tamanho) {
        var resultado = operacoes.findAll(PageRequest.of(pagina, tamanho,
                Sort.by(Sort.Direction.DESC, "dataCriacao", "id")));
        var conteudo = resultado.map(operacao -> new OperacaoListagemDTO(
                operacao.getId(), operacao.getBeneficiarioId(), operacao.getDataCriacao(),
                operacao.getStatusIntegracao(), operacao.getStatusOperacao(),
                operacao.getProtocoloCentral())).getContent();
        return new OperacaoPaginaDTO(conteudo, resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages());
    }

    @Transactional(propagation = Propagation.NEVER)
    public ResponseEntity<OperacaoRespostaDTO> criarOperacao(CriarOperacaoRequestDTO request, UUID chave) {
        var parcelas = new HashSet<>(request.parcelasIds());
        if (parcelas.size() != request.parcelasIds().size()) {
            throw new RequisicaoInvalidaException("A lista contém parcelas repetidas");
        }
        var existente = buscar(chave);
        if (existente != null) return recuperar(existente, request);
        OperacaoPortabilidade criada;
        try {
            criada = transacao.execute(tx -> operacoes.saveAndFlush(new OperacaoPortabilidade(
                    chave, request.beneficiarioId(), parcelas, new ContaDestino(request.contaDestino().banco(),
                    request.contaDestino().agencia(), request.contaDestino().conta()))));
        } catch (DataIntegrityViolationException excecao) {
            // execute já concluiu o rollback; buscar abre outra transação.
            existente = buscar(chave);
            if (existente == null) throw excecao;
            return recuperar(existente, request);
        }
        ResponseEntity<OperacaoRespostaDTO> resposta;
        try {
            resposta = centralClient.criarOperacao(request, chave);
            var corpo = resposta.getBody();
            if (!resposta.getStatusCode().is2xxSuccessful() || corpo == null || corpo.protocolo() == null
                    || corpo.dataCriacao() == null || !request.beneficiarioId().equals(corpo.beneficiarioId())) {
                throw new CentralIndisponivelException(new IllegalStateException("Resposta inválida da Central"));
            }
        } catch (CentralIndisponivelException | RestClientException excecao) {
            atualizarFalha(criada.getId(), resultadoConhecido(excecao)
                    ? StatusIntegracao.FALHA_ENVIO : StatusIntegracao.ENVIO_INDETERMINADO);
            throw new CentralIndisponivelException(excecao);
        }
        var corpo = resposta.getBody();
        try {
            transacao.executeWithoutResult(tx -> {
                var operacao = operacoes.findById(criada.getId()).orElseThrow();
                operacao.setProtocoloCentral(corpo.protocolo());
                operacao.setDataCriacaoCentral(corpo.dataCriacao());
                operacao.setStatusOperacao(corpo.status() == null ? StatusOperacao.PENDENTE : corpo.status());
                operacao.setStatusIntegracao(StatusIntegracao.ENVIADA);
            });
        } catch (RuntimeException excecao) {
            try { atualizarFalha(criada.getId(), StatusIntegracao.ENVIO_INDETERMINADO); }
            catch (RuntimeException falhaPersistencia) { excecao.addSuppressed(falhaPersistencia); }
            throw new CentralIndisponivelException(excecao);
        }
        return ResponseEntity.status(resposta.getStatusCode()).body(new OperacaoRespostaDTO(corpo.protocolo(),
                corpo.status() == null ? StatusOperacao.PENDENTE : corpo.status(),
                corpo.dataCriacao(), corpo.beneficiarioId()));
    }

    private OperacaoPortabilidade buscar(UUID chave) {
        return transacao.execute(tx -> operacoes.findByChaveIdempotencia(chave).map(operacao -> {
            operacao.getParcelasIds(); // Inicializar coleção antes de encerrar a transação.
            return operacao;
        }).orElse(null));
    }

    private ResponseEntity<OperacaoRespostaDTO> recuperar(OperacaoPortabilidade operacao,
                                                         CriarOperacaoRequestDTO request) {
        var conta = operacao.getContaDestino();
        if (!operacao.getBeneficiarioId().equals(request.beneficiarioId())
                || !operacao.getParcelasIds().equals(new HashSet<>(request.parcelasIds()))
                || !conta.getBanco().equals(request.contaDestino().banco())
                || !conta.getAgencia().equals(request.contaDestino().agencia())
                || !conta.getConta().equals(request.contaDestino().conta())) {
            throw new ConflitoException("Chave de idempotência reutilizada com conteúdo diferente");
        }
        if (operacao.getStatusIntegracao() != StatusIntegracao.ENVIADA || operacao.getProtocoloCentral() == null) {
            throw new CentralIndisponivelException("Operação registrada com integração "
                    + operacao.getStatusIntegracao() + "; reenvio não disponível nesta etapa.");
        }
        return ResponseEntity.accepted().body(new OperacaoRespostaDTO(operacao.getProtocoloCentral(),
                operacao.getStatusOperacao(), operacao.getDataCriacaoCentral(), operacao.getBeneficiarioId()));
    }

    private void atualizarFalha(UUID id, StatusIntegracao status) {
        transacao.executeWithoutResult(tx -> operacoes.findById(id).orElseThrow().setStatusIntegracao(status));
    }

    private boolean resultadoConhecido(Throwable excecao) {
        if (excecao instanceof RestClientResponseException resposta) {
            return resposta.getStatusCode().is4xxClientError() && resposta.getStatusCode().value() != 408;
        }
        for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
            if (causa instanceof java.net.ConnectException || causa instanceof java.net.UnknownHostException) return true;
        }
        return false;
    }
}
