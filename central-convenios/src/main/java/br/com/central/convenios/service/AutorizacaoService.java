package br.com.central.convenios.service;

import br.com.central.convenios.dto.AutorizacaoRequisicaoDTO;
import br.com.central.convenios.dto.AutorizacaoRespostaDTO;
import br.com.central.convenios.entity.Autorizacao;
import br.com.central.convenios.entity.StatusAutorizacao;
import br.com.central.convenios.repository.AutorizacaoRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AutorizacaoService {
    private final AutorizacaoRepository repository;
    private final TransactionTemplate transacao;

    public AutorizacaoService(AutorizacaoRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transacao = new TransactionTemplate(transactionManager);
        this.transacao.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ResultadoRegistro registrar(AutorizacaoRequisicaoDTO requisicao) {
        var existente = repository.findByBeneficiarioIdAndConvenioId(
                requisicao.beneficiarioId(), requisicao.convenioId());
        if (existente.isPresent() && existente.get().getStatus() == StatusAutorizacao.ATIVA) {
            return new ResultadoRegistro(AutorizacaoRespostaDTO.de(existente.get()), false);
        }
        try {
            var criada = transacao.execute(status -> repository.saveAndFlush(
                    new Autorizacao(requisicao.beneficiarioId(), requisicao.convenioId())));
            return new ResultadoRegistro(AutorizacaoRespostaDTO.de(criada), true);
        } catch (DataIntegrityViolationException excecao) {
            var concorrente = repository.findByBeneficiarioIdAndConvenioId(
                    requisicao.beneficiarioId(), requisicao.convenioId());
            if (concorrente.isPresent() && concorrente.get().getStatus() == StatusAutorizacao.ATIVA) {
                return new ResultadoRegistro(AutorizacaoRespostaDTO.de(concorrente.get()), false);
            }
            throw excecao;
        }
    }

    public record ResultadoRegistro(AutorizacaoRespostaDTO autorizacao, boolean criada) {}
}
