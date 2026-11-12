CREATE TABLE reservas_parcelas (
    parcela_id VARCHAR(100) PRIMARY KEY,
    operacao_protocolo UUID NOT NULL,
    CONSTRAINT fk_reserva_vinculo FOREIGN KEY (operacao_protocolo, parcela_id)
        REFERENCES operacoes_parcelas (operacao_protocolo, parcela_id)
);

INSERT INTO reservas_parcelas (parcela_id, operacao_protocolo)
SELECT op.parcela_id, op.operacao_protocolo
FROM operacoes_parcelas op
JOIN operacoes_portabilidade o ON o.protocolo = op.operacao_protocolo
WHERE o.status IN ('PENDENTE', 'CONFIRMADA');

ALTER TABLE operacoes_parcelas DROP CONSTRAINT uk_operacoes_parcelas_parcela;
CREATE INDEX idx_reservas_operacao ON reservas_parcelas (operacao_protocolo);
CREATE INDEX idx_operacoes_pendentes ON operacoes_portabilidade (data_criacao, protocolo)
    WHERE status = 'PENDENTE';
