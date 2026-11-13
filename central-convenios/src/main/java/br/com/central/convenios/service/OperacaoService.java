package br.com.central.convenios.service;

import br.com.central.convenios.dto.*;
import br.com.central.convenios.entity.*;
import br.com.central.convenios.repository.*;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OperacaoService {
    private final OperacaoRepository operacoes;
    private final ParcelaRepository parcelas;
    private final AutorizacaoRepository autorizacoes;
    private final ReservaRepository reservas;
    private final Clock relogio;
    private final TransactionTemplate transacao;

    public OperacaoService(OperacaoRepository operacoes, ParcelaRepository parcelas,
                           AutorizacaoRepository autorizacoes, ReservaRepository reservas, Clock relogio,
                           PlatformTransactionManager transactionManager) {
        this.operacoes = operacoes;
        this.parcelas = parcelas;
        this.autorizacoes = autorizacoes;
        this.reservas = reservas;
        this.relogio = relogio;
        transacao = new TransactionTemplate(transactionManager);
        transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(readOnly = true)
    public OperacaoConsultaRespostaDTO consultar(UUID protocolo) {
        return OperacaoConsultaRespostaDTO.de(operacoes.findById(protocolo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Protocolo não encontrato!")));
    }

    public OperacaoRespostaDTO registrar(UUID chave, OperacaoRequisicaoDTO requisicao) {
        var ids = new HashSet<>(requisicao.parcelasIds());
        if (ids.size() != requisicao.parcelasIds().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista contém parcelas repetidas");
        }
        var existente = operacoes.findByChaveIdempotencia(chave);

        if (existente.isPresent()) {
            return recuperar(existente.get(), requisicao);
        }
        try {
            return transacao.execute(transacaoAtual -> {
                // A chave é disputada antes das parcelas; toda falha desfaz também esta inserção.
                var operacao = operacoes.saveAndFlush(
                        new OperacaoPortabilidade(
                            chave,
                            requisicao.beneficiarioId(),
                            requisicao.contaDestino().conta(),
                            requisicao.contaDestino().agencia(),
                            requisicao.contaDestino().banco(),
                            OffsetDateTime.now(relogio)
                        )
                );
                var selecionadas = parcelas.buscarParaReserva(ids);
                if (selecionadas.size() != ids.size()) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Parcelas inexistentes");
                }
                for (var parcela : selecionadas) {
                    if (!parcela.getBeneficiarioId().equals(requisicao.beneficiarioId())
                            || parcela.getStatus() != StatusParcela.DISPONIVEL
                            || parcela.getDataDesconto().isBefore(LocalDate.now(relogio))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcela indisponível para portabilidade");
                    }
                    var autorizacao = autorizacoes.findByBeneficiarioIdAndConvenioId(
                            requisicao.beneficiarioId(), parcela.getConvenioId());
                    if (autorizacao.isEmpty() || autorizacao.get().getStatus() != StatusAutorizacao.ATIVA) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, "Beneficiário sem autorização ativa");
                    }
                }
                operacao.vincularParcelas(ids);
                operacoes.flush();
                reservas.reservar(operacao.getProtocolo());
                return OperacaoRespostaDTO.de(operacao);
            });
        } catch (DataIntegrityViolationException excecao) {
            // Consultar apenas depois do rollback: a transação vencedora já está confirmada.
            var concorrente = operacoes.findByChaveIdempotencia(chave);
            if (concorrente.isPresent()) return recuperar(concorrente.get(), requisicao);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Parcela já vinculada a outra operação", excecao);
        }
    }

    private OperacaoRespostaDTO recuperar(OperacaoPortabilidade operacao, OperacaoRequisicaoDTO requisicao) {
        if (!operacao.mesmoConteudo(
                requisicao.beneficiarioId(),
                requisicao.contaDestino().banco(),
                requisicao.contaDestino().agencia(),
                requisicao.contaDestino().conta(),
                requisicao.parcelasIds())
        ) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chave de idempotência reutilizada com conteúdo diferente");
        }
        return OperacaoRespostaDTO.de(operacao);
    }
}
