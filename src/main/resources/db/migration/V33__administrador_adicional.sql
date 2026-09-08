-- Administrador general adicional pedido explícitamente por el usuario. La contraseña ya
-- viene cifrada con BCrypt (misma librería y misma fuerza -- 10 -- que usa
-- AdminBootstrapRunner/AuthService en el resto del sistema, generada con
-- BCryptPasswordEncoder real, no un valor inventado a mano): nunca se guarda en texto
-- plano, ni siquiera en esta migración.
--
-- ON CONFLICT (correo) DO NOTHING: correo es UNIQUE (ver V1) -- si por cualquier motivo ya
-- existiera un usuario con este correo (ej. se creó a mano desde el panel antes de que esta
-- migración llegara a correr en algún entorno), la migración no falla ni lo sobreescribe.
INSERT INTO usuarios_internos (nombre, correo, contrasena, rol, activo, fecha_creacion)
VALUES (
    'Administrador',
    'siejuridicoafb@gmail.com',
    '$2a$10$0u6qqgSkRd0wWZZARabvHe33/p8zDAtOAoRqd8CqtJJocm.VFqyh6',
    'ADMIN_GENERAL',
    true,
    now()
)
ON CONFLICT (correo) DO NOTHING;
