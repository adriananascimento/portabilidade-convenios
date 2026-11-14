package br.com.portabilidade.convenios.repository;

import br.com.portabilidade.convenios.entity.EventoOperacao;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface EventoOperacaoRepository extends Repository<EventoOperacao, UUID> {
    EventoOperacao save(EventoOperacao evento);
    List<EventoOperacao> findByOperacaoIdOrderByDataCriacaoAscIdAsc(UUID operacaoId);
}
