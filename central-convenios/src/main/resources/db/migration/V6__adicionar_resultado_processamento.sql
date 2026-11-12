-- V5 já ampliou os estados permitidos; preservamos todas as migrações anteriores.
ALTER TABLE operacoes_portabilidade
    ADD COLUMN data_processamento TIMESTAMP WITH TIME ZONE,
    ADD COLUMN motivo_rejeicao VARCHAR(255);
