#!/usr/bin/env python3
"""
Script para registrar la plantilla oficial de aviso de nuevo blog en Meta WhatsApp Cloud API
y enviarla automáticamente a revisión.

Uso:
  python scripts/crear_plantilla_blog_meta.py --waba-id <TU_WABA_ID> [--token <TOKEN>]
"""

import argparse
import json
import os
import sys
import urllib.request
import urllib.error

VERSION_API = "v22.0"
NOMBRE_PLANTILLA = "aviso_nuevo_blog"
IDIOMA = "es_CO"

def main():
    parser = argparse.ArgumentParser(description="Crear plantilla de WhatsApp en Meta para aviso de nuevo blog")
    parser.add_argument("--waba-id", help="WhatsApp Business Account ID (WABA ID)")
    parser.add_argument("--token", help="Access token con permiso whatsapp_business_management")
    parser.add_argument("--env-file", default=".env.prod", help="Archivo .env de donde leer variables si no se pasan por CLI")
    args = parser.parse_args()

    token = args.token
    waba_id = args.waba_id

    # Si falta token o waba_id, intentar leer del archivo .env
    if os.path.exists(args.env_file):
        with open(args.env_file, encoding="utf-8") as f:
            for line in f:
                if "=" in line and not line.strip().startswith("#"):
                    k, v = line.strip().split("=", 1)
                    if k == "WHATSAPP_ACCESS_TOKEN" and not token:
                        token = v
                    if k == "WHATSAPP_BUSINESS_ACCOUNT_ID" and not waba_id:
                        waba_id = v

    if not token:
        print("[ERROR] Falta el token de acceso de Meta (WHATSAPP_ACCESS_TOKEN).")
        sys.exit(1)

    if not waba_id:
        print("=========================================================================")
        print(" [ATENCIÓN] Se necesita el WhatsApp Business Account ID (WABA ID) de Meta.")
        print("=========================================================================")
        print(" Puedes encontrarlo en:")
        print(" 1. Meta Business Suite -> Configuración -> Cuentas de WhatsApp -> ID de cuenta")
        print(" 2. O en Meta for Developers -> Tu App -> WhatsApp -> API Setup -> 'WhatsApp Business Account ID'")
        print("")
        print(" Ejecuta de nuevo:")
        print("   python scripts/crear_plantilla_blog_meta.py --waba-id <TU_WABA_ID>")
        sys.exit(1)

    payload = {
        "name": NOMBRE_PLANTILLA,
        "category": "UTILITY",
        "allow_category_change": True,
        "language": IDIOMA,
        "components": [
            {
                "type": "BODY",
                "text": "Recordatorio: Se acaba de publicar un nuevo contenido en SIE Jurídicos: {{1}}. Puedes leerlo completo aquí: {{2}}",
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
    }

    url = f"https://graph.facebook.com/{VERSION_API}/{waba_id}/message_templates"
    cuerpo_bytes = json.dumps(payload, ensure_ascii=False).encode("utf-8")

    req = urllib.request.Request(
        url,
        data=cuerpo_bytes,
        headers={
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json; charset=utf-8"
        },
        method="POST"
    )

    print(f"[*] Enviando solicitud de creación y revisión a Meta ({url})...")
    print(f"[*] Plantilla: '{NOMBRE_PLANTILLA}' | Idioma: '{IDIOMA}' | Categoría: 'UTILITY'")

    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            print("")
            print("=========================================================================")
            print(" [ÉXITO] Plantilla registrada y enviada a revisión en Meta WhatsApp API!")
            print(f" ID de plantilla: {data.get('id')}")
            print(f" Estado: {data.get('status', 'PENDING')}")
            print(f" Categoría: {data.get('category', 'UTILITY')}")
            print("=========================================================================")
            print(" Meta revisa automáticamente las plantillas de tipo Utilidad en pocos minutos.")
            print(f" Ya puedes configurar WHATSAPP_TEMPLATE_BLOG_NAME={NOMBRE_PLANTILLA}")
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        print(f"[ERROR HTTP {e.code}] No se pudo crear la plantilla:")
        try:
            err_json = json.loads(err_body)
            print(json.dumps(err_json, indent=2, ensure_ascii=False))
        except Exception:
            print(err_body)
        sys.exit(1)
    except Exception as ex:
        print(f"[ERROR] {ex}")
        sys.exit(1)

if __name__ == "__main__":
    main()
