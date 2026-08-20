"use client";

import { useEditor, EditorContent } from "@tiptap/react";
import StarterKit from "@tiptap/starter-kit";
import LinkExtension from "@tiptap/extension-link";
import ImageExtension from "@tiptap/extension-image";
import Placeholder from "@tiptap/extension-placeholder";
import {
  TextB,
  TextItalic,
  ListBullets,
  ListNumbers,
  LinkSimple,
  ImageSquare,
  ArrowUUpLeft,
  ArrowUUpRight,
} from "@phosphor-icons/react";
import { cn } from "@/lib/utils";

// Defensa en el cliente además de la restricción de protocols del LinkExtension: bloquea
// "javascript:", "data:" u otros esquemas antes de que el comando de Tiptap los inserte,
// para que el HTML guardado nunca dependa solo del filtro del propio editor.
function esUrlSegura(url: string): boolean {
  return /^https?:\/\//i.test(url.trim());
}

export function RichTextEditor({
  value,
  onChange,
}: {
  value: string;
  onChange: (html: string) => void;
}) {
  const editor = useEditor({
    // evita el mismatch SSR/cliente (mismo tipo de bug que ya corregimos en el hero):
    // Tiptap no debe renderizar contenido inicial durante el primer render del servidor.
    immediatelyRender: false,
    extensions: [
      StarterKit,
      // protocols restringido: sin esto, Tiptap acepta cualquier esquema en un enlace
      // (incluido "javascript:"), que terminaría insertado tal cual en el HTML del
      // artículo y ejecutándose en /blog vía dangerouslySetInnerHTML.
      LinkExtension.configure({ openOnClick: false, autolink: true, protocols: ["http", "https", "mailto"] }),
      ImageExtension,
      Placeholder.configure({ placeholder: "Escribe el contenido del artículo..." }),
    ],
    content: value,
    editorProps: {
      attributes: {
        class:
          "prose prose-neutral max-w-none min-h-[320px] px-4 py-4 focus:outline-none [&_h2]:font-display [&_h2]:text-xl [&_h3]:font-display [&_h3]:text-lg [&_a]:text-gold-deep",
      },
    },
    onUpdate: ({ editor }) => onChange(editor.getHTML()),
  });

  if (!editor) {
    return (
      <div className="min-h-[380px] animate-pulse rounded-xl border border-line bg-ink/5" />
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-paper">
      <div className="flex flex-wrap items-center gap-1 border-b border-line bg-surface p-2">
        <ToolbarButton active={editor.isActive("bold")} onClick={() => editor.chain().focus().toggleBold().run()} label="Negrita">
          <TextB weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <ToolbarButton active={editor.isActive("italic")} onClick={() => editor.chain().focus().toggleItalic().run()} label="Cursiva">
          <TextItalic weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <Divisor />
        <ToolbarButton
          active={editor.isActive("heading", { level: 2 })}
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          label="Encabezado 2"
        >
          H2
        </ToolbarButton>
        <ToolbarButton
          active={editor.isActive("heading", { level: 3 })}
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          label="Encabezado 3"
        >
          H3
        </ToolbarButton>
        <Divisor />
        <ToolbarButton active={editor.isActive("bulletList")} onClick={() => editor.chain().focus().toggleBulletList().run()} label="Lista">
          <ListBullets weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <ToolbarButton active={editor.isActive("orderedList")} onClick={() => editor.chain().focus().toggleOrderedList().run()} label="Lista numerada">
          <ListNumbers weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <Divisor />
        <ToolbarButton
          active={editor.isActive("link")}
          label="Enlace"
          onClick={() => {
            const url = window.prompt("URL del enlace");
            if (!url) return;
            if (!esUrlSegura(url)) {
              window.alert("El enlace debe empezar por http:// o https://");
              return;
            }
            editor.chain().focus().setLink({ href: url }).run();
          }}
        >
          <LinkSimple weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <ToolbarButton
          label="Insertar imagen (URL)"
          onClick={() => {
            const url = window.prompt("URL de la imagen");
            if (!url) return;
            if (!esUrlSegura(url)) {
              window.alert("La imagen debe cargarse desde una URL que empiece por http:// o https://");
              return;
            }
            editor.chain().focus().setImage({ src: url }).run();
          }}
        >
          <ImageSquare weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <Divisor />
        <ToolbarButton label="Deshacer" onClick={() => editor.chain().focus().undo().run()}>
          <ArrowUUpLeft weight="bold" className="h-4 w-4" />
        </ToolbarButton>
        <ToolbarButton label="Rehacer" onClick={() => editor.chain().focus().redo().run()}>
          <ArrowUUpRight weight="bold" className="h-4 w-4" />
        </ToolbarButton>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}

function ToolbarButton({
  children,
  onClick,
  active,
  label,
}: {
  children: React.ReactNode;
  onClick: () => void;
  active?: boolean;
  label: string;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className={cn(
        "flex h-8 min-w-8 items-center justify-center rounded-lg px-2 text-xs font-semibold transition-colors",
        active ? "bg-ink text-paper" : "text-ink-soft hover:bg-ink/5",
      )}
    >
      {children}
    </button>
  );
}

function Divisor() {
  return <span className="mx-1 h-5 w-px bg-line" />;
}
