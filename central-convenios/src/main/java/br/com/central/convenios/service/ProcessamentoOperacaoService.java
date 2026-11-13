package br.com.central.convenios.service;

import br.com.central.convenios.entity.*;
import br.com.central.convenios.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.sql.SQLException;
import org.springframework.dao.TransientDataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class ProcessamentoOperacaoService {
    private static final Logger log = LoggerFactory.getLogger(ProcessamentoOperacaoService.class);
    private final OperacaoRepository operacoes;
    private final ParcelaRepository parcelas;
    private final AutorizacaoRepository autorizacoes;
    private final ReservaRepository reservas;
    private final Clock relogio;
    private final Duration atrasoMinimo;
    private final TransactionTemplate transacao;

    public ProcessamentoOperacaoService(OperacaoRepository operacoes, ParcelaRepository parcelas,
            AutorizacaoRepository autorizacoes, ReservaRepository reservas, Clock relogio,
            PlatformTransactionManager transactionManager,
            @Value("${central.processamento.atraso-minimo:PT10S}") Duration atrasoMinimo) {
        if (atrasoMinimo.isNegative()) throw new IllegalArgumentException("Atraso mínimo não pode ser negativo");
        this.operacoes = operacoes;
        this.parcelas = parcelas;
        this.autorizacoes = autorizacoes;
        this.reservas = reservas;
        this.relogio = relogio;
        this.atrasoMinimo = atrasoMinimo;
        transacao = new TransactionTemplate(transactionManager);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void processarPendentes() {
        var agora = OffsetDateTime.now(relogio);
        var limite = agora.minus(atrasoMinimo);
        for (var protocolo : operacoes.buscarPendentes(limite, agora)) {
            try {
                processar(protocolo);
            } catch (RuntimeException excecao) {
                // A falha de uma operação não interrompe as demais; não expor mensagens SQL.
                log.warn("Falha técnica no processamento da operação {}", protocolo);
            }
        }
    }

    public boolean processar(UUID protocolo) {
        // Capturado somente após o lock e a revalidação. Não inclui tentativas não iniciadas.
        int[] tentativaAnterior = {-1};
        try {
            return Boolean.TRUE.equals(transacao.execute(atual -> {
                var encontrada = operacoes.buscarParaProcessamento(protocolo);
                if (encontrada.isEmpty()) return false;
                var operacao = encontrada.get();
                var agora = OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.MICROS);
                if (operacao.getStatus() != StatusOperacao.PENDENTE
                        || operacao.getDataCriacao().isAfter(agora.minus(atrasoMinimo))
                        || (operacao.getProximaTentativaEm() != null
                            && operacao.getProximaTentativaEm().isAfter(agora))) return false;
                tentativaAnterior[0] = operacao.getTentativasProcessamento();

                var selecionadas = parcelas.buscarParaReserva(operacao.getParcelasIds());
                String motivo = null;
                for (var parcela : selecionadas) {
                    if (!parcela.getBeneficiarioId().equals(operacao.getBeneficiarioId())
                            || parcela.getDataDesconto().isBefore(LocalDate.now(relogio))) {
                        motivo = "Parcela indisponível para portabilidade";
                        break;
                    }
                    var autorizacao = autorizacoes.buscarParaProcessamento(
                            operacao.getBeneficiarioId(), parcela.getConvenioId());
                    if (autorizacao.isEmpty() || autorizacao.get().getStatus() != StatusAutorizacao.ATIVA) {
                        motivo = "Beneficiário sem autorização ativa";
                        break;
                    }
                }
                operacao.finalizar(agora, motivo);
                if (motivo != null) reservas.liberar(protocolo);
                operacoes.flush();
                return true;
            }));
        } catch (RuntimeException excecao) {
            // execute já concluiu o rollback, inclusive quando a falha ocorreu no commit.
            if (tentativaAnterior[0] >= 0) {
                registrarFalha(protocolo, tentativaAnterior[0], excecao);
            }
            throw excecao;
        }
    }

    private void registrarFalha(UUID protocolo, int tentativaAnterior, RuntimeException excecao) {
        boolean transitoria = falhaTransitoria(excecao);
        transacao.executeWithoutResult(atual -> {
            var encontrada = operacoes.buscarParaProcessamento(protocolo);
            if (encontrada.isEmpty()) return;
            var operacao = encontrada.get();
            // Outra execução pode ter concluído ou registrado esta tentativa após o rollback.
            if (operacao.getStatus() != StatusOperacao.PENDENTE
                    || operacao.getTentativasProcessamento() != tentativaAnterior) return;
            operacao.registrarFalha(OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.MICROS),
                    transitoria, transitoria ? "Falha transitória de infraestrutura"
                            : "Falha técnica permanente ou não classificada");
            operacoes.flush();
        });
    }

    private boolean falhaTransitoria(Throwable excecao) {
        for (Throwable causa = excecao; causa != null; causa = causa.getCause()) {
            if (causa instanceof TransientDataAccessException) return true;
            if (causa instanceof SQLException sql) {
                String estado = sql.getSQLState();
                if (estado != null && (estado.startsWith("08") || estado.equals("40001")
                        || estado.equals("40P01") || estado.equals("55P03"))) return true;
            }
        }
        return false;
    }
}
