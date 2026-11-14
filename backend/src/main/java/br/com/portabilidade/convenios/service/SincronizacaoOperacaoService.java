package br.com.portabilidade.convenios.service;

import br.com.portabilidade.convenios.client.CentralConveniosClient;
import br.com.portabilidade.convenios.dto.*;
import br.com.portabilidade.convenios.entity.*;
import br.com.portabilidade.convenios.exception.*;
import br.com.portabilidade.convenios.repository.*;
import java.util.UUID;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SincronizacaoOperacaoService {
    private final OperacaoPortabilidadeRepository operacoes;
    private final EventoOperacaoRepository eventos;
    private final CentralConveniosClient central;
    private final TransactionTemplate transacao;

    public SincronizacaoOperacaoService(OperacaoPortabilidadeRepository operacoes,
            EventoOperacaoRepository eventos, CentralConveniosClient central, PlatformTransactionManager manager) {
        this.operacoes = operacoes;
        this.eventos = eventos;
        this.central = central;
        transacao = new TransactionTemplate(manager);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(propagation = Propagation.NEVER)
    public OperacaoListagemDTO sincronizar(UUID id) {
        var inicial = transacao.execute(tx -> buscar(id));
        if (finalizada(inicial.getStatusOperacao())) return resposta(inicial);
        if (inicial.getProtocoloCentral() == null) throw new ConflitoException(
                "Operação sem protocolo da Central; sincronização indisponível");
        var recebido = central.consultarOperacao(inicial.getProtocoloCentral());
        if (recebido == null || recebido.status() == null
                || !inicial.getProtocoloCentral().equals(recebido.protocolo())) {
            throw new SincronizacaoException(HttpStatus.BAD_GATEWAY, "Resposta inválida da Central", null);
        }
        try {
            return transacao.execute(tx -> {
                var atual = buscar(id);
                if (atual.getVersao() != inicial.getVersao()) return resolverConflito(atual, recebido.status());
                if (atual.getStatusOperacao() == recebido.status()) return resposta(atual);
                // A Central atual somente altera operações PENDENTES; FALHA_TECNICA não tem saída.
                if (atual.getStatusOperacao() != null && atual.getStatusOperacao() != StatusOperacao.PENDENTE) {
                    throw new SincronizacaoException(HttpStatus.BAD_GATEWAY,
                            "Transição de status incompatível com a Central", null);
                }
                eventos.save(new EventoOperacao(atual, TipoEventoOperacao.STATUS_OPERACAO_ATUALIZADO,
                        "Status oficial atualizado por consulta à Central", null, null,
                        atual.getStatusOperacao(), recebido.status(), null));
                atual.setStatusOperacao(recebido.status());
                operacoes.flush();
                return resposta(atual);
            });
        } catch (OptimisticLockingFailureException excecao) {
            // O rollback já terminou. Não repetir HTTP nem aplicar a resposta antiga.
            return transacao.execute(tx -> resolverConflito(buscar(id), recebido.status()));
        } catch (SincronizacaoException | ConflitoException | RecursoNaoEncontradoException excecao) {
            throw excecao;
        } catch (RuntimeException excecao) {
            throw new SincronizacaoException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Falha na persistência local da sincronização", excecao);
        }
    }

    private OperacaoListagemDTO resolverConflito(OperacaoPortabilidade atual, StatusOperacao recebido) {
        if (finalizada(atual.getStatusOperacao()) || atual.getStatusOperacao() == recebido) return resposta(atual);
        throw new ConflitoException("Operação alterada durante a consulta; resposta antiga não aplicada");
    }

    private OperacaoPortabilidade buscar(UUID id) {
        return operacoes.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Operação não encontrada"));
    }

    private boolean finalizada(StatusOperacao status) {
        return status == StatusOperacao.CONFIRMADA || status == StatusOperacao.REJEITADA;
    }

    private OperacaoListagemDTO resposta(OperacaoPortabilidade operacao) {
        return new OperacaoListagemDTO(operacao.getId(), operacao.getBeneficiarioId(), operacao.getDataCriacao(),
                operacao.getStatusIntegracao(), operacao.getStatusOperacao(), operacao.getProtocoloCentral());
    }
}
