#!/usr/bin/env python3
"""
Herramienta de automatización y administración de plantillas de WhatsApp en Meta Cloud API para SIE Jurídicos.

Permite:
- Listar todas las plantillas existentes y ver su estado de aprobación (APPROVED, PENDING, REJECTED).
- Crear y registrar automáticamente las plantillas oficiales del sistema:
    1. notificacion_radicado_caso (Notificación de nuevo caso al cliente con enlace)
    2. recordatorio_cobro (Recordatorio de pago con botones de respuesta rápida)
    3. nueva_solicitud (Aviso interno a la firma con datos del formulario de contacto)
    4. confirmacion_cita (Confirmación de reunión con cliente)
    5. reporte_semanal_caso (Resumen semanal de estado del caso)
    6. aviso_nuevo_blog (Aviso automático al equipo de redes de nuevo artículo publicado)
- Inspeccionar el estado de una plantilla específica con sus motivos de rechazo si aplica.
- Eliminar plantillas obsoletas.

Uso:
  python scripts/gestionar_plantillas_whatsapp.py --listar
  python scripts/gestionar_plantillas_whatsapp.py --crear aviso_nuevo_blog
  python scripts/gestionar_plantillas_whatsapp.py --crear-todas
  python scripts/gestionar_plantillas_whatsapp.py --estado notificacion_radicado_caso
  python scripts/gestionar_plantillas_whatsapp.py --eliminar plantilla_vieja
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error

VERSION_API = "v22.0"
IDIOMA_DEFECTO = "es_CO"

# Definición de las plantillas oficiales del sistema
PLANTILLAS_SISTEMA = {
    "aviso_nuevo_blog": {
        "category": "UTILITY",
        "components": [
            {
                "type": "BODY",
                "text": "Se acaba de publicar un nuevo contenido en SIE Jurídicos: {{1}}. Puedes leerlo completo aquí: {{2}}",
                "example": {
                    "body_text": [
                        [
                            "Novedades laborales y jurisprudencia reciente",
                            "https://siejuridicos.com/blog/novedades-laborales"
                        ]
                    ]
                }
            }
        ]
    },
    "notificacion_radicado_caso": {
        "category": "UTILITY",
        "components": [
            {
                "type": "HEADER",
                "format": "IMAGE",
                "example": {
                    "header_handle": [
                        "https://siejuridicos.com/marca/logo.png"
                    ]
                }
            },
            {
                "type": "BODY",
                "text": "Estimado(a) {{1}},\n\nLe confirmamos que su proceso legal ha sido radicado bajo el número: *{{2}}*.\n\nPuede consultar el avance y las decisiones de su caso en cualquier momento ingresando al siguiente enlace:\n{{3}}\n\nAtentamente,\n*SIE Jurídicos*",
                "example": {
                    "body_text": [
                        [
                            "Carlos Mendoza",
                            "11001333502120150038900",
                            "https://siejuridicos.com/consulta-caso"
                        ]
                    ]
                }
            }
        ]
    },
    "recordatorio_cobro": {
        "category": "UTILITY",
        "components": [
            {
                "type": "BODY",
                "text": "Estimado(a) {{1}},\n\nLe recordamos amablemente que presenta una cuota mensual pendiente correspondiente a los honorarios de su proceso por valor de: *{{2}}*.\n\nPor favor indíquenos si ya realizó el pago correspondiente.",
                "example": {
                    "body_text": [
                        [
                            "María Fernanda Gómez",
                            "$ 500.000 COP"
                        ]
                    ]
                }
            },
            {
                "type": "BUTTONS",
                "buttons": [
                    {
                        "type": "QUICK_REPLY",
                        "text": "Ya realicé el pago"
                    },
                    {
                        "type": "QUICK_REPLY",
                        "text": "Pendiente de pago"
                    }
                ]
            }
        ]
    },
    "nueva_solicitud": {
        "category": "UTILITY",
        "components": [
            {
                "type": "HEADER",
                "format": "IMAGE",
                "example": {
                    "header_handle": [
                        "https://siejuridicos.com/marca/logo.png"
                    ]
                }
            },
            {
                "type": "BODY",
                "text": "🔔 *Nueva solicitud de contacto*\n\nCliente: {{1}}\nCorreo: {{2}}\nTeléfono: {{3}}\nMensaje: {{4}}",
                "example": {
                    "body_text": [
                        [
                            "Andrés Torres",
                            "andres@ejemplo.com",
                            "3001234567",
                            "Requiero asesoría en derecho laboral para un proceso ordinario."
                        ]
                    ]
                }
            }
        ]
    },
    "confirmacion_cita": {
        "category": "UTILITY",
        "components": [
            {
                "type": "HEADER",
                "format": "IMAGE",
                "example": {
                    "header_handle": [
                        "https://siejuridicos.com/marca/logo.png"
                    ]
                }
            },
            {
                "type": "BODY",
                "text": "Estimado(a) {{1}},\n\nLe confirmamos su reunión con el equipo legal de SIE Jurídicos programada para: *{{2}}*.\n\nModalidad / Enlace: {{3}}\n\nAtentamente,\n*SIE Jurídicos*",
                "example": {
                    "body_text": [
                        [
                            "Laura Rodríguez",
                            "Lunes 10 de Noviembre a las 3:00 PM",
                            "https://meet.google.com/xyz-abc-def"
                        ]
                    ]
                }
            }
        ]
    },
    "reporte_semanal_caso": {
        "category": "UTILITY",
        "components": [
            {
                "type": "HEADER",
                "format": "IMAGE",
                "example": {
                    "header_handle": [
                        "https://siejuridicos.com/marca/logo.png"
                    ]
                }
            },
            {
                "type": "BODY",
                "text": "Estimado(a) {{1}},\n\nLe compartimos el reporte de seguimiento a su proceso judicial radicado *{{2}}*.\n\nPuede ver los últimos avances, ubicación y decisiones actualizadas ingresando a:\n{{3}}\n\nAtentamente,\n*SIE Jurídicos*",
                "example": {
                    "body_text": [
                        [
                            "Carlos Mendoza",
                            "11001333502120150038900",
                            "https://siejuridicos.com/consulta-caso"
                        ]
                    ]
                }
            }
        ]
    }
}


def cargar_config(env_file=".env.prod", cli_token=None, cli_waba_id=None):
    token = cli_token
    waba_id = cli_waba_id

    # Buscar en env_file local o en la raíz
    rutas = [env_file, os.path.join(os.path.dirname(__file__), "..", env_file), ".env.prod", ".env"]
    for ruta in rutas:
        if os.path.exists(ruta):
            with open(ruta, encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if "=" in line and not line.startswith("#"):
                        k, v = line.split("=", 1)
                        k = k.strip()
                        v = v.strip().strip('"').strip("'")
                        if k == "WHATSAPP_ACCESS_TOKEN" and not token:
                            token = v
                        if k == "WHATSAPP_BUSINESS_ACCOUNT_ID" and not waba_id:
                            waba_id = v
            if token and waba_id:
                break

    return token, waba_id


def api_request(url, method="GET", token="", payload=None):
    cuerpo = json.dumps(payload, ensure_ascii=False).encode("utf-8") if payload else None
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json; charset=utf-8"
    }
    req = urllib.request.Request(url, data=cuerpo, headers=headers, method=method)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))


def listar_plantillas(token, waba_id):
    url = f"https://graph.facebook.com/{VERSION_API}/{waba_id}/message_templates?limit=100"
    print(f"[*] Consultando plantillas en Meta WABA ID {waba_id}...")
    try:
        data = api_request(url, token=token)
        plantillas = data.get("data", [])
        print(f"\n[+] Total de plantillas registradas: {len(plantillas)}\n")
        print(f"{'NOMBRE':<35} {'ESTADO':<15} {'CATEGORÍA':<15} {'IDIOMA':<8} {'ID'}")
        print("-" * 95)
        for p in plantillas:
            print(f"{p.get('name', ''):<35} {p.get('status', ''):<15} {p.get('category', ''):<15} {p.get('language', ''):<8} {p.get('id', '')}")
        return plantillas
    except urllib.error.HTTPError as e:
        print(f"[ERROR {e.code}] Error al listar plantillas:")
        print(e.read().decode("utf-8"))
        return []


def consultar_estado(token, waba_id, nombre):
    url = f"https://graph.facebook.com/{VERSION_API}/{waba_id}/message_templates?name={nombre}"
    print(f"[*] Consultando estado de la plantilla '{nombre}'...")
    try:
        data = api_request(url, token=token)
        plantillas = data.get("data", [])
        if not plantillas:
            print(f"[-] No se encontró ninguna plantilla con el nombre '{nombre}'.")
            return
        for p in plantillas:
            print("\n" + "=" * 60)
            print(f" Nombre:     {p.get('name')}")
            print(f" ID:         {p.get('id')}")
            print(f" Estado:     {p.get('status')}")
            print(f" Categoría:  {p.get('category')}")
            print(f" Idioma:     {p.get('language')}")
            if p.get("quality_score"):
                print(f" Calidad:    {p.get('quality_score')}")
            if p.get("rejected_reason"):
                print(f" Motivo rechazo: {p.get('rejected_reason')}")
            print("=" * 60)
    except urllib.error.HTTPError as e:
        print(f"[ERROR {e.code}] {e.read().decode('utf-8')}")


def crear_plantilla(token, waba_id, nombre, idioma=IDIOMA_DEFECTO):
    if nombre not in PLANTILLAS_SISTEMA:
        print(f"[ERROR] La plantilla '{nombre}' no está definida en las plantillas oficiales del sistema.")
        print(f"Plantillas disponibles: {', '.join(PLANTILLAS_SISTEMA.keys())}")
        return False

    definicion = PLANTILLAS_SISTEMA[nombre]
    payload = {
        "name": nombre,
        "category": definicion["category"],
        "allow_category_change": True,
        "language": idioma,
        "components": definicion["components"]
    }

    url = f"https://graph.facebook.com/{VERSION_API}/{waba_id}/message_templates"
    print(f"[*] Registrando plantilla '{nombre}' (categoría {definicion['category']}) en Meta...")

    try:
        resp = api_request(url, method="POST", token=token, payload=payload)
        print(f"[ÉXITO] Plantilla registrada correctamente!")
        print(f"  ID de plantilla: {resp.get('id')}")
        print(f"  Estado:          {resp.get('status', 'PENDING')}")
        print(f"  Categoría:       {resp.get('category', definicion['category'])}")
        return True
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8")
        print(f"[ERROR {e.code}] No se pudo crear la plantilla '{nombre}':")
        try:
            err_json = json.loads(err)
            print(json.dumps(err_json, indent=2, ensure_ascii=False))
        except Exception:
            print(err)
        return False


def eliminar_plantilla(token, waba_id, nombre):
    url = f"https://graph.facebook.com/{VERSION_API}/{waba_id}/message_templates?name={nombre}"
    print(f"[*] Solicitando eliminación de la plantilla '{nombre}' en Meta...")
    try:
        resp = api_request(url, method="DELETE", token=token)
        print(f"[ÉXITO] Plantilla eliminada: {resp}")
        return True
    except urllib.error.HTTPError as e:
        print(f"[ERROR {e.code}] {e.read().decode('utf-8')}")
        return False


def main():
    parser = argparse.ArgumentParser(description="Gestor automatizado de plantillas de WhatsApp para SIE Jurídicos")
    parser.add_argument("--token", help="Access token de Meta (whatsapp_business_management)")
    parser.add_argument("--waba-id", help="WhatsApp Business Account ID (WABA ID)")
    parser.add_argument("--env-file", default=".env.prod", help="Archivo de configuración .env")
    parser.add_argument("--listar", action="store_true", help="Listar todas las plantillas registradas")
    parser.add_argument("--crear", help="Nombre de la plantilla a crear")
    parser.add_argument("--crear-todas", action="store_true", help="Crear todas las plantillas oficiales del sistema")
    parser.add_argument("--estado", help="Consultar estado de una plantilla por nombre")
    parser.add_argument("--eliminar", help="Eliminar una plantilla por nombre")
    args = parser.parse_args()

    token, waba_id = cargar_config(args.env_file, args.token, args.waba_id)

    if not token:
        print("[ERROR] Falta el token de acceso de Meta (WHATSAPP_ACCESS_TOKEN).")
        print("Configúralo en .env.prod o pásalo con --token <TOKEN>")
        sys.exit(1)

    if not waba_id:
        print("=========================================================================")
        print(" [ATENCIÓN] Se necesita el WhatsApp Business Account ID (WABA ID) de Meta.")
        print("=========================================================================")
        print(" Puedes obtenerlo en:")
        print(" 1. Meta Business Suite -> Cuentas de WhatsApp -> ID de la cuenta de WhatsApp")
        print(" 2. O en Meta for Developers -> Tu App -> WhatsApp -> API Setup -> 'WhatsApp Business Account ID'")
        print(" Configúralo en .env.prod como WHATSAPP_BUSINESS_ACCOUNT_ID=<TU_WABA_ID> o pásalo con --waba-id")
        sys.exit(1)

    if args.listar:
        listar_plantillas(token, waba_id)
    elif args.crear:
        crear_plantilla(token, waba_id, args.crear)
    elif args.crear_todas:
        print(f"[*] Registrando las {len(PLANTILLAS_SISTEMA)} plantillas oficiales del sistema en Meta...")
        for nombre in PLANTILLAS_SISTEMA:
            crear_plantilla(token, waba_id, nombre)
            print("-" * 50)
    elif args.estado:
        consultar_estado(token, waba_id, args.estado)
    elif args.eliminar:
        eliminar_plantilla(token, waba_id, args.eliminar)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
