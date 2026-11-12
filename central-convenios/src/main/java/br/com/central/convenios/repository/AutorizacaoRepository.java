package br.com.central.convenios.repository;

import br.com.central.convenios.entity.Autorizacao;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutorizacaoRepository extends JpaRepository<Autorizacao, UUID> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select a from Autorizacao a where a.beneficiarioId = :beneficiario and a.convenioId = :convenio")
    Optional<Autorizacao> buscarParaProcessamento(@Param("beneficiario") String beneficiario,
                                                @Param("convenio") String convenio);

    Optional<Autorizacao> findByBeneficiarioIdAndConvenioId(String beneficiarioId, String convenioId);
}
