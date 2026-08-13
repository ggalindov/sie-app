import type { Metadata } from "next";
import { AuthProvider } from "@/lib/auth-context";

export const metadata: Metadata = {
  title: {
    default: "Panel administrativo",
    template: "%s | Panel SIE Jurídicos",
  },
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return <AuthProvider>{children}</AuthProvider>;
}
