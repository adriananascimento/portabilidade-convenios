package br.com.central.convenios.repository;

import br.com.central.convenios.entity.Autorizacao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutorizacaoRepository extends JpaRepository<Autorizacao, UUID> {
    Optional<Autorizacao> findByBeneficiarioIdAndConvenioId(String beneficiarioId, String convenioId);
}
