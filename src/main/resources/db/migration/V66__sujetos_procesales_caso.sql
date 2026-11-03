-- Gap real detectado comparando contra el correo diario de Lupa Jurídica (pedido explícito
-- del usuario: "lupa nos da un seguimiento mas robusto... necesito mejorar esto"): Lupa
-- muestra demandante/demandado y el despacho completo en cada caso -- ese dato YA viene en
-- la respuesta del CPNU que SeguimientoJudicialService consulta para resolver idProceso
-- (campos "sujetosProcesales" y "despacho"), y se venía descartando. Ver
-- RamaJudicialCpnuClient.buscarProceso().
ALTER TABLE casos
    ADD COLUMN sujetos_procesales TEXT,
    ADD COLUMN despacho_judicial VARCHAR(255);
