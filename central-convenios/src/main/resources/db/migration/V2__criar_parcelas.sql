CREATE TABLE parcelas (
    id VARCHAR(100) PRIMARY KEY,
    beneficiario_id VARCHAR(100) NOT NULL,
    convenio_id VARCHAR(100) NOT NULL,
    valor NUMERIC(15,2) NOT NULL,
    data_desconto DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    CONSTRAINT ck_parcelas_status CHECK (status IN ('DISPONIVEL'))
);

CREATE INDEX idx_parcelas_beneficiario_data ON parcelas (beneficiario_id, data_desconto);
