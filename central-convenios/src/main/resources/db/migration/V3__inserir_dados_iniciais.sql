-- Dados fictícios; autorizações devem ser registradas pela API existente.
INSERT INTO parcelas (id, beneficiario_id, convenio_id, valor, data_desconto, status) VALUES
    ('PAR-001', 'BEN-001', 'CONV-001', 350.00, DATE '2099-12-05', 'DISPONIVEL'),
    ('PAR-002', 'BEN-001', 'CONV-001', 125.50, DATE '2099-11-05', 'DISPONIVEL'),
    ('PAR-003', 'BEN-001', 'CONV-002', 200.00, DATE '2099-10-05', 'DISPONIVEL'),
    ('PAR-004', 'BEN-001', 'CONV-001', 90.00, DATE '2020-01-05', 'DISPONIVEL'),
    ('PAR-005', 'BEN-002', 'CONV-002', 450.75, DATE '2099-12-05', 'DISPONIVEL'),
    ('PAR-006', 'BEN-002', 'CONV-002', 80.00, DATE '2020-02-05', 'DISPONIVEL');
