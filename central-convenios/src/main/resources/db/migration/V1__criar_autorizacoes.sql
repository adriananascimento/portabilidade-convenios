CREATE TABLE autorizacoes (
    id UUID PRIMARY KEY,
    beneficiario_id VARCHAR(100) NOT NULL,
    convenio_id VARCHAR(100) NOT NULL,
    data_autorizacao TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT uk_autorizacoes_beneficiario_convenio UNIQUE (beneficiario_id, convenio_id),
    CONSTRAINT ck_autorizacoes_status CHECK (status IN ('ATIVA', 'REVOGADA'))
);
