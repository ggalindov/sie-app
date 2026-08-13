import { AdminPageHeader } from "@/components/admin/ui";
import { ArticuloForm } from "@/components/admin/articulo-form";

export default function NuevoArticuloPage() {
  return (
    <div>
      <AdminPageHeader title="Nuevo artículo" />
      <ArticuloForm />
    </div>
  );
}
