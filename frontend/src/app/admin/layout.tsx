import type { Metadata } from "next";
import { AuthProvider } from "@/lib/auth-context";
import { AdminBodyClass } from "@/components/admin/admin-body-class";

export const metadata: Metadata = {
  title: {
    default: "Panel administrativo",
    template: "%s | Panel SIE Jurídicos",
  },
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <AuthProvider>
      <AdminBodyClass />
      <div className="panel-admin min-h-screen">
        {children}
      </div>
    </AuthProvider>
  );
}
