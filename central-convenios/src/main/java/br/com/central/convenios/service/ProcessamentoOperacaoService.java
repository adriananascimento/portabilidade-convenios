package br.com.central.convenios.service;

import br.com.central.convenios.entity.*;
import br.com.central.convenios.repository.*;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
        var limite = OffsetDateTime.now(relogio).minus(atrasoMinimo);
        for (var protocolo : operacoes.buscarPendentes(limite)) {
            try {
                processar(protocolo);
            } catch (RuntimeException excecao) {
                // Rollback desta operação; as demais continuam e esta pode ser tentada no próximo ciclo.
                log.error("Falha ao processar operação {}", protocolo, excecao);
            }
        }
    }

    public boolean processar(UUID protocolo) {
        return Boolean.TRUE.equals(transacao.execute(atual -> {
            var encontrada = operacoes.buscarParaProcessamento(protocolo);
            if (encontrada.isEmpty()) return false;
            var operacao = encontrada.get();
            var agora = OffsetDateTime.now(relogio).truncatedTo(ChronoUnit.MICROS);
            if (operacao.getStatus() != StatusOperacao.PENDENTE
                    || operacao.getDataCriacao().isAfter(agora.minus(atrasoMinimo))) return false;

            var selecionadas = parcelas.buscarParaReserva(operacao.getParcelasIds());
            String motivo = null;
            for (var parcela : selecionadas) {
                if (!parcela.getBeneficiarioId().equals(operacao.getBeneficiarioId())
                        || parcela.getStatus() != StatusParcela.DISPONIVEL
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
    }
}
