package br.com.central.convenios.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@EnableScheduling
@ConditionalOnProperty(name = "central.processamento.habilitado", havingValue = "true", matchIfMissing = true)
public class AgendamentoOperacaoService {
    private final ProcessamentoOperacaoService processamento;

    public AgendamentoOperacaoService(ProcessamentoOperacaoService processamento) {
        this.processamento = processamento;
    }

    @Scheduled(fixedDelayString = "${central.processamento.intervalo:PT5S}")
    public void processar() {
        processamento.processarPendentes();
    }
}
